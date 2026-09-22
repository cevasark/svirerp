package com.svivanrilski.svirerp.membership;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Derives a member's tier/expiry/status from their completed-payment history. Stateless — shared
 * by the real tier recompute ({@link MembershipService#recomputeTier}) and the Zeffy import
 * preview, which simulates the tier a new/updated payment would produce before anything is
 * committed.
 *
 * <p>Tiers, low to high: Follower (any payment, including $0) &lt; Member ($150+ — VOTING_THRESHOLD,
 * grants voting rights) &lt; Benefactor ($1000+). "Member" is this org's pre-existing name for the
 * $150+ tier — not to be confused with the generic domain concept of a "Member" (any
 * {@code membership.Member} row, regardless of tier).
 *
 * <p><b>Membership-period chaining rule</b> (confirmed with the user after a historical bulk
 * import of ~2 years of Zeffy data made the previous "qualifying payment within the last 12
 * months of today" rule produce very wrong-looking results): a $150+ payment starts a 1-year
 * membership period from its own payment date — <em>unless</em> it arrives while a still-active
 * period is already running, in which case it extends that period by a year from its existing
 * expiry (chained, not restarted). Each period's tier is simply whatever that triggering payment's
 * amount qualifies for ($1000+ → Benefactor, $150–999 → Member) — not cumulative/sticky, so a
 * Benefactor's next $150 payment starts a Member-tier period. Once the final period's expiry has
 * passed, {@code status} becomes "inactive" but the tier itself is <b>not</b> reverted to
 * Follower — an admin needs to see what a lapsed member's last real tier was, not just "Follower."
 *
 * <p>Members with no $150+ payment at all (Follower-only, e.g. small one-off donations) are
 * deliberately left alone by this calculator — always Follower/active/no expiry, matching this
 * app's pre-chaining-rule behavior. Zeffy's Transactions export doesn't carry sub-$150 "Follower"
 * signups at all as of this writing, so properly modeling Follower expiry is a separate,
 * not-yet-solved problem.
 */
public final class TierCalculator {

    public static final String BENEFACTOR = "Benefactor";
    public static final String MEMBER = "Member";
    public static final String FOLLOWER = "Follower";

    public static final BigDecimal BENEFACTOR_THRESHOLD = new BigDecimal("1000.00");
    public static final BigDecimal VOTING_THRESHOLD = new BigDecimal("150.00");

    /** Dates on Zeffy's export are already Chicago-local calendar dates; "today" for the
     *  active-vs-lapsed check uses the same zone. */
    private static final ZoneId WINDOW_ZONE = ZoneId.of("America/Chicago");

    private TierCalculator() {
    }

    public record PaymentSnapshot(BigDecimal amount, LocalDate paymentDate) {
    }

    /** Null if there is no completed-payment history at all (caller decides how to treat that —
     *  see {@link MembershipService#recomputeTier}). {@code status} is "active" or "inactive". */
    public record TierResult(String tierName, LocalDate expiryDate, String status) {
    }

    public static TierResult compute(List<PaymentSnapshot> completedPayments) {
        if (completedPayments == null || completedPayments.isEmpty()) {
            return null;
        }

        List<PaymentSnapshot> qualifying = completedPayments.stream()
                .filter(p -> p.amount() != null && p.paymentDate() != null
                        && p.amount().compareTo(VOTING_THRESHOLD) >= 0)
                .sorted(Comparator.comparing(PaymentSnapshot::paymentDate))
                .toList();

        if (qualifying.isEmpty()) {
            // Only sub-$150 payments exist — Follower is a non-expiring baseline; see class doc
            // for why this app doesn't yet model Follower expiry.
            return new TierResult(FOLLOWER, null, "active");
        }

        LocalDate currentExpiry = null;
        String currentTier = null;
        for (PaymentSnapshot payment : qualifying) {
            // "Still active" includes the expiry date itself (valid-through semantics) — a
            // same-day renewal chains rather than restarting.
            boolean stillActive = currentExpiry != null && !payment.paymentDate().isAfter(currentExpiry);
            LocalDate periodStart = stillActive ? currentExpiry : payment.paymentDate();
            currentExpiry = periodStart.plusYears(1);
            currentTier = payment.amount().compareTo(BENEFACTOR_THRESHOLD) >= 0 ? BENEFACTOR : MEMBER;
        }

        LocalDate today = LocalDate.now(WINDOW_ZONE);
        String status = today.isAfter(currentExpiry) ? "inactive" : "active";
        return new TierResult(currentTier, currentExpiry, status);
    }
}
