package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class ZeffyPaymentSyncService {

    private static final ZoneId CHURCH_ZONE = ZoneId.of("America/Chicago");
    private static final String PAYMENTS = "PAYMENTS";

    private final ZeffyApiClient apiClient;
    private final ZeffyPaymentProcessor processor;
    private final ZeffySyncRunService runService;
    private final ZeffySyncPaymentResultService resultService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ZeffySyncRun preview(LocalDate createdFrom, LocalDate createdThrough, String initiatedBy) {
        if (createdFrom == null) throw new IllegalArgumentException("Created-from date is required");
        if (createdThrough != null && createdThrough.isBefore(createdFrom)) {
            throw new IllegalArgumentException("Created-through date cannot be before created-from date");
        }
        OffsetDateTime from = startOfDay(createdFrom);
        OffsetDateTime through = createdThrough == null ? null : endOfDay(createdThrough);
        return execute("PREVIEW", initiatedBy, from, through, null, Map.of());
    }

    public ZeffySyncRun apply(UUID previewRunId, String initiatedBy) {
        if (previewRunId == null) throw new IllegalArgumentException("A preview run is required");
        ZeffySyncRun preview = runService.getDetailed(previewRunId);
        if (!PAYMENTS.equals(preview.getSyncType()) || !"PREVIEW".equals(preview.getExecutionMode())
                || !"COMPLETED".equals(preview.getStatus())) {
            throw new IllegalArgumentException("Apply requires a completed payment preview run");
        }
        List<ZeffySyncPaymentResult> previewResults = resultService.findAll(previewRunId);
        Map<String, ZeffySyncPaymentResult> byPaymentId = new HashMap<>();
        for (ZeffySyncPaymentResult result : previewResults) {
            byPaymentId.put(result.getZeffyPaymentId(), result);
        }
        return execute("APPLY", initiatedBy, preview.getRequestedFrom(), preview.getRequestedTo(),
                previewRunId, byPaymentId);
    }

    private ZeffySyncRun execute(String mode, String initiatedBy, OffsetDateTime from,
                                 OffsetDateTime through, UUID previewRunId,
                                 Map<String, ZeffySyncPaymentResult> previewResults) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("A historical payment synchronization is already running");
        }
        ZeffySyncRun run = null;
        MutableCounts counts = new MutableCounts();
        String cursor = null;
        Set<String> seenCursors = new HashSet<>();
        Set<String> seenPayments = new HashSet<>();
        try {
            run = runService.startPayment(mode, initiatedBy, from, through, previewRunId);
            while (true) {
                ZeffyApiClient.PaymentPageFetch page = apiClient.fetchPaymentPage(
                        from.toEpochSecond(), through == null ? null : through.toEpochSecond(), cursor);
                OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.UTC);
                for (JsonNode payment : page.payments()) {
                    counts.fetched++;
                    processOne(run.getId(), mode, payment, observedAt, previewResults, seenPayments, counts);
                }
                String nextCursor = page.nextCursor();
                runService.checkpointPayment(run.getId(), counts.snapshot(), nextCursor);
                if (!page.hasMore()) break;
                if (nextCursor == null || nextCursor.isBlank() || !seenCursors.add(nextCursor)) {
                    throw new ZeffyApiException(502, "Zeffy returned a repeated payment pagination cursor");
                }
                cursor = nextCursor;
            }

            if ("APPLY".equals(mode)) {
                recordMissingPreviewPayments(run.getId(), previewResults, seenPayments, counts);
            }
            return runService.finishPayment(run.getId(), counts.snapshot(), cursor);
        } catch (RuntimeException ex) {
            if (run != null) {
                try {
                    runService.abortPayment(run.getId(), counts.snapshot(), cursor, safeError(ex));
                } catch (RuntimeException recordingFailure) {
                    ex.addSuppressed(recordingFailure);
                }
            }
            throw ex;
        } finally {
            running.set(false);
        }
    }

    private void processOne(UUID runId, String mode, JsonNode payment, OffsetDateTime observedAt,
                            Map<String, ZeffySyncPaymentResult> previewResults,
                            Set<String> seenPayments, MutableCounts counts) {
        String paymentId = text(payment, "id");
        String fingerprint = processor.fingerprint(payment);
        if (paymentId != null) seenPayments.add(paymentId);
        try {
            if ("APPLY".equals(mode)) {
                ZeffySyncPaymentResult preview = paymentId == null ? null : previewResults.get(paymentId);
                if (preview == null || !fingerprint.equals(preview.getPayloadSha256())) {
                    String id = paymentId != null ? paymentId : "missing-id-" + fingerprint.substring(0, 16);
                    resultService.record(runId, id, null, "CHANGED_AFTER_PREVIEW", fingerprint,
                            preview == null ? "Payment was not present in the approved preview"
                                    : "Payment changed after the approved preview",
                            observedAt);
                    counts.needsReview++;
                    return;
                }
            }
            ZeffyPaymentProcessor.ProcessingResult result = "PREVIEW".equals(mode)
                    ? processor.previewApiPayment(payment, observedAt)
                    : processor.applyApiPayment(payment, observedAt);
            resultService.record(runId, result, observedAt);
            counts.accept(result);
        } catch (RuntimeException ex) {
            String id = paymentId != null ? paymentId : "missing-id-" + fingerprint.substring(0, 16);
            resultService.record(runId, id, null, "ERROR", fingerprint, safeError(ex), observedAt);
            counts.failed++;
        }
    }

    private void recordMissingPreviewPayments(UUID runId,
                                              Map<String, ZeffySyncPaymentResult> previewResults,
                                              Set<String> seenPayments, MutableCounts counts) {
        OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.UTC);
        for (ZeffySyncPaymentResult preview : previewResults.values()) {
            if (seenPayments.contains(preview.getZeffyPaymentId())) continue;
            UUID paymentRecordId = preview.getZeffyPayment() == null ? null : preview.getZeffyPayment().getId();
            resultService.record(runId, preview.getZeffyPaymentId(), paymentRecordId,
                    "CHANGED_AFTER_PREVIEW", preview.getPayloadSha256(),
                    "Payment from the approved preview was not returned during apply", observedAt);
            counts.needsReview++;
        }
    }

    static OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(CHURCH_ZONE).toOffsetDateTime();
    }

    static OffsetDateTime endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(CHURCH_ZONE).minusSeconds(1).toOffsetDateTime();
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) return null;
        return value.textValue().trim();
    }

    private String safeError(RuntimeException ex) {
        if (ex instanceof ZeffyApiException || ex instanceof IllegalArgumentException) return ex.getMessage();
        return "Unexpected historical payment synchronization failure";
    }

    private static final class MutableCounts {
        private int fetched;
        private int inserted;
        private int updated;
        private int ignored;
        private int failed;
        private int alreadyApplied;
        private int eligible;
        private int needsMapping;
        private int needsReview;
        private int processed;

        private void accept(ZeffyPaymentProcessor.ProcessingResult result) {
            if (result.inserted()) inserted++; else updated++;
            switch (result.outcome()) {
                case "IGNORED" -> ignored++;
                case "ALREADY_APPLIED" -> alreadyApplied++;
                case "ELIGIBLE" -> eligible++;
                case "NEEDS_MAPPING" -> needsMapping++;
                case "NEEDS_REVIEW" -> needsReview++;
                case "PROCESSED" -> processed++;
                default -> failed++;
            }
        }

        private ZeffySyncRunService.PaymentCounts snapshot() {
            return new ZeffySyncRunService.PaymentCounts(fetched, inserted, updated, ignored, failed,
                    alreadyApplied, eligible, needsMapping, needsReview, processed);
        }
    }
}
