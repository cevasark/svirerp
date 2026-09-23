package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZeffySyncRunService {

    private final ZeffySyncRunRepository repository;

    public record PaymentCounts(int fetched, int inserted, int updated, int ignored, int failed,
                                int alreadyApplied, int eligible, int needsMapping,
                                int needsReview, int processed) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffySyncRun start(String syncType, String triggerType, String initiatedBy) {
        return repository.saveAndFlush(ZeffySyncRun.builder()
                .syncType(syncType)
                .status("RUNNING")
                .triggerType(triggerType)
                .initiatedBy(trimToNull(initiatedBy))
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffySyncRun finish(UUID runId, ZeffyCampaignSyncWriter.Result result, String endingCursor) {
        ZeffySyncRun run = get(runId);
        run.setStatus(result.failed() > 0 ? "PARTIAL" : "COMPLETED");
        run.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        run.setEndingCursor(trimToNull(endingCursor));
        run.setFetchedCount(result.fetched());
        run.setInsertedCount(result.inserted());
        run.setUpdatedCount(result.updated());
        run.setIgnoredCount(result.ignored());
        run.setFailedCount(result.failed());
        return repository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffySyncRun startPayment(String executionMode, String initiatedBy,
                                     OffsetDateTime requestedFrom, OffsetDateTime requestedTo,
                                     UUID previewRunId) {
        ZeffySyncRun preview = previewRunId == null ? null : getDetailed(previewRunId);
        return repository.saveAndFlush(ZeffySyncRun.builder()
                .syncType("PAYMENTS")
                .executionMode(executionMode)
                .previewRun(preview)
                .status("RUNNING")
                .triggerType("MANUAL")
                .initiatedBy(trimToNull(initiatedBy))
                .requestedFrom(requestedFrom)
                .requestedTo(requestedTo)
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffySyncRun checkpointPayment(UUID runId, PaymentCounts counts, String endingCursor) {
        ZeffySyncRun run = get(runId);
        copyCounts(run, counts);
        run.setEndingCursor(trimToNull(endingCursor));
        return repository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffySyncRun finishPayment(UUID runId, PaymentCounts counts, String endingCursor) {
        ZeffySyncRun run = get(runId);
        copyCounts(run, counts);
        run.setEndingCursor(trimToNull(endingCursor));
        boolean incompleteApply = "APPLY".equals(run.getExecutionMode())
                && (counts.needsMapping() > 0 || counts.needsReview() > 0);
        run.setStatus(counts.failed() > 0 || incompleteApply ? "PARTIAL" : "COMPLETED");
        run.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return repository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abortPayment(UUID runId, PaymentCounts counts, String endingCursor, String errorSummary) {
        ZeffySyncRun run = get(runId);
        copyCounts(run, counts);
        run.setEndingCursor(trimToNull(endingCursor));
        run.setStatus(counts.fetched() > 0 ? "PARTIAL" : "FAILED");
        run.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        run.setErrorSummary(truncate(errorSummary, 1000));
        repository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId, String errorSummary) {
        ZeffySyncRun run = get(runId);
        run.setStatus("FAILED");
        run.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        run.setErrorSummary(truncate(errorSummary, 1000));
        repository.save(run);
    }

    @Transactional(readOnly = true)
    public ZeffySyncRun latest(String syncType) {
        return repository.findFirstBySyncTypeOrderByStartedAtDesc(syncType).orElse(null);
    }

    @Transactional(readOnly = true)
    public ZeffySyncRun latest(String syncType, String executionMode) {
        return repository.findFirstBySyncTypeAndExecutionModeOrderByStartedAtDesc(syncType, executionMode)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ZeffySyncRun getDetailed(UUID id) {
        return repository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ZeffySyncRun", id));
    }

    @Transactional(readOnly = true)
    public Page<ZeffySyncRun> find(String syncType, Pageable pageable) {
        if (syncType == null || syncType.isBlank()) return repository.findAll(pageable);
        return repository.findBySyncType(syncType.trim().toUpperCase(), pageable);
    }

    private ZeffySyncRun get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ZeffySyncRun", id));
    }

    private void copyCounts(ZeffySyncRun run, PaymentCounts counts) {
        run.setFetchedCount(counts.fetched());
        run.setInsertedCount(counts.inserted());
        run.setUpdatedCount(counts.updated());
        run.setIgnoredCount(counts.ignored());
        run.setFailedCount(counts.failed());
        run.setAlreadyAppliedCount(counts.alreadyApplied());
        run.setEligibleCount(counts.eligible());
        run.setNeedsMappingCount(counts.needsMapping());
        run.setNeedsReviewCount(counts.needsReview());
        run.setProcessedCount(counts.processed());
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int max) {
        String safe = trimToNull(value);
        return safe == null || safe.length() <= max ? safe : safe.substring(0, max);
    }
}
