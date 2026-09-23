package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.finance.RecordIncomeRequest;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.membership.MemberPaymentRepository;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Applies the same payment rules to live webhooks and historical API synchronization. */
@Service
@RequiredArgsConstructor
public class ZeffyPaymentProcessor {

    private static final ZoneId CHURCH_ZONE = ZoneId.of("America/Chicago");
    private static final int REASON_MAX_LENGTH = 1000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String PAYLOAD_MISMATCH =
            "A duplicate Zeffy event ID was received with a different signed payload";

    private final ZeffyWebhookEventRepository eventRepository;
    private final ZeffyPaymentRepository paymentRepository;
    private final ZeffyCampaignRepository campaignRepository;
    private final PersonService personService;
    private final MembershipService membershipService;
    private final MemberPaymentRepository memberPaymentRepository;
    private final FinanceService financeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void applyEvent(UUID eventId) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        if ("PROCESSED".equals(event.getStatus()) || "IGNORED".equals(event.getStatus())) return;
        if ("NEEDS_REVIEW".equals(event.getStatus()) && PAYLOAD_MISMATCH.equals(event.getErrorSummary())) return;
        if (event.getSchemaVersion() != 1 || !"payment.completed".equals(event.getEventType())) {
            throw new IllegalArgumentException("Only version 1 payment.completed events can be processed");
        }

        OffsetDateTime attemptedAt = OffsetDateTime.now(ZoneOffset.UTC);
        event.setStatus("PROCESSING");
        event.setProcessingAttemptCount(event.getProcessingAttemptCount() + 1);
        event.setLastAttemptedAt(attemptedAt);
        event.setErrorSummary(null);
        process(parseEvent(event.getRawPayload()), event, "WEBHOOK", true,
                event.getDispatchedAt(), attemptedAt);
    }

    @Transactional
    public ProcessingResult previewApiPayment(JsonNode payment, OffsetDateTime observedAt) {
        return process(parsePayment(payment), null, "API_SYNC", false, observedAt, observedAt);
    }

    @Transactional
    public ProcessingResult applyApiPayment(JsonNode payment, OffsetDateTime observedAt) {
        return process(parsePayment(payment), null, "API_SYNC", true, observedAt, observedAt);
    }

    private ProcessingResult process(ParsedPayment parsed, ZeffyWebhookEvent event, String source,
                                     boolean apply, OffsetDateTime observedAt,
                                     OffsetDateTime processingAt) {
        if (parsed.id() == null) throw new IllegalArgumentException("Payment ID is missing");
        String payloadHash = fingerprint(parsed.payloadNode());
        Optional<ZeffyPayment> existing = paymentRepository.findByZeffyPaymentIdForUpdate(parsed.id());
        boolean inserted = existing.isEmpty();
        ZeffyPayment payment = existing.orElseGet(() -> {
            ZeffyPayment created = ZeffyPayment.builder()
                    .zeffyPaymentId(parsed.id())
                    .latestPayload(parsed.payload())
                    .processingStatus("RECEIVED")
                    .firstSeenSource(source)
                    .firstSeenAt(processingAt)
                    .build();
            if ("WEBHOOK".equals(source)) created.setLastEventAt(observedAt);
            return paymentRepository.saveAndFlush(created);
        });
        if (event != null) event.setZeffyPayment(payment);
        updateSnapshot(payment, parsed, source, observedAt);

        if (payment.getAppliedAt() != null || "PROCESSED".equals(payment.getProcessingStatus())) {
            finish(event, payment, "PROCESSED", null, payment.getAppliedAt());
            return result(payment, "ALREADY_APPLIED", null, payloadHash, inserted);
        }

        String invalid = validatePayment(parsed);
        if (invalid != null) {
            finish(event, payment, "NEEDS_REVIEW", invalid, null);
            return result(payment, "NEEDS_REVIEW", invalid, payloadHash, inserted);
        }

        Optional<ZeffyCampaign> mappingResult =
                campaignRepository.findWithMappingByZeffyCampaignId(parsed.campaignId());
        if (mappingResult.isEmpty() || !Boolean.TRUE.equals(mappingResult.get().getMappingConfirmed())) {
            String reason = "Campaign " + parsed.campaignId() + " does not have a confirmed mapping";
            finish(event, payment, "NEEDS_MAPPING", reason, null);
            return result(payment, "NEEDS_MAPPING", reason, payloadHash, inserted);
        }
        ZeffyCampaign campaign = mappingResult.get();
        if (payment.getCampaignTitle() == null) payment.setCampaignTitle(campaign.getTitle());
        payment.setMappingAction(campaign.getProcessingAction());
        payment.setMappedFund(campaign.getFund());
        payment.setMappedAccount(campaign.getCategoryAccount());
        payment.setMembershipCredit(Boolean.TRUE.equals(campaign.getGrantsMembershipCredit()));
        if ("IGNORE".equals(campaign.getProcessingAction())) {
            String reason = "Campaign mapping is configured to ignore payments";
            finish(event, payment, "IGNORED", reason, apply ? processingAt : null);
            return result(payment, "IGNORED", reason, payloadHash, inserted);
        }
        if (!"APPLY".equals(campaign.getProcessingAction())
                || campaign.getFund() == null || campaign.getCategoryAccount() == null) {
            String reason = "Campaign APPLY mapping is incomplete";
            finish(event, payment, "NEEDS_MAPPING", reason, null);
            return result(payment, "NEEDS_MAPPING", reason, payloadHash, inserted);
        }

        PersonAssessment identity = assessPerson(parsed);
        if (identity.reason() != null) {
            finish(event, payment, "NEEDS_REVIEW", identity.reason(), null);
            return result(payment, "NEEDS_REVIEW", identity.reason(), payloadHash, inserted);
        }
        if (!apply) {
            finish(event, payment, "RECEIVED", null, null);
            return result(payment, "ELIGIBLE", null, payloadHash, inserted);
        }

        Person person = resolvePerson(parsed, identity);
        LocalDate paymentDate = parsed.createdAt().atZoneSameInstant(CHURCH_ZONE).toLocalDate();
        Member member = null;
        MemberPayment memberPayment = null;
        if (Boolean.TRUE.equals(campaign.getGrantsMembershipCredit())) {
            member = membershipService.findOrCreateFollowerMember(person.getId(), paymentDate);
            memberPayment = memberPaymentRepository.save(MemberPayment.builder()
                    .member(member)
                    .amount(parsed.amount())
                    .paymentDate(paymentDate)
                    .paymentMethod("zeffy")
                    .transactionRef(parsed.id())
                    .status("completed")
                    .notes("Paid via Zeffy — " + campaign.getTitle())
                    .build());
            member = membershipService.recomputeTier(member.getId());
        }

        JournalEntry journalEntry = null;
        if (parsed.amount().signum() > 0) {
            Account depositAccount = financeService.findOrCreateAccountByNumber(
                    "1020", "Undeposited Funds – Zeffy", "asset");
            journalEntry = financeService.recordIncome(new RecordIncomeRequest(
                    paymentDate, parsed.amount(),
                    "Zeffy payment " + parsed.id() + " — " + campaign.getTitle(),
                    campaign.getCategoryAccount().getId(), depositAccount.getId(), campaign.getFund().getId(),
                    person.getId(), null, "zeffy", null, null, null));
        }

        payment.setPerson(person);
        payment.setMember(member);
        payment.setMemberPayment(memberPayment);
        payment.setJournalEntry(journalEntry);
        payment.setAppliedAt(processingAt);
        finish(event, payment, "PROCESSED", null, processingAt);
        return result(payment, "PROCESSED", null, payloadHash, inserted);
    }

    /** Records an unexpected webhook failure independently after applyEvent rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(UUID eventId, String reason) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        event.setStatus("ERROR");
        event.setProcessingAttemptCount(event.getProcessingAttemptCount() + 1);
        event.setLastAttemptedAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setErrorSummary(truncate(reason));
        eventRepository.save(event);
    }

    public String fingerprint(JsonNode payment) {
        if (payment == null) throw new IllegalArgumentException("Payment payload is missing");
        try {
            Object value = objectMapper.convertValue(payment, Object.class);
            String canonical = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value);
            return sha256(canonical);
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            throw new IllegalArgumentException("Payment payload could not be fingerprinted", ex);
        }
    }

    private ParsedPayment parseEvent(String rawPayload) {
        final JsonNode data;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            data = root == null ? null : root.get("data");
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored Zeffy event payload is not valid JSON");
        }
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("Stored Zeffy event has no payment data object");
        }
        return parsePayment(data);
    }

    private ParsedPayment parsePayment(JsonNode data) {
        if (data == null || !data.isObject()) throw new IllegalArgumentException("Payment data must be an object");
        JsonNode buyer = data.path("buyer");
        JsonNode address = buyer.path("address");
        JsonNode dispute = data.path("dispute");
        return new ParsedPayment(
                text(data, "id"), text(data, "status"), text(data, "refund_status"),
                dispute.isObject() ? text(dispute, "status") : null,
                cents(data.get("amount")), cents(data.get("eligible_amount")), text(data, "currency"),
                text(data, "type"), unixTime(data.get("created")), text(data, "campaign_id"),
                text(data, "description"), text(data, "contact"), text(buyer, "email"),
                text(buyer, "first_name"), text(buyer, "last_name"), text(address, "line1"),
                text(address, "city"), text(address, "state"), text(address, "postal_code"),
                data.toString(), data);
    }

    private String validatePayment(ParsedPayment payment) {
        if (payment.id() == null) return "Payment ID is missing";
        if (!"succeeded".equalsIgnoreCase(payment.status())) return "Payment status is not succeeded";
        if (payment.amount() == null) return "Payment amount is missing or is not whole cents";
        if (payment.amount().signum() < 0) return "Payment amount cannot be negative";
        if (payment.eligibleAmount() == null || payment.eligibleAmount().signum() < 0) {
            return "Eligible amount is missing or invalid";
        }
        if (!"usd".equalsIgnoreCase(payment.currency())) return "Only USD payments are currently supported";
        if (payment.createdAt() == null) return "Payment creation timestamp is missing or invalid";
        if (payment.campaignId() == null) return "Payment campaign ID is missing";
        if (payment.refundStatus() != null && !"none".equalsIgnoreCase(payment.refundStatus())) {
            return "Payment was already partially or fully refunded";
        }
        if (payment.disputeStatus() != null) return "Payment has an active dispute record";
        return null;
    }

    private PersonAssessment assessPerson(ParsedPayment payment) {
        String email = normalizedEmail(payment.email());
        if (email == null || !EMAIL.matcher(email).matches()) {
            return new PersonAssessment(null, null, "Buyer email is missing or invalid");
        }
        List<Person> matches = personService.findByNormalizedEmail(email);
        if (matches.size() > 1) {
            return new PersonAssessment(email, null, "Buyer email matches more than one local person");
        }
        if (matches.isEmpty() && (trim(payment.firstName()) == null || trim(payment.lastName()) == null)) {
            return new PersonAssessment(email, null, "Buyer name is required to create a local person");
        }
        return new PersonAssessment(email, matches.isEmpty() ? null : matches.getFirst(), null);
    }

    private Person resolvePerson(ParsedPayment payment, PersonAssessment assessment) {
        Person incoming = Person.builder()
                .firstName(trim(payment.firstName())).lastName(trim(payment.lastName())).email(assessment.email())
                .addressLine1(trim(payment.addressLine1())).city(trim(payment.city()))
                .state(trim(payment.state())).zip(trim(payment.postalCode())).build();
        return assessment.existing() == null
                ? personService.create(incoming)
                : personService.fillBlankFields(assessment.existing().getId(), incoming);
    }

    private void updateSnapshot(ZeffyPayment target, ParsedPayment source, String origin, OffsetDateTime observedAt) {
        target.setStatus(source.status());
        target.setRefundStatus(source.refundStatus());
        target.setDisputeStatus(source.disputeStatus());
        target.setAmount(source.amount());
        target.setEligibleAmount(source.eligibleAmount());
        target.setCurrency(source.currency() == null ? null : source.currency().toUpperCase(Locale.ROOT));
        target.setPaymentType(source.paymentType());
        target.setPaymentCreatedAt(source.createdAt());
        target.setCampaignId(source.campaignId());
        target.setCampaignTitle(source.campaignTitle());
        target.setContactId(source.contactId());
        target.setBuyerEmail(normalizedEmail(source.email()));
        target.setBuyerFirstName(trim(source.firstName()));
        target.setBuyerLastName(trim(source.lastName()));
        target.setLatestPayload(source.payload());
        if ("WEBHOOK".equals(origin)) {
            if (target.getLastEventAt() == null || observedAt.isAfter(target.getLastEventAt())) {
                target.setLastEventAt(observedAt);
            }
        } else {
            target.setLastSyncedAt(observedAt);
        }
    }

    private void finish(ZeffyWebhookEvent event, ZeffyPayment payment, String status,
                        String reason, OffsetDateTime processedAt) {
        payment.setProcessingStatus(status);
        payment.setOutcomeReason(truncate(reason));
        paymentRepository.save(payment);
        if (event != null) {
            event.setZeffyPayment(payment);
            event.setStatus(status);
            event.setErrorSummary(truncate(reason));
            event.setProcessedAt(processedAt);
            eventRepository.save(event);
        }
    }

    private ProcessingResult result(ZeffyPayment payment, String outcome, String detail,
                                    String payloadHash, boolean inserted) {
        return new ProcessingResult(payment.getZeffyPaymentId(), payment.getId(), outcome,
                truncate(detail), payloadHash, inserted);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private BigDecimal cents(JsonNode value) {
        if (value == null || !value.isNumber()) return null;
        try {
            BigDecimal cents = value.decimalValue().stripTrailingZeros();
            if (cents.scale() > 0) return null;
            return BigDecimal.valueOf(cents.longValueExact(), 2);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private OffsetDateTime unixTime(JsonNode value) {
        if (value == null || !value.isNumber()) return null;
        try {
            BigDecimal seconds = value.decimalValue().stripTrailingZeros();
            if (seconds.scale() > 0) return null;
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds.longValueExact()), ZoneOffset.UTC);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private String text(JsonNode parent, String field) {
        if (parent == null || !parent.isObject()) return null;
        JsonNode value = parent.get(field);
        return value != null && value.isTextual() ? trim(value.textValue()) : null;
    }

    private String normalizedEmail(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= REASON_MAX_LENGTH ? value : value.substring(0, REASON_MAX_LENGTH);
    }

    public record ProcessingResult(String paymentId, UUID paymentRecordId, String outcome,
                                   String detail, String payloadSha256, boolean inserted) {
    }

    private record PersonAssessment(String email, Person existing, String reason) {
    }

    private record ParsedPayment(
            String id, String status, String refundStatus, String disputeStatus, BigDecimal amount,
            BigDecimal eligibleAmount, String currency, String paymentType, OffsetDateTime createdAt,
            String campaignId, String campaignTitle, String contactId, String email, String firstName,
            String lastName, String addressLine1, String city, String state, String postalCode,
            String payload, JsonNode payloadNode) {
    }
}
