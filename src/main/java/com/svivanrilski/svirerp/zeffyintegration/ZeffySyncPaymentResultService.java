package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZeffySyncPaymentResultService {

    private final ZeffySyncPaymentResultRepository repository;
    private final ZeffySyncRunRepository runRepository;
    private final ZeffyPaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffySyncPaymentResult record(UUID runId, ZeffyPaymentProcessor.ProcessingResult result,
                                         OffsetDateTime observedAt) {
        return record(runId, result.paymentId(), result.paymentRecordId(), result.outcome(),
                result.payloadSha256(), result.detail(), observedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffySyncPaymentResult record(UUID runId, String paymentId, UUID paymentRecordId,
                                         String outcome, String payloadSha256, String detail,
                                         OffsetDateTime observedAt) {
        ZeffySyncPaymentResult target = repository.findBySyncRunIdAndZeffyPaymentId(runId, paymentId)
                .orElseGet(() -> ZeffySyncPaymentResult.builder()
                        .syncRun(runRepository.getReferenceById(runId))
                        .zeffyPaymentId(paymentId)
                        .build());
        target.setZeffyPayment(paymentRecordId == null ? null : paymentRepository.getReferenceById(paymentRecordId));
        target.setOutcome(outcome);
        target.setPayloadSha256(payloadSha256);
        target.setDetail(truncate(detail));
        target.setObservedAt(observedAt);
        return repository.saveAndFlush(target);
    }

    @Transactional(readOnly = true)
    public List<ZeffySyncPaymentResult> findAll(UUID runId) {
        requireRun(runId);
        return repository.findBySyncRunId(runId);
    }

    @Transactional(readOnly = true)
    public Page<ZeffySyncPaymentResult> find(UUID runId, Pageable pageable) {
        requireRun(runId);
        return repository.findBySyncRunId(runId, pageable);
    }

    private void requireRun(UUID runId) {
        if (!runRepository.existsById(runId)) {
            throw new ResourceNotFoundException("ZeffySyncRun", runId);
        }
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
