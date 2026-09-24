package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.membership.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Posts idempotent Phase 5B corrections and then rebuilds the affected membership. */
@Service
@RequiredArgsConstructor
public class ZeffyPaymentCorrectionService {

    private static final ZoneId CHURCH_ZONE = ZoneId.of("America/Chicago");

    private final ZeffyPaymentRepository paymentRepository;
    private final ZeffyRefundRepository refundRepository;
    private final ZeffyDisputeRepository disputeRepository;
    private final ZeffyPaymentChangeRepository changeRepository;
    private final FinanceService financeService;
    private final MembershipService membershipService;

    public record CorrectionOutcome(
            int corrected,
            int alreadyCorrected,
            int needsReview,
            String summary,
            String reviewReason) {
        public boolean changed() {
            return corrected > 0;
        }
    }

    @Transactional
    public CorrectionOutcome correctPayment(UUID paymentId) {
        ZeffyPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy payment not found: " + paymentId));
        return applyCorrections(payment);
    }

    /** Called by the lifecycle processor while it already holds the payment row lock. */
    @Transactional
    public CorrectionOutcome applyCorrections(ZeffyPayment payment) {
        if (payment.getAppliedAt() == null) {
            return new CorrectionOutcome(0, 0, 0, "Unapplied payment requires no correction", null);
        }

        List<ZeffyPaymentChange> changes = changeRepository
                .findByZeffyPayment_IdOrderByObservedAtAsc(payment.getId());
        List<ZeffyRefund> refunds = refundRepository
                .findByZeffyPayment_IdOrderByRefundCreatedAtAsc(payment.getId());
        List<ZeffyDispute> disputes = disputeRepository
                .findByZeffyPayment_IdOrderByDisputeCreatedAtAsc(payment.getId());

        int already = countCorrected(changes, refunds, disputes);
        List<CorrectedItem> correctedItems = new ArrayList<>();
        List<String> reviewReasons = new ArrayList<>();

        if (payment.getAmount() == null || payment.getAmount().signum() < 0) {
            markAwaitingForReview(changes, refunds, disputes,
                    "Current Zeffy payment amount is missing or invalid", reviewReasons);
            return finish(payment, correctedItems, already, reviewReasons);
        }
        if (!"USD".equalsIgnoreCase(payment.getCurrency())) {
            markAwaitingForReview(changes, refunds, disputes,
                    "Only USD corrections can be applied automatically", reviewReasons);
            return finish(payment, correctedItems, already, reviewReasons);
        }

        BigDecimal correctedLosses = correctedLossTotal(refunds, disputes);
        if (correctedLosses.compareTo(payment.getAmount()) > 0) {
            markAwaitingForReview(changes, refunds, disputes,
                    "Previously corrected refunds and disputes exceed the current payment amount",
                    reviewReasons);
            return finish(payment, correctedItems, already, reviewReasons);
        }

        for (ZeffyPaymentChange change : changes) {
            if (!"AWAITING_CORRECTION".equals(change.getCorrectionStatus())) continue;
            if (change.getPreviousAmount() == null || change.getCurrentAmount() == null
                    || change.getPreviousAmount().signum() < 0 || change.getCurrentAmount().signum() < 0) {
                review(change, "Payment amount change is missing valid before/after values", reviewReasons);
                continue;
            }
            if (change.getCurrentAmount().compareTo(correctedLosses) < 0) {
                review(change, "Changed payment amount is below already corrected refunds or disputes", reviewReasons);
                continue;
            }
            BigDecimal delta = change.getCurrentAmount().subtract(change.getPreviousAmount());
            if (delta.signum() == 0) {
                change.setCorrectionStatus("NOT_REQUIRED");
                change.setCorrectionSummary("Payment amount did not change");
                changeRepository.save(change);
                continue;
            }
            JournalEntry entry;
            try {
                entry = postFinancialCorrection(payment, delta,
                        "Zeffy payment amount correction " + payment.getZeffyPaymentId(),
                        "Zeffy amount change " + change.getId());
            } catch (IllegalArgumentException ex) {
                review(change, ex.getMessage(), reviewReasons);
                continue;
            }
            change.setCorrectionStatus("CORRECTED");
            change.setCorrectionJournalEntry(entry);
            change.setCorrectedAt(now());
            String summary = "Payment amount " + money(change.getPreviousAmount()) + " -> "
                    + money(change.getCurrentAmount()) + journalText(entry);
            change.setCorrectionSummary(summary);
            changeRepository.save(change);
            correctedItems.add(new CorrectedItem(change, null, null, summary));
        }

        for (ZeffyRefund refund : refunds) {
            if (!"AWAITING_CORRECTION".equals(refund.getCorrectionStatus())) continue;
            String invalid = validateLoss(payment, refund.getAmount(), refund.getCurrency(), correctedLosses,
                    "refund " + refund.getZeffyRefundId());
            if (invalid != null) {
                review(refund, invalid, reviewReasons);
                continue;
            }
            JournalEntry entry;
            try {
                entry = postFinancialCorrection(payment, refund.getAmount().negate(),
                        "Zeffy refund " + refund.getZeffyRefundId() + " for payment "
                                + payment.getZeffyPaymentId(),
                        "Zeffy refund " + refund.getZeffyRefundId());
            } catch (IllegalArgumentException ex) {
                review(refund, ex.getMessage(), reviewReasons);
                continue;
            }
            refund.setCorrectionStatus("CORRECTED");
            refund.setCorrectionJournalEntry(entry);
            refund.setCorrectedAmount(refund.getAmount());
            refund.setCorrectedAt(now());
            String summary = "Refund " + money(refund.getAmount()) + " corrected" + journalText(entry);
            refund.setCorrectionSummary(summary);
            refundRepository.save(refund);
            correctedLosses = correctedLosses.add(refund.getAmount());
            correctedItems.add(new CorrectedItem(null, refund, null, summary));
        }

        for (ZeffyDispute dispute : disputes) {
            if (!"AWAITING_CORRECTION".equals(dispute.getCorrectionStatus())) continue;
            String invalid = validateLoss(payment, dispute.getAmount(), dispute.getCurrency(), correctedLosses,
                    "dispute " + dispute.getZeffyDisputeId());
            if (invalid != null) {
                review(dispute, invalid, reviewReasons);
                continue;
            }
            JournalEntry entry;
            try {
                entry = postFinancialCorrection(payment, dispute.getAmount().negate(),
                        "Lost Zeffy dispute " + dispute.getZeffyDisputeId() + " for payment "
                                + payment.getZeffyPaymentId(),
                        "Zeffy dispute " + dispute.getZeffyDisputeId());
            } catch (IllegalArgumentException ex) {
                review(dispute, ex.getMessage(), reviewReasons);
                continue;
            }
            dispute.setCorrectionStatus("CORRECTED");
            dispute.setCorrectionJournalEntry(entry);
            dispute.setCorrectedAmount(dispute.getAmount());
            dispute.setCorrectedAt(now());
            String summary = "Lost dispute " + money(dispute.getAmount()) + " corrected" + journalText(entry);
            dispute.setCorrectionSummary(summary);
            disputeRepository.save(dispute);
            correctedLosses = correctedLosses.add(dispute.getAmount());
            correctedItems.add(new CorrectedItem(null, null, dispute, summary));
        }

        if (!correctedItems.isEmpty() && payment.getMemberPayment() != null) {
            BigDecimal retained = payment.getAmount().subtract(correctedLosses);
            MembershipService.ZeffyMembershipCorrection membership =
                    membershipService.applyZeffyCorrection(payment.getMemberPayment().getId(), retained);
            appendMembershipSummary(correctedItems, membership.summary());
        }

        return finish(payment, correctedItems, already, reviewReasons);
    }

    private JournalEntry postFinancialCorrection(
            ZeffyPayment payment, BigDecimal signedAmount, String description, String reference) {
        if (signedAmount.signum() == 0) return null;
        if (payment.getJournalEntry() == null) {
            throw new IllegalArgumentException("Applied payment does not have an original journal entry");
        }
        if (payment.getPaymentCreatedAt() == null) {
            throw new IllegalArgumentException("Applied payment does not have an original payment date");
        }
        LocalDate originalDate = payment.getPaymentCreatedAt()
                .atZoneSameInstant(CHURCH_ZONE).toLocalDate();
        return financeService.recordJournalCorrection(new FinanceService.JournalCorrectionRequest(
                payment.getJournalEntry().getId(), originalDate, signedAmount, description, reference));
    }

    private String validateLoss(ZeffyPayment payment, BigDecimal amount, String currency,
                                BigDecimal alreadyCorrected, String label) {
        if (amount == null || amount.signum() < 0) return "Invalid amount for " + label;
        if (!payment.getCurrency().equalsIgnoreCase(currency)) return "Currency mismatch for " + label;
        if (alreadyCorrected.add(amount).compareTo(payment.getAmount()) > 0) {
            return "Correction for " + label + " would exceed the current payment amount";
        }
        return null;
    }

    private BigDecimal correctedLossTotal(List<ZeffyRefund> refunds, List<ZeffyDispute> disputes) {
        BigDecimal total = refunds.stream()
                .filter(refund -> refund.getCorrectedAmount() != null
                        || "CORRECTED".equals(refund.getCorrectionStatus()))
                .map(refund -> refund.getCorrectedAmount() == null
                        ? refund.getAmount() : refund.getCorrectedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.add(disputes.stream()
                .filter(dispute -> dispute.getCorrectedAmount() != null
                        || "CORRECTED".equals(dispute.getCorrectionStatus()))
                .map(dispute -> dispute.getCorrectedAmount() == null
                        ? dispute.getAmount() : dispute.getCorrectedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private int countCorrected(List<ZeffyPaymentChange> changes, List<ZeffyRefund> refunds,
                               List<ZeffyDispute> disputes) {
        return (int) (changes.stream().filter(c -> "CORRECTED".equals(c.getCorrectionStatus())).count()
                + refunds.stream().filter(r -> r.getCorrectedAmount() != null
                        || "CORRECTED".equals(r.getCorrectionStatus())).count()
                + disputes.stream().filter(d -> d.getCorrectedAmount() != null
                        || "CORRECTED".equals(d.getCorrectionStatus())).count());
    }

    private void markAwaitingForReview(List<ZeffyPaymentChange> changes, List<ZeffyRefund> refunds,
                                       List<ZeffyDispute> disputes, String reason,
                                       List<String> reviewReasons) {
        changes.stream().filter(c -> "AWAITING_CORRECTION".equals(c.getCorrectionStatus()))
                .forEach(c -> review(c, reason, reviewReasons));
        refunds.stream().filter(r -> "AWAITING_CORRECTION".equals(r.getCorrectionStatus()))
                .forEach(r -> review(r, reason, reviewReasons));
        disputes.stream().filter(d -> "AWAITING_CORRECTION".equals(d.getCorrectionStatus()))
                .forEach(d -> review(d, reason, reviewReasons));
    }

    private void review(ZeffyPaymentChange change, String reason, List<String> reasons) {
        change.setCorrectionStatus("NEEDS_REVIEW");
        change.setCorrectionSummary(reason);
        changeRepository.save(change);
        reasons.add(reason);
    }

    private void review(ZeffyRefund refund, String reason, List<String> reasons) {
        refund.setCorrectionStatus("NEEDS_REVIEW");
        refund.setCorrectionSummary(reason);
        refundRepository.save(refund);
        reasons.add(reason);
    }

    private void review(ZeffyDispute dispute, String reason, List<String> reasons) {
        dispute.setCorrectionStatus("NEEDS_REVIEW");
        dispute.setCorrectionSummary(reason);
        disputeRepository.save(dispute);
        reasons.add(reason);
    }

    private void appendMembershipSummary(List<CorrectedItem> items, String membershipSummary) {
        for (CorrectedItem item : items) {
            String summary = truncate(item.baseSummary() + "; membership: " + membershipSummary);
            if (item.change() != null) {
                item.change().setCorrectionSummary(summary);
                changeRepository.save(item.change());
            } else if (item.refund() != null) {
                item.refund().setCorrectionSummary(summary);
                refundRepository.save(item.refund());
            } else {
                item.dispute().setCorrectionSummary(summary);
                disputeRepository.save(item.dispute());
            }
        }
    }

    private CorrectionOutcome finish(ZeffyPayment payment, List<CorrectedItem> corrected,
                                     int already, List<String> reviewReasons) {
        String review = reviewReasons.isEmpty() ? null : truncate(String.join("; ", reviewReasons));
        String summary = corrected.isEmpty()
                ? (review == null ? "No pending Zeffy corrections" : "Correction requires review")
                : "Applied " + corrected.size() + " Zeffy correction(s)"
                + (review == null ? "" : "; additional correction requires review");
        if (review != null) {
            payment.setProcessingStatus("NEEDS_REVIEW");
            payment.setOutcomeReason(review);
        }
        paymentRepository.save(payment);
        return new CorrectionOutcome(corrected.size(), already, reviewReasons.size(), summary, review);
    }

    private String journalText(JournalEntry entry) {
        return entry == null ? "; no journal entry required" : "; journal " + entry.getId();
    }

    private String money(BigDecimal value) {
        return "$" + value.toPlainString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String truncate(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record CorrectedItem(
            ZeffyPaymentChange change, ZeffyRefund refund, ZeffyDispute dispute,
            String baseSummary) {
    }
}
