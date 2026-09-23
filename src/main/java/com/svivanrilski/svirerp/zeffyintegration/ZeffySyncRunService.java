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
    public Page<ZeffySyncRun> find(String syncType, Pageable pageable) {
        if (syncType == null || syncType.isBlank()) return repository.findAll(pageable);
        return repository.findBySyncType(syncType.trim().toUpperCase(), pageable);
    }

    private ZeffySyncRun get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ZeffySyncRun", id));
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
