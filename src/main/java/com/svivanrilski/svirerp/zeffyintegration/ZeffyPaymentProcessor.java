package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
    private final ZeffyPaymentPayload payload;

    @Transactional
    public void applyEvent(UUID eventId) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        if ("PROCESSED".equals(event.getStatus()) || "IGNORED".equals(event.getStatus())) return;
        if ("NEEDS_REVIEW".equals(event.getStatus()) && PAYLOAD_MISMATCH.equals(event.getErrorSummary())) return;
        if (event.getSchemaVersion() != 1 || !("payment.completed".equals(event.getEventType())
                || "payment.created".equals(event.getEventType()))) {
            throw new IllegalArgumentException(
                    "Only version 1 payment.completed or succeeded payment.created events can be processed");
        }

        OffsetDateTime attemptedAt = OffsetDateTime.now(ZoneOffset.UTC);
        event.setStatus("PROCESSING");
        event.setProcessingAttemptCount(event.getProcessingAttemptCount() + 1);
        event.setLastAttemptedAt(attemptedAt);
        event.setErrorSummary(null);
        process(payload.parseEvent(event.getRawPayload()), event, "WEBHOOK", true,
                event.getDispatchedAt(), attemptedAt);
    }

    @Transactional
    public ProcessingResult previewApiPayment(JsonNode payment, OffsetDateTime observedAt) {
        return process(payload.parse(payment), null, "API_SYNC", false, observedAt, observedAt);
    }

    @Transactional
    public ProcessingResult applyApiPayment(JsonNode payment, OffsetDateTime observedAt) {
        return process(payload.parse(payment), null, "API_SYNC", true, observedAt, observedAt);
    }

    private ProcessingResult process(ZeffyPaymentPayload.PaymentData parsed, ZeffyWebhookEvent event, String source,
                                     boolean apply, OffsetDateTime observedAt,
                                     OffsetDateTime processingAt) {
        if (parsed.id() == null) throw new IllegalArgumentException("Payment ID is missing");
        String payloadHash = payload.fingerprint(parsed.payloadNode());
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
        payload.applySnapshot(payment, parsed, source, observedAt);

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
        return payload.fingerprint(payment);
    }

    private String validatePayment(ZeffyPaymentPayload.PaymentData payment) {
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

    private PersonAssessment assessPerson(ZeffyPaymentPayload.PaymentData payment) {
        String email = payload.normalizedEmail(payment.email());
        if (email == null || !EMAIL.matcher(email).matches()) {
            return new PersonAssessment(null, null, "Buyer email is missing or invalid");
        }
        List<Person> matches = personService.findByNormalizedEmail(email);
        if (matches.size() > 1) {
            return new PersonAssessment(email, null, "Buyer email matches more than one local person");
        }
        if (matches.isEmpty() && (payload.trim(payment.firstName()) == null
                || payload.trim(payment.lastName()) == null)) {
            return new PersonAssessment(email, null, "Buyer name is required to create a local person");
        }
        return new PersonAssessment(email, matches.isEmpty() ? null : matches.getFirst(), null);
    }

    private Person resolvePerson(ZeffyPaymentPayload.PaymentData payment, PersonAssessment assessment) {
        Person incoming = Person.builder()
                .firstName(payload.trim(payment.firstName())).lastName(payload.trim(payment.lastName()))
                .email(assessment.email()).addressLine1(payload.trim(payment.addressLine1()))
                .city(payload.trim(payment.city())).state(payload.trim(payment.state()))
                .zip(payload.trim(payment.postalCode())).build();
        return assessment.existing() == null
                ? personService.create(incoming)
                : personService.fillBlankFields(assessment.existing().getId(), incoming);
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

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= REASON_MAX_LENGTH ? value : value.substring(0, REASON_MAX_LENGTH);
    }

    public record ProcessingResult(String paymentId, UUID paymentRecordId, String outcome,
                                   String detail, String payloadSha256, boolean inserted) {
    }

    private record PersonAssessment(String email, Person existing, String reason) {
    }

}
