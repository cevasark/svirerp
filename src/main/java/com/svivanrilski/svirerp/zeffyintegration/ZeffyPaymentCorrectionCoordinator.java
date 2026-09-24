package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies pre-existing Phase 5A correction candidates one payment transaction at a time. */
@Service
@RequiredArgsConstructor
public class ZeffyPaymentCorrectionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ZeffyPaymentCorrectionCoordinator.class);

    private final ZeffyPaymentRepository paymentRepository;
    private final ZeffyPaymentCorrectionService correctionService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public record ApplyPendingResult(
            int paymentsScanned,
            int correctionsApplied,
            int alreadyCorrected,
            int needsReview,
            int failed) {
    }

    public ApplyPendingResult applyPending() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("Zeffy payment corrections are already running");
        }
        try {
            List<UUID> paymentIds = paymentRepository.findPaymentIdsAwaitingCorrection();
            int applied = 0;
            int already = 0;
            int review = 0;
            int failed = 0;
            for (UUID paymentId : paymentIds) {
                try {
                    ZeffyPaymentCorrectionService.CorrectionOutcome outcome =
                            correctionService.correctPayment(paymentId);
                    applied += outcome.corrected();
                    already += outcome.alreadyCorrected();
                    review += outcome.needsReview();
                } catch (RuntimeException ex) {
                    log.error("Could not apply pending Zeffy corrections for payment {}", paymentId, ex);
                    failed++;
                }
            }
            return new ApplyPendingResult(paymentIds.size(), applied, already, review, failed);
        } finally {
            running.set(false);
        }
    }
}
