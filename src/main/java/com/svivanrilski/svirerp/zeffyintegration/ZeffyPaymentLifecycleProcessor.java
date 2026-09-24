package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Records Phase 5A payment changes and correction candidates without posting corrections. */
@Service
@RequiredArgsConstructor
public class ZeffyPaymentLifecycleProcessor {

    private static final Set<String> REFUND_STATUSES = Set.of("pending", "succeeded", "failed");
    private static final Set<String> DISPUTE_STATUSES = Set.of("needs_response", "won", "lost");
    private static final Set<String> MATERIAL_APPLIED_FIELDS = Set.of(
            "status", "amount", "eligible_amount", "currency", "campaign_id");

    private final ZeffyWebhookEventRepository eventRepository;
    private final ZeffyPaymentRepository paymentRepository;
    private final ZeffyPaymentChangeRepository changeRepository;
    private final ZeffyRefundRepository refundRepository;
    private final ZeffyDisputeRepository disputeRepository;
    private final ZeffyPaymentCorrectionService correctionService;
    private final ZeffyPaymentPayload payload;

    @Transactional
    public void recordCreated(UUID eventId) {
        ZeffyWebhookEvent event = lockSupportedEvent(eventId, "payment.created");
        if (terminal(event)) return;
        begin(event);
        ZeffyPaymentPayload.PaymentData data = payload.parseEvent(event.getRawPayload());
        recordSnapshot(event, data, "WEBHOOK", "CREATED");
    }

    @Transactional
    public void recordUpdated(UUID eventId, JsonNode currentPayment) {
        ZeffyWebhookEvent event = lockSupportedEvent(eventId, "payment.updated");
        if (terminal(event)) return;
        begin(event);
        ZeffyPaymentPayload.PaymentData data = payload.parse(currentPayment);
        if (!event.getZeffyResourceId().equals(data.id())) {
            throw new IllegalArgumentException("Fetched Zeffy payment ID does not match the webhook resource ID");
        }
        recordSnapshot(event, data, "API_FETCH", "UPDATED");
    }

    @Transactional
    public void auditCreatedApplication(UUID eventId) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        if (event.getSchemaVersion() != 1 || !"payment.created".equals(event.getEventType())) {
            throw new IllegalArgumentException("Only version 1 payment.created events can be audited here");
        }
        if (event.getZeffyPayment() == null) return;
        if (changeRepository.findByWebhookEvent_Id(eventId).isPresent()) return;
        ZeffyPaymentPayload.PaymentData data = payload.parseEvent(event.getRawPayload());
        String summary = "Succeeded payment.created was processed through the initial payment pipeline";
        upsertRefundsAndDispute(event.getZeffyPayment(), event, data, event.getDispatchedAt());
        upsertChange(event, event.getZeffyPayment(), "CREATED", List.of("initial_snapshot"),
                null, event.getZeffyPayment().getLatestPayloadSha256(), summary,
                event.getZeffyPayment().getAmount(), event.getZeffyPayment().getAmount(), false);
        event.setProcessingSummary(summary);
        eventRepository.save(event);
    }

    @Transactional
    public void recordDeleted(UUID eventId, boolean confirmedByFetch) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        if (terminal(event)) return;
        if (event.getSchemaVersion() != 1
                || !("payment.deleted".equals(event.getEventType())
                || "payment.updated".equals(event.getEventType()))) {
            throw new IllegalArgumentException("Only version 1 payment deletion events can be processed");
        }
        begin(event);
        OffsetDateTime observedAt = event.getDispatchedAt();
        ZeffyPayment payment = paymentRepository.findByZeffyPaymentIdForUpdate(event.getZeffyResourceId())
                .orElseGet(() -> paymentRepository.saveAndFlush(ZeffyPayment.builder()
                        .zeffyPaymentId(event.getZeffyResourceId())
                        .latestPayload("{}")
                        .processingStatus("RECEIVED")
                        .firstSeenSource("WEBHOOK")
                        .firstSeenAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .lastEventAt(observedAt)
                        .build()));
        String previousHash = payment.getLatestPayloadSha256();
        event.setZeffyPayment(payment);
        if (!confirmedByFetch && payment.getLastEventAt() != null
                && payment.getLastEventAt().isAfter(observedAt)) {
            String summary = "Older payment.deleted event was ignored because newer payment data is stored";
            upsertChange(event, payment, "DELETED", List.of(), previousHash, previousHash, summary,
                    payment.getAmount(), payment.getAmount(), false);
            finish(event, "PROCESSED", summary, null);
            return;
        }
        payment.setDeletedAt(observedAt);
        if (payment.getLastEventAt() == null || payment.getLastEventAt().isBefore(observedAt)) {
            payment.setLastEventAt(observedAt);
        }
        boolean applied = payment.getAppliedAt() != null;
        String summary = applied
                ? "Applied payment was deleted in Zeffy; staff review is required"
                : "Unapplied payment was deleted in Zeffy";
        payment.setProcessingStatus(applied ? "NEEDS_REVIEW" : "IGNORED");
        payment.setOutcomeReason(applied ? summary : null);
        paymentRepository.save(payment);
        upsertChange(event, payment, confirmedByFetch ? "FETCH_NOT_FOUND" : "DELETED", List.of("deleted"),
                previousHash, previousHash, summary, payment.getAmount(), payment.getAmount(), false);
        finish(event, applied ? "NEEDS_REVIEW" : "PROCESSED", summary,
                applied ? summary : null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(UUID eventId, String reason) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        event.setStatus("ERROR");
        event.setProcessingAttemptCount(event.getProcessingAttemptCount() + 1);
        event.setLastAttemptedAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setProcessingSummary("Payment lifecycle processing failed");
        event.setErrorSummary(truncate(reason));
        eventRepository.save(event);
    }

    private void recordSnapshot(ZeffyWebhookEvent event, ZeffyPaymentPayload.PaymentData data,
                                String source, String changeKind) {
        if (data.id() == null) throw new IllegalArgumentException("Payment ID is missing");
        if (!event.getZeffyResourceId().equals(data.id())) {
            throw new IllegalArgumentException("Payment ID does not match the webhook resource ID");
        }
        OffsetDateTime observedAt = event.getDispatchedAt();
        Optional<ZeffyPayment> existing = paymentRepository.findByZeffyPaymentIdForUpdate(data.id());
        ZeffyPayment payment = existing.orElseGet(() -> paymentRepository.saveAndFlush(ZeffyPayment.builder()
                .zeffyPaymentId(data.id())
                .latestPayload(data.payload())
                .processingStatus("RECEIVED")
                .firstSeenSource("WEBHOOK".equals(source) ? "WEBHOOK" : "API_SYNC")
                .firstSeenAt(OffsetDateTime.now(ZoneOffset.UTC))
                .lastEventAt(observedAt)
                .build()));
        event.setZeffyPayment(payment);

        if ("CREATED".equals(changeKind) && payment.getLastEventAt() != null
                && payment.getLastEventAt().isAfter(observedAt)) {
            String summary = "Older payment.created snapshot was ignored because newer payment data is stored";
            upsertChange(event, payment, changeKind, List.of(), payment.getLatestPayloadSha256(),
                    payment.getLatestPayloadSha256(), summary,
                    payment.getAmount(), payment.getAmount(), false);
            finish(event, "PROCESSED", summary, null);
            return;
        }

        String previousHash = payment.getLatestPayloadSha256();
        BigDecimal previousAmount = payment.getAmount();
        List<String> changedFields = existing.isEmpty()
                ? List.of("initial_snapshot") : payload.changedFields(payment, data);
        String previousProcessingStatus = payment.getProcessingStatus();
        boolean applied = payment.getAppliedAt() != null;
        payload.applySnapshot(payment, data, source, observedAt);
        RefundDisputeOutcome lifecycle = upsertRefundsAndDispute(payment, event, data, observedAt);

        List<String> material = changedFields.stream().filter(MATERIAL_APPLIED_FIELDS::contains).toList();
        List<String> unsupportedMaterial = material.stream()
                .filter(field -> !"amount".equals(field)).toList();
        String materialReview = applied && !unsupportedMaterial.isEmpty()
                ? "Applied payment changed material fields: " + String.join(", ", unsupportedMaterial)
                : null;
        String reviewReason = lifecycle.reviewReason();
        if (reviewReason == null && materialReview != null) {
            reviewReason = materialReview;
        }
        if (reviewReason == null && !applied && "succeeded".equalsIgnoreCase(data.status())
                && "UPDATED".equals(changeKind)) {
            reviewReason = "Succeeded payment has not been applied; run a historical preview before applying it";
        }

        String summary = lifecycle.summary() != null ? lifecycle.summary()
                : changedFields.isEmpty() ? "Payment update contained no normalized field changes"
                : (existing.isEmpty() ? "Payment snapshot recorded"
                : "Payment snapshot updated: " + String.join(", ", changedFields));
        if (reviewReason != null) {
            payment.setProcessingStatus("NEEDS_REVIEW");
            payment.setOutcomeReason(truncate(reviewReason));
        } else if (applied) {
            payment.setProcessingStatus("PROCESSED");
            payment.setOutcomeReason(null);
        } else if ("PROCESSED".equals(previousProcessingStatus)) {
            payment.setProcessingStatus(previousProcessingStatus);
        } else {
            payment.setProcessingStatus("RECEIVED");
            payment.setOutcomeReason(null);
        }
        paymentRepository.save(payment);
        upsertChange(event, payment, changeKind, changedFields, previousHash,
                payment.getLatestPayloadSha256(), summary, previousAmount, payment.getAmount(),
                applied && changedFields.contains("amount"));

        ZeffyPaymentCorrectionService.CorrectionOutcome correction =
                correctionService.applyCorrections(payment);
        if (correction.changed()) {
            summary = correction.summary();
            if (reviewReason != null && reviewReason.contains("awaits Phase 5B correction")) {
                reviewReason = null;
            }
            if (material.size() == 1 && material.contains("amount")) {
                reviewReason = null;
            }
            if (materialReview != null) {
                reviewReason = materialReview;
            }
        }
        if (correction.reviewReason() != null) {
            reviewReason = correction.reviewReason();
            summary = correction.summary();
        }
        if (reviewReason == null && applied) {
            payment.setProcessingStatus("PROCESSED");
            payment.setOutcomeReason(null);
            paymentRepository.save(payment);
        }
        finish(event, reviewReason == null ? "PROCESSED" : "NEEDS_REVIEW", summary, reviewReason);
    }

    private RefundDisputeOutcome upsertRefundsAndDispute(
            ZeffyPayment payment, ZeffyWebhookEvent event, ZeffyPaymentPayload.PaymentData data,
            OffsetDateTime observedAt) {
        String review = null;
        String summary = null;
        BigDecimal succeededRefundTotal = BigDecimal.ZERO;
        for (ZeffyPaymentPayload.RefundData refund : data.refunds()) {
            validateRefund(refund);
            ZeffyRefund target = refundRepository.findByZeffyRefundId(refund.id())
                    .orElseGet(() -> ZeffyRefund.builder()
                            .zeffyRefundId(refund.id()).zeffyPayment(payment).firstSeenAt(observedAt).build());
            if (target.getZeffyPayment() != null && target.getZeffyPayment().getId() != null
                    && !target.getZeffyPayment().getId().equals(payment.getId())) {
                throw new IllegalArgumentException("Zeffy refund ID is already linked to another payment");
            }
            String previousRefundStatus = target.getStatus();
            BigDecimal previousRefundAmount = target.getAmount();
            boolean wasCorrected = "CORRECTED".equals(target.getCorrectionStatus());
            target.setLatestWebhookEvent(event);
            target.setAmount(refund.amount());
            target.setCurrency(refund.currency().toUpperCase(Locale.ROOT));
            target.setStatus(refund.status().toLowerCase(Locale.ROOT));
            target.setRefundCreatedAt(refund.createdAt());
            target.setLastSeenAt(observedAt);
            boolean correctedRefundChanged = wasCorrected
                    && (previousRefundAmount == null || previousRefundAmount.compareTo(refund.amount()) != 0
                    || previousRefundStatus == null
                    || !previousRefundStatus.equalsIgnoreCase(refund.status()));
            if (correctedRefundChanged) {
                target.setCorrectionStatus("NEEDS_REVIEW");
                target.setCorrectionSummary("Already-corrected refund changed amount or status in Zeffy");
            } else if (!wasCorrected && !"NEEDS_REVIEW".equals(target.getCorrectionStatus())) {
                target.setCorrectionStatus("succeeded".equalsIgnoreCase(refund.status())
                        && payment.getAppliedAt() != null ? "AWAITING_CORRECTION" : "NOT_REQUIRED");
            }
            refundRepository.save(target);
            if ("succeeded".equalsIgnoreCase(refund.status())) {
                succeededRefundTotal = succeededRefundTotal.add(refund.amount());
            }
            if (correctedRefundChanged || "NEEDS_REVIEW".equals(target.getCorrectionStatus())) {
                review = target.getCorrectionSummary();
                summary = review;
            } else if ("succeeded".equalsIgnoreCase(refund.status())) {
                if (payment.getAppliedAt() != null
                        && "AWAITING_CORRECTION".equals(target.getCorrectionStatus())) {
                    review = "Succeeded refund " + refund.id() + " awaits Phase 5B correction";
                    summary = "Succeeded refund recorded; no accounting or membership correction was posted";
                } else if (wasCorrected) {
                    summary = "Succeeded refund was already corrected";
                }
            }
        }
        if (payment.getAmount() != null && succeededRefundTotal.compareTo(payment.getAmount()) > 0) {
            review = "Succeeded refunds exceed the original payment amount";
            summary = review;
            for (ZeffyRefund refund : refundRepository.findByZeffyPayment_IdOrderByRefundCreatedAtAsc(payment.getId())) {
                if ("succeeded".equals(refund.getStatus())) {
                    refund.setCorrectionStatus("NEEDS_REVIEW");
                    refundRepository.save(refund);
                }
            }
        }

        ZeffyPaymentPayload.DisputeData dispute = data.dispute();
        if (dispute != null) {
            validateDispute(dispute);
            ZeffyDispute target = disputeRepository.findByZeffyDisputeId(dispute.id())
                    .orElseGet(() -> ZeffyDispute.builder()
                            .zeffyDisputeId(dispute.id()).zeffyPayment(payment).firstSeenAt(observedAt).build());
            if (target.getZeffyPayment() != null && target.getZeffyPayment().getId() != null
                    && !target.getZeffyPayment().getId().equals(payment.getId())) {
                throw new IllegalArgumentException("Zeffy dispute ID is already linked to another payment");
            }
            String previousDisputeStatus = target.getStatus();
            BigDecimal previousDisputeAmount = target.getAmount();
            boolean wasCorrected = "CORRECTED".equals(target.getCorrectionStatus());
            target.setLatestWebhookEvent(event);
            target.setAmount(dispute.amount());
            target.setCurrency(dispute.currency().toUpperCase(Locale.ROOT));
            target.setStatus(dispute.status().toLowerCase(Locale.ROOT));
            target.setReason(dispute.reason());
            target.setDisputeCreatedAt(dispute.createdAt());
            target.setLastSeenAt(observedAt);
            boolean correctedDisputeChanged = wasCorrected
                    && (previousDisputeAmount == null || previousDisputeAmount.compareTo(dispute.amount()) != 0
                    || previousDisputeStatus == null
                    || !previousDisputeStatus.equalsIgnoreCase(dispute.status()));
            if (correctedDisputeChanged) {
                target.setCorrectionStatus("NEEDS_REVIEW");
                target.setCorrectionSummary("Already-corrected dispute changed amount or status in Zeffy");
            } else if (!wasCorrected && !"NEEDS_REVIEW".equals(target.getCorrectionStatus())) {
                target.setCorrectionStatus("lost".equalsIgnoreCase(dispute.status())
                        && payment.getAppliedAt() != null ? "AWAITING_CORRECTION" : "NOT_REQUIRED");
            }
            disputeRepository.save(target);
            if (correctedDisputeChanged || "NEEDS_REVIEW".equals(target.getCorrectionStatus())) {
                review = target.getCorrectionSummary();
                summary = review;
            } else if ("needs_response".equalsIgnoreCase(dispute.status())) {
                review = "Zeffy dispute requires a response";
                summary = review;
            } else if ("lost".equalsIgnoreCase(dispute.status()) && payment.getAppliedAt() != null
                    && "AWAITING_CORRECTION".equals(target.getCorrectionStatus())) {
                review = "Lost dispute " + dispute.id() + " awaits Phase 5B correction";
                summary = "Lost dispute recorded; no accounting or membership correction was posted";
            } else if ("lost".equalsIgnoreCase(dispute.status()) && wasCorrected) {
                summary = "Lost dispute was already corrected";
            } else if ("won".equalsIgnoreCase(dispute.status())) {
                summary = "Won dispute recorded; no correction is required";
            }
        }
        return new RefundDisputeOutcome(summary, review);
    }

    private ZeffyPaymentChange upsertChange(ZeffyWebhookEvent event, ZeffyPayment payment, String kind,
                                            List<String> changedFields, String previousHash, String currentHash,
                                            String summary, BigDecimal previousAmount,
                                            BigDecimal currentAmount, boolean amountNeedsCorrection) {
        ZeffyPaymentChange change = changeRepository.findByWebhookEvent_Id(event.getId())
                .orElseGet(() -> ZeffyPaymentChange.builder()
                        .webhookEvent(event).zeffyPayment(payment).build());
        change.setChangeKind(kind);
        change.setChangedFields(changedFields.isEmpty() ? null : String.join(",", changedFields));
        change.setPreviousPayloadSha256(previousHash);
        change.setCurrentPayloadSha256(currentHash);
        change.setPaymentSnapshot(payment.getLatestPayload());
        change.setSummary(truncate(summary));
        change.setObservedAt(event.getDispatchedAt());
        change.setPreviousAmount(previousAmount);
        change.setCurrentAmount(currentAmount);
        if (amountNeedsCorrection && change.getCorrectionStatus() == null) {
            change.setCorrectionStatus("AWAITING_CORRECTION");
        }
        return changeRepository.save(change);
    }

    private ZeffyWebhookEvent lockSupportedEvent(UUID eventId, String expectedType) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        if (event.getSchemaVersion() != 1 || !expectedType.equals(event.getEventType())) {
            throw new IllegalArgumentException("Only version 1 " + expectedType + " events can be processed");
        }
        return event;
    }

    private void begin(ZeffyWebhookEvent event) {
        event.setStatus("PROCESSING");
        event.setProcessingAttemptCount(event.getProcessingAttemptCount() + 1);
        event.setLastAttemptedAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setProcessingSummary(null);
        event.setErrorSummary(null);
    }

    private void finish(ZeffyWebhookEvent event, String status, String summary, String error) {
        event.setStatus(status);
        event.setProcessingSummary(truncate(summary));
        event.setErrorSummary(truncate(error));
        event.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        eventRepository.save(event);
    }

    private boolean terminal(ZeffyWebhookEvent event) {
        return "PROCESSED".equals(event.getStatus()) || "IGNORED".equals(event.getStatus());
    }

    private void validateRefund(ZeffyPaymentPayload.RefundData refund) {
        if (refund.id() == null || refund.amount() == null || refund.amount().signum() < 0
                || refund.currency() == null || refund.createdAt() == null
                || refund.status() == null || !REFUND_STATUSES.contains(refund.status().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Zeffy returned an invalid refund record");
        }
    }

    private void validateDispute(ZeffyPaymentPayload.DisputeData dispute) {
        if (dispute.id() == null || dispute.amount() == null || dispute.amount().signum() < 0
                || dispute.currency() == null || dispute.createdAt() == null
                || dispute.status() == null
                || !DISPUTE_STATUSES.contains(dispute.status().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Zeffy returned an invalid dispute record");
        }
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record RefundDisputeOutcome(String summary, String reviewReason) {
    }
}
