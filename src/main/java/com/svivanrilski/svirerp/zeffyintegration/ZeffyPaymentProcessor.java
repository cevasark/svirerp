package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Applies one durably recorded payment.completed event. Receipt is committed before this service
 * is called, while all Person, membership, accounting, payment-audit, and event-link changes made
 * here share one transaction.
 */
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

        ParsedPayment parsed = parse(event.getRawPayload());
        ZeffyPayment payment = paymentRepository.findByZeffyPaymentIdForUpdate(parsed.id())
                .orElseGet(() -> paymentRepository.saveAndFlush(ZeffyPayment.builder()
                        .zeffyPaymentId(parsed.id())
                        .latestPayload(parsed.payload())
                        .processingStatus("RECEIVED")
                        .firstSeenSource("WEBHOOK")
                        .firstSeenAt(attemptedAt)
                        .lastEventAt(event.getDispatchedAt())
                        .build()));
        event.setZeffyPayment(payment);
        updateSnapshot(payment, parsed, event.getDispatchedAt());

        if (payment.getAppliedAt() != null || "PROCESSED".equals(payment.getProcessingStatus())) {
            finish(event, payment, "PROCESSED", null, payment.getAppliedAt());
            return;
        }

        String invalid = validatePayment(parsed);
        if (invalid != null) {
            block(event, payment, "NEEDS_REVIEW", invalid);
            return;
        }

        Optional<ZeffyCampaign> mappingResult =
                campaignRepository.findWithMappingByZeffyCampaignId(parsed.campaignId());
        if (mappingResult.isEmpty() || !Boolean.TRUE.equals(mappingResult.get().getMappingConfirmed())) {
            block(event, payment, "NEEDS_MAPPING",
                    "Campaign " + parsed.campaignId() + " does not have a confirmed mapping");
            return;
        }
        ZeffyCampaign campaign = mappingResult.get();
        if (payment.getCampaignTitle() == null) payment.setCampaignTitle(campaign.getTitle());
        payment.setMappingAction(campaign.getProcessingAction());
        payment.setMappedFund(campaign.getFund());
        payment.setMappedAccount(campaign.getCategoryAccount());
        payment.setMembershipCredit(Boolean.TRUE.equals(campaign.getGrantsMembershipCredit()));
        if ("IGNORE".equals(campaign.getProcessingAction())) {
            finish(event, payment, "IGNORED", "Campaign mapping is configured to ignore payments", attemptedAt);
            return;
        }
        if (!"APPLY".equals(campaign.getProcessingAction())
                || campaign.getFund() == null || campaign.getCategoryAccount() == null) {
            block(event, payment, "NEEDS_MAPPING", "Campaign APPLY mapping is incomplete");
            return;
        }

        PersonResolution identity = resolvePerson(parsed);
        if (identity.reason() != null) {
            block(event, payment, "NEEDS_REVIEW", identity.reason());
            return;
        }
        Person person = identity.person();
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
                    paymentDate,
                    parsed.amount(),
                    "Zeffy payment " + parsed.id() + " — " + campaign.getTitle(),
                    campaign.getCategoryAccount().getId(),
                    depositAccount.getId(),
                    campaign.getFund().getId(),
                    person.getId(),
                    null,
                    "zeffy",
                    null,
                    null,
                    null));
        }

        payment.setPerson(person);
        payment.setMember(member);
        payment.setMemberPayment(memberPayment);
        payment.setJournalEntry(journalEntry);
        payment.setAppliedAt(attemptedAt);
        finish(event, payment, "PROCESSED", null, attemptedAt);
    }

    /** Records an unexpected failure independently after applyEvent has rolled its transaction back. */
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

    private ParsedPayment parse(String rawPayload) {
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

        String id = text(data, "id");
        BigDecimal amount = cents(data.get("amount"));
        BigDecimal eligibleAmount = cents(data.get("eligible_amount"));
        OffsetDateTime createdAt = unixTime(data.get("created"));
        JsonNode buyer = data.path("buyer");
        JsonNode address = buyer.path("address");
        JsonNode dispute = data.path("dispute");

        return new ParsedPayment(
                id,
                text(data, "status"),
                text(data, "refund_status"),
                dispute.isObject() ? text(dispute, "status") : null,
                amount,
                eligibleAmount,
                text(data, "currency"),
                text(data, "type"),
                createdAt,
                text(data, "campaign_id"),
                text(data, "description"),
                text(data, "contact"),
                text(buyer, "email"),
                text(buyer, "first_name"),
                text(buyer, "last_name"),
                text(address, "line1"),
                text(address, "city"),
                text(address, "state"),
                text(address, "postal_code"),
                data.toString());
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

    private PersonResolution resolvePerson(ParsedPayment payment) {
        String email = normalizedEmail(payment.email());
        if (email == null || !EMAIL.matcher(email).matches()) {
            return new PersonResolution(null, "Buyer email is missing or invalid");
        }
        List<Person> matches = personService.findByNormalizedEmail(email);
        if (matches.size() > 1) {
            return new PersonResolution(null, "Buyer email matches more than one local person");
        }

        Person incoming = Person.builder()
                .firstName(trim(payment.firstName()))
                .lastName(trim(payment.lastName()))
                .email(email)
                .addressLine1(trim(payment.addressLine1()))
                .city(trim(payment.city()))
                .state(trim(payment.state()))
                .zip(trim(payment.postalCode()))
                .build();
        if (matches.size() == 1) {
            return new PersonResolution(personService.fillBlankFields(matches.get(0).getId(), incoming), null);
        }
        if (incoming.getFirstName() == null || incoming.getLastName() == null) {
            return new PersonResolution(null, "Buyer name is required to create a local person");
        }
        return new PersonResolution(personService.create(incoming), null);
    }

    private void updateSnapshot(ZeffyPayment target, ParsedPayment source, OffsetDateTime eventAt) {
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
        if (target.getLastEventAt() == null || eventAt.isAfter(target.getLastEventAt())) {
            target.setLastEventAt(eventAt);
        }
    }

    private void block(ZeffyWebhookEvent event, ZeffyPayment payment, String status, String reason) {
        finish(event, payment, status, reason, null);
    }

    private void finish(ZeffyWebhookEvent event, ZeffyPayment payment, String status,
                        String reason, OffsetDateTime processedAt) {
        payment.setProcessingStatus(status);
        payment.setOutcomeReason(truncate(reason));
        paymentRepository.save(payment);
        event.setZeffyPayment(payment);
        event.setStatus(status);
        event.setErrorSummary(truncate(reason));
        event.setProcessedAt(processedAt);
        eventRepository.save(event);
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

    private record PersonResolution(Person person, String reason) {
    }

    private record ParsedPayment(
            String id,
            String status,
            String refundStatus,
            String disputeStatus,
            BigDecimal amount,
            BigDecimal eligibleAmount,
            String currency,
            String paymentType,
            OffsetDateTime createdAt,
            String campaignId,
            String campaignTitle,
            String contactId,
            String email,
            String firstName,
            String lastName,
            String addressLine1,
            String city,
            String state,
            String postalCode,
            String payload) {
    }
}
