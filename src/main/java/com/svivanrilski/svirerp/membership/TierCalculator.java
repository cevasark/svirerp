package com.svivanrilski.svirerp.membership;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Derives membership periods and the current tier from completed payments. */
public final class TierCalculator {

    public static final String BENEFACTOR = "Benefactor";
    public static final String MEMBER = "Member";
    public static final String FOLLOWER = "Follower";

    public static final BigDecimal BENEFACTOR_THRESHOLD = new BigDecimal("1000.00");
    public static final BigDecimal VOTING_THRESHOLD = new BigDecimal("150.00");

    private static final ZoneId WINDOW_ZONE = ZoneId.of("America/Chicago");

    private TierCalculator() {
    }

    public record PaymentSnapshot(String paymentKey, BigDecimal amount, LocalDate paymentDate) {
        public PaymentSnapshot(BigDecimal amount, LocalDate paymentDate) {
            this(null, amount, paymentDate);
        }
    }

    public record PaymentPeriod(
            String paymentKey, String tierName, LocalDate periodStart, LocalDate periodEnd) {
    }

    public record TierResult(String tierName, LocalDate expiryDate, String status) {
    }

    public record Calculation(TierResult result, List<PaymentPeriod> periods) {
    }

    public static TierResult compute(List<PaymentSnapshot> completedPayments) {
        return calculate(completedPayments, LocalDate.now(WINDOW_ZONE)).result();
    }

    static TierResult compute(List<PaymentSnapshot> completedPayments, LocalDate asOfDate) {
        return calculate(completedPayments, asOfDate).result();
    }

    /**
     * Processes payments from oldest to newest. A same-tier renewal is appended to the existing
     * paid-through date. A lower tier is scheduled after the current tier expires. A higher tier
     * starts immediately and remains in force for one year after the existing paid-through date.
     */
    public static Calculation calculate(List<PaymentSnapshot> completedPayments, LocalDate asOfDate) {
        if (completedPayments == null || completedPayments.isEmpty()) {
            return new Calculation(null, List.of());
        }

        List<PaymentSnapshot> payments = completedPayments.stream()
                .filter(p -> p != null && p.amount() != null && p.paymentDate() != null)
                .sorted(Comparator.comparing(PaymentSnapshot::paymentDate))
                .toList();
        if (payments.isEmpty()) {
            return new Calculation(null, List.of());
        }

        List<PaymentPeriod> periods = new ArrayList<>();
        LocalDate paidThrough = null;
        boolean latestPaymentWasFollower = false;
        PaymentPeriod latestFollowerTransition = null;

        for (PaymentSnapshot payment : payments) {
            String paymentTier = tierFor(payment.amount());
            if (FOLLOWER.equals(paymentTier)) {
                LocalDate effectiveDate = paidThrough != null
                        && !payment.paymentDate().isAfter(paidThrough)
                        ? paidThrough
                        : payment.paymentDate();
                latestPaymentWasFollower = true;
                latestFollowerTransition = new PaymentPeriod(
                        payment.paymentKey(), FOLLOWER, effectiveDate, null);
                periods.add(latestFollowerTransition);
                continue;
            }

            latestPaymentWasFollower = false;
            LocalDate periodStart;
            LocalDate periodEnd;
            if (paidThrough == null || payment.paymentDate().isAfter(paidThrough)) {
                periodStart = payment.paymentDate();
                periodEnd = periodStart.plusYears(1);
            } else {
                String tierInForce = highestPaidTierAt(periods, payment.paymentDate());
                if (tierRank(paymentTier) > tierRank(tierInForce)) {
                    periodStart = payment.paymentDate();
                    periodEnd = paidThrough.plusYears(1);
                } else {
                    periodStart = paidThrough;
                    periodEnd = periodStart.plusYears(1);
                }
            }

            periods.add(new PaymentPeriod(
                    payment.paymentKey(), paymentTier, periodStart, periodEnd));
            if (paidThrough == null || periodEnd.isAfter(paidThrough)) {
                paidThrough = periodEnd;
            }
        }

        PaymentPeriod currentPaidPeriod = periods.stream()
                .filter(p -> !FOLLOWER.equals(p.tierName()))
                .filter(p -> !asOfDate.isBefore(p.periodStart()) && !asOfDate.isAfter(p.periodEnd()))
                .max(Comparator.comparingInt((PaymentPeriod p) -> tierRank(p.tierName()))
                        .thenComparing(PaymentPeriod::periodEnd))
                .orElse(null);

        if (currentPaidPeriod != null) {
            LocalDate visibleExpiry = extendAcrossContiguousSameTier(currentPaidPeriod, periods);
            return new Calculation(
                    new TierResult(currentPaidPeriod.tierName(), visibleExpiry, "active"),
                    List.copyOf(periods));
        }

        boolean followerIsLatest = latestFollowerTransition != null
                && !asOfDate.isBefore(latestFollowerTransition.periodStart())
                && latestPaymentWasFollower;
        if (followerIsLatest) {
            return new Calculation(
                    new TierResult(FOLLOWER, null, "active"), List.copyOf(periods));
        }

        PaymentPeriod lastPaidPeriod = periods.stream()
                .filter(p -> !FOLLOWER.equals(p.tierName()))
                .filter(p -> !p.periodEnd().isAfter(asOfDate))
                .max(Comparator.comparing(PaymentPeriod::periodEnd))
                .orElse(null);
        if (lastPaidPeriod != null) {
            return new Calculation(
                    new TierResult(lastPaidPeriod.tierName(), lastPaidPeriod.periodEnd(), "inactive"),
                    List.copyOf(periods));
        }

        return new Calculation(new TierResult(FOLLOWER, null, "active"), List.copyOf(periods));
    }

    private static String tierFor(BigDecimal amount) {
        if (amount.compareTo(BENEFACTOR_THRESHOLD) >= 0) return BENEFACTOR;
        if (amount.compareTo(VOTING_THRESHOLD) >= 0) return MEMBER;
        return FOLLOWER;
    }

    private static String highestPaidTierAt(List<PaymentPeriod> periods, LocalDate date) {
        return periods.stream()
                .filter(p -> !FOLLOWER.equals(p.tierName()))
                .filter(p -> !date.isBefore(p.periodStart()) && !date.isAfter(p.periodEnd()))
                .max(Comparator.comparingInt(p -> tierRank(p.tierName())))
                .map(PaymentPeriod::tierName)
                .orElse(null);
    }

    private static LocalDate extendAcrossContiguousSameTier(
            PaymentPeriod current, List<PaymentPeriod> periods) {
        LocalDate expiry = current.periodEnd();
        boolean extended;
        do {
            LocalDate currentExpiry = expiry;
            LocalDate nextExpiry = periods.stream()
                    .filter(p -> current.tierName().equals(p.tierName()))
                    .filter(p -> !p.periodStart().isAfter(currentExpiry))
                    .map(PaymentPeriod::periodEnd)
                    .filter(end -> end != null && end.isAfter(currentExpiry))
                    .max(LocalDate::compareTo)
                    .orElse(currentExpiry);
            extended = nextExpiry.isAfter(expiry);
            expiry = nextExpiry;
        } while (extended);
        return expiry;
    }

    private static int tierRank(String tierName) {
        if (BENEFACTOR.equals(tierName)) return 2;
        if (MEMBER.equals(tierName)) return 1;
        return 0;
    }
}
