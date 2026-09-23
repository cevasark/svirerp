package com.svivanrilski.svirerp.stripeintegration;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.InvoicePayment;
import com.stripe.model.LineItem;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.InvoiceRetrieveParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.settings.AppSettingService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the Stripe webhook receiver: verifies the signature, parses the event types this
 * endpoint acts on, and delegates the actual DB writes to {@link StripeWebhookEventApplier} — a
 * different bean, so each event gets its own transaction regardless of outcome (see that class's
 * doc comment). Also owns the admin-facing product-mapping CRUD and event listing/reprocess, same
 * "one shared service per domain area" convention as ZeffyImportService.
 *
 * <p>Checkout happens entirely outside svirerp (a WordPress page, a Stripe Invoice sent directly to
 * a payer, or a mobile card-reader/Tap-to-Pay app for in-person food/drink sales) — this endpoint
 * only reacts to completed payments. WordPress Checkout Sessions raise
 * {@code checkout.session.completed}; a paid Stripe Invoice raises {@code invoice.payment_succeeded};
 * a mobile app driving PaymentIntents directly (no Checkout Session or Invoice involved) raises
 * {@code payment_intent.succeeded}.
 *
 * <p><b>Important:</b> both Stripe Checkout and Stripe Invoicing create a real PaymentIntent under
 * the hood, so a single payment through either one fires <i>both</i> its own event ({@code
 * checkout.session.completed} or {@code invoice.payment_succeeded}) <i>and</i>
 * {@code payment_intent.succeeded} — two distinct event ids for the same payment, so the
 * event-id-based idempotency guard in {@link StripeWebhookEventApplier} does not by itself prevent
 * double-posting it. To avoid that, {@link #parsePaymentIntent} only treats a PaymentIntent event
 * as actionable when it carries an explicit {@code price_id} metadata tag — which only a
 * standalone PaymentIntent (the mobile Tap-to-Pay path) is expected to have; a Checkout- or
 * Invoice-created PaymentIntent never does, so its companion event is deliberately ignored here
 * (already handled via the higher-level event).
 */
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final Set<String> PURPOSES =
            Set.of("membership_dues", "service_request", "event_ticket", "general_income");
    private static final Set<String> SERVICE_TYPES =
            Set.of("wedding", "baptism", "funeral", "memorial", "blessing", "other");

    private final AppSettingService settingService;
    private final FinanceService financeService;
    private final StripeWebhookEventApplier applier;
    private final StripeWebhookEventRepository eventRepo;
    private final StripeProductMappingRepository mappingRepo;

    public record StripeProductMappingRequest(
            String stripePriceId, String displayName, String purpose,
            UUID fundId, UUID categoryAccountId, String serviceType) {
    }

    public record StripePriceInfo(
            String priceId, String displayName, Long unitAmount, String currency, boolean alreadyMapped) {
    }

    /** {@code ignoreReason} non-null means "recognized, but deliberately not actionable" — still
     *  recorded (status='ignored') so an admin can see why, rather than the event leaving no trace
     *  at all. Only an event type this endpoint isn't subscribed to skips recording entirely
     *  (see {@link #parse}). */
    private record ParsedEvent(
            String priceId, BigDecimal amount, BigDecimal feeAmount, String email, String firstName,
            String lastName, String ignoreReason) {
        static ParsedEvent ignored(String reason) {
            return new ParsedEvent(null, null, null, null, null, null, reason);
        }
    }

    // ── Webhook receipt ──────────────────────────────────────────────────────

    /**
     * Verifies the signature, then — for a recognized event type — records and applies it. Never
     * throws for a business-level failure (bad/missing data, unmapped price): those are caught and
     * recorded on the event row so the controller can still return 200 (a webhook retry wouldn't
     * fix a business problem, and Stripe would otherwise keep redelivering for up to ~3 days). Only
     * signature verification failures and genuine infrastructure errors propagate to the caller.
     */
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        String webhookSecret = settingService.getDecryptedValue("stripe.webhook-signing-secret")
                .orElseThrow(() -> new IllegalStateException("Stripe webhook signing secret is not configured"));
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        if (eventRepo.existsByStripeEventId(event.getId())) {
            return; // already seen
        }

        ParsedEvent parsed = parse(event);
        if (parsed == null) {
            return; // an event type this endpoint doesn't act on
        }

        StripeWebhookEvent row = applier.recordReceived(event.getId(), event.getType(),
                parsed.priceId(), parsed.amount(), parsed.feeAmount(), parsed.email(), parsed.firstName(),
                parsed.lastName(), payload);
        if (row == null) {
            return; // a concurrent delivery of the same event won the race
        }

        if (parsed.ignoreReason() != null) {
            applier.markIgnored(row.getId(), parsed.ignoreReason());
            return;
        }

        try {
            applier.applyEvent(row.getId());
        } catch (Exception ex) {
            log.warn("Stripe event {} ({}) failed to apply: {}", event.getId(), event.getType(), ex.getMessage());
            applier.markEventError(row.getId(), ex.getMessage());
        }
    }

    private ParsedEvent parse(Event event) {
        return switch (event.getType()) {
            case "checkout.session.completed" -> parseCheckoutSession(event);
            case "invoice.payment_succeeded" -> parseInvoicePaymentSucceeded(event);
            case "payment_intent.succeeded" -> parsePaymentIntent(event);
            default -> null;
        };
    }

    private ParsedEvent parseCheckoutSession(Event event) {
        Session session = (Session) deserialize(event);
        if (!"paid".equals(session.getPaymentStatus())) {
            // e.g. a session completed with a still-pending/failed payment
            return ParsedEvent.ignored("Checkout session payment_status is '" + session.getPaymentStatus() + "', not 'paid'");
        }
        String email = session.getCustomerDetails() != null
                ? session.getCustomerDetails().getEmail() : session.getCustomerEmail();
        String name = session.getCustomerDetails() != null ? session.getCustomerDetails().getName() : null;
        BigDecimal amount = session.getAmountTotal() != null
                ? BigDecimal.valueOf(session.getAmountTotal()).movePointLeft(2) : null;
        String priceId = resolveLineItemPriceId(session.getId());
        BigDecimal fee = feeAmountFor(session.getPaymentIntent());
        String[] nameParts = splitName(name);
        return new ParsedEvent(priceId, amount, fee, email, nameParts[0], nameParts[1], null);
    }

    /** A Stripe Invoice sent directly to a payer (e.g. dues billed outside a WordPress checkout) —
     *  its own event carries the line-item Price info the companion payment_intent.succeeded lacks. */
    private ParsedEvent parseInvoicePaymentSucceeded(Event event) {
        Invoice invoice = (Invoice) deserialize(event);
        if (invoice.getAmountPaid() == null || invoice.getAmountPaid() <= 0) {
            return ParsedEvent.ignored("Invoice amount_paid is 0 — nothing to record"); // e.g. a $0 invoice
        }
        BigDecimal amount = BigDecimal.valueOf(invoice.getAmountPaid()).movePointLeft(2);
        String priceId = resolveInvoiceLineItemPriceId(invoice);
        BigDecimal fee = feeAmountFor(resolveInvoicePaymentIntentId(invoice));
        String[] nameParts = splitName(invoice.getCustomerName());
        return new ParsedEvent(priceId, amount, fee, invoice.getCustomerEmail(), nameParts[0], nameParts[1], null);
    }

    private String resolveInvoiceLineItemPriceId(Invoice invoice) {
        String priceId = firstLinePriceId(invoice);
        if (priceId != null) {
            return priceId;
        }
        // The webhook payload's embedded `lines` can be truncated/absent — re-fetch with an
        // explicit expand, same fallback as Checkout Session's line items.
        try {
            InvoiceRetrieveParams params = InvoiceRetrieveParams.builder()
                    .addExpand("lines.data.pricing.price_details.price")
                    .build();
            Invoice full = Invoice.retrieve(invoice.getId(), params, stripeRequestOptions());
            return firstLinePriceId(full);
        } catch (StripeException e) {
            log.warn("Failed to retrieve line items for Stripe Invoice {}: {}", invoice.getId(), e.getMessage());
            return null;
        }
    }

    private String firstLinePriceId(Invoice invoice) {
        if (invoice.getLines() == null || invoice.getLines().getData() == null
                || invoice.getLines().getData().isEmpty()) {
            return null;
        }
        InvoiceLineItem.Pricing pricing = invoice.getLines().getData().get(0).getPricing();
        return pricing != null && pricing.getPriceDetails() != null
                ? pricing.getPriceDetails().getPrice() : null;
    }

    /** The PaymentIntent ID behind this invoice's payment — needed to look up the BalanceTransaction
     *  for the processing fee (see {@link #feeAmountFor}). Same embedded-then-re-fetch fallback as
     *  {@link #resolveInvoiceLineItemPriceId}. */
    private String resolveInvoicePaymentIntentId(Invoice invoice) {
        String id = firstInvoicePaymentIntentId(invoice);
        if (id != null) {
            return id;
        }
        try {
            InvoiceRetrieveParams params = InvoiceRetrieveParams.builder()
                    .addExpand("payments.data.payment.payment_intent")
                    .build();
            Invoice full = Invoice.retrieve(invoice.getId(), params, stripeRequestOptions());
            return firstInvoicePaymentIntentId(full);
        } catch (StripeException e) {
            log.warn("Failed to retrieve payment info for Stripe Invoice {}: {}", invoice.getId(), e.getMessage());
            return null;
        }
    }

    private String firstInvoicePaymentIntentId(Invoice invoice) {
        if (invoice.getPayments() == null || invoice.getPayments().getData() == null
                || invoice.getPayments().getData().isEmpty()) {
            return null;
        }
        InvoicePayment.Payment payment = invoice.getPayments().getData().get(0).getPayment();
        return payment != null ? payment.getPaymentIntent() : null;
    }

    private ParsedEvent parsePaymentIntent(Event event) {
        PaymentIntent intent = (PaymentIntent) deserialize(event);
        // A standalone PaymentIntent (the mobile Tap-to-Pay path, with no Checkout Session
        // involved) has no line items to resolve a Price from — the mobile app tags which product
        // it's charging for via this metadata key instead. Its absence means this event is a
        // Checkout-created PaymentIntent's companion event (see class doc) — deliberately ignored,
        // not just "unmapped", since it was already handled via checkout.session.completed and
        // processing it too would double-post the same payment.
        String priceId = intent.getMetadata() != null ? intent.getMetadata().get("price_id") : null;
        if (priceId == null || priceId.isBlank()) {
            return ParsedEvent.ignored("No price_id metadata tag — likely a Checkout/Invoice-created "
                    + "PaymentIntent's companion event, already handled via its own event");
        }
        String email = intent.getReceiptEmail();
        BigDecimal amount = intent.getAmount() != null
                ? BigDecimal.valueOf(intent.getAmount()).movePointLeft(2) : null;
        BigDecimal fee = feeAmountFor(intent.getId());
        return new ParsedEvent(priceId, amount, fee, email, null, null, null);
    }

    /**
     * Stripe deducts its processing fee before ever depositing to the bank — the BalanceTransaction
     * behind a PaymentIntent's latest Charge has already computed it (`fee`) by the time the payment
     * succeeds, so this is available synchronously, no need to wait for the eventual payout. Returns
     * null (not zero) on any failure to resolve it — FinanceService#recordIncome falls back to a
     * plain 2-line entry (no fee split) rather than fail the whole payment over fee-lookup trouble.
     */
    private BigDecimal feeAmountFor(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return null;
        }
        try {
            PaymentIntentRetrieveParams params = PaymentIntentRetrieveParams.builder()
                    .addExpand("latest_charge.balance_transaction")
                    .build();
            PaymentIntent full = PaymentIntent.retrieve(paymentIntentId, params, stripeRequestOptions());
            Charge charge = full.getLatestChargeObject();
            BalanceTransaction balanceTransaction = charge != null ? charge.getBalanceTransactionObject() : null;
            return balanceTransaction != null && balanceTransaction.getFee() != null
                    ? BigDecimal.valueOf(balanceTransaction.getFee()).movePointLeft(2) : null;
        } catch (StripeException e) {
            log.warn("Failed to retrieve balance transaction for PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            return null;
        }
    }

    /**
     * An event's data object is only "safely" deserializable when its Stripe API version's release
     * train (e.g. "dahlia") matches this stripe-java SDK's pinned version — otherwise
     * {@code getObject()} deliberately returns empty rather than risk silently-wrong field mapping
     * (see {@link EventDataObjectDeserializer}'s own class doc). The `pom.xml` dependency comment
     * explains keeping the SDK version's train aligned with the church's Stripe account (currently
     * "dahlia") as the durable fix — bump it there when Stripe ships a new train the account moves
     * to. This `deserializeUnsafe()` fallback exists for the gap in between (an account can move to
     * a new train before this app's dependency catches up): most API version bumps only touch a
     * handful of classes, so a mismatched *date* within the same train still deserializes correctly
     * far more often than not.
     */
    private StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject().get();
        }
        try {
            log.warn("Stripe event {} ({}) has API version {} — falling back to unsafe deserialization",
                    event.getId(), event.getType(), event.getApiVersion());
            return deserializer.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new IllegalStateException("Could not deserialize " + event.getType()
                    + " payload even with the unsafe fallback — API version mismatch too severe: "
                    + e.getMessage(), e);
        }
    }

    /** A webhook event payload never includes Checkout Session line items — they have to be
     *  fetched back from the Stripe API with an explicit expand. */
    private String resolveLineItemPriceId(String sessionId) {
        try {
            SessionRetrieveParams params = SessionRetrieveParams.builder().addExpand("line_items").build();
            Session full = Session.retrieve(sessionId, params, stripeRequestOptions());
            if (full.getLineItems() != null && !full.getLineItems().getData().isEmpty()) {
                LineItem first = full.getLineItems().getData().get(0);
                return first.getPrice() != null ? first.getPrice().getId() : null;
            }
        } catch (StripeException e) {
            log.warn("Failed to retrieve line items for Stripe Checkout Session {}: {}", sessionId, e.getMessage());
        }
        return null;
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[] {null, null};
        }
        String trimmed = fullName.trim();
        int idx = trimmed.indexOf(' ');
        return idx < 0 ? new String[] {trimmed, null} : new String[] {trimmed.substring(0, idx), trimmed.substring(idx + 1)};
    }

    private RequestOptions stripeRequestOptions() {
        String secretKey = settingService.getDecryptedValue("stripe.secret-key")
                .orElseThrow(() -> new IllegalStateException("Stripe secret key is not configured"));
        return RequestOptions.builder().setApiKey(secretKey).build();
    }

    // ── Admin: events ────────────────────────────────────────────────────────

    public Page<StripeWebhookEvent> findEvents(Pageable pageable) {
        return eventRepo.findAllByOrderByReceivedAtDesc(pageable);
    }

    /**
     * Re-runs the same idempotent apply path a fresh webhook delivery would — used once an admin
     * has fixed whatever made the event 'needs_mapping' or 'error'. Deliberately NOT
     * {@code @Transactional} — applier.applyEvent/markEventError are on a different bean, each
     * with their own {@code @Transactional} (default REQUIRED propagation); an ambient transaction
     * on this method would make applyEvent's failure join and poison it, so the catch block here
     * would appear to handle it but this method's own commit would then throw
     * UnexpectedRollbackException anyway. Same idiom as ZeffyImportService#commitImport and
     * StripeWebhookService#handleWebhook.
     */
    public StripeWebhookEvent reprocessEvent(UUID eventId) {
        try {
            applier.applyEvent(eventId);
        } catch (Exception ex) {
            applier.markEventError(eventId, ex.getMessage());
        }
        return eventRepo.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("StripeWebhookEvent", eventId));
    }

    // ── Admin: product mappings ──────────────────────────────────────────────

    public List<StripeProductMapping> findMappings() {
        return mappingRepo.findAll();
    }

    @Transactional
    public StripeProductMapping createMapping(StripeProductMappingRequest req) {
        validateMappingRequest(req);
        StripeProductMapping mapping = new StripeProductMapping();
        applyMappingFields(mapping, req);
        return mappingRepo.save(mapping);
    }

    @Transactional
    public StripeProductMapping updateMapping(UUID id, StripeProductMappingRequest req) {
        validateMappingRequest(req);
        StripeProductMapping existing = mappingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StripeProductMapping", id));
        applyMappingFields(existing, req);
        return mappingRepo.save(existing);
    }

    @Transactional
    public void deleteMapping(UUID id) {
        if (!mappingRepo.existsById(id)) throw new ResourceNotFoundException("StripeProductMapping", id);
        mappingRepo.deleteById(id);
    }

    private void applyMappingFields(StripeProductMapping mapping, StripeProductMappingRequest req) {
        mapping.setStripePriceId(req.stripePriceId());
        mapping.setDisplayName(req.displayName());
        mapping.setPurpose(req.purpose());
        mapping.setFund(req.fundId() != null ? financeService.findFundById(req.fundId()) : null);
        mapping.setCategoryAccount(req.categoryAccountId() != null
                ? financeService.findAccountById(req.categoryAccountId()) : null);
        mapping.setServiceType("service_request".equals(req.purpose()) ? req.serviceType() : null);
    }

    private void validateMappingRequest(StripeProductMappingRequest req) {
        if (req.stripePriceId() == null || req.stripePriceId().isBlank()) {
            throw new IllegalArgumentException("Stripe Price ID is required");
        }
        if (!PURPOSES.contains(req.purpose())) {
            throw new IllegalArgumentException("Invalid purpose: '" + req.purpose() + "'. Allowed: " + PURPOSES);
        }
        if ("service_request".equals(req.purpose())
                && (req.serviceType() == null || !SERVICE_TYPES.contains(req.serviceType()))) {
            throw new IllegalArgumentException(
                    "A service_request mapping needs a valid serviceType. Allowed: " + SERVICE_TYPES);
        }
    }

    /** Lists active Prices from the connected Stripe account so an admin can pick one to map
     *  without needing to wait for a live payment to first surface it as a 'needs_mapping' event. */
    public List<StripePriceInfo> listStripePrices() {
        try {
            PriceListParams params = PriceListParams.builder()
                    .setActive(true)
                    .setLimit(100L)
                    .addExpand("data.product")
                    .build();
            List<Price> prices = Price.list(params, stripeRequestOptions()).getData();
            Set<String> mappedPriceIds = mappingRepo.findAll().stream()
                    .map(StripeProductMapping::getStripePriceId)
                    .collect(Collectors.toSet());
            return prices.stream()
                    .map(price -> new StripePriceInfo(
                            price.getId(),
                            resolvePriceDisplayName(price),
                            price.getUnitAmount(),
                            price.getCurrency(),
                            mappedPriceIds.contains(price.getId())))
                    .toList();
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to list Stripe prices: " + e.getMessage(), e);
        }
    }

    private String resolvePriceDisplayName(Price price) {
        if (price.getNickname() != null && !price.getNickname().isBlank()) {
            return price.getNickname();
        }
        return price.getProductObject() != null ? price.getProductObject().getName() : price.getId();
    }
}
