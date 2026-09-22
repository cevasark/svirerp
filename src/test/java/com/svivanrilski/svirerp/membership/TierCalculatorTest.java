package com.svivanrilski.svirerp.membership;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.svivanrilski.svirerp.membership.TierCalculator.PaymentSnapshot;
import static com.svivanrilski.svirerp.membership.TierCalculator.TierResult;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the membership-period chaining rule confirmed with the user after a bulk historical
 * Zeffy import (~2 years of data in one go) made the previous "qualifying payment within the last
 * 12 months of today" rule produce very wrong-looking tiers — see TierCalculator's class doc for
 * the exact rule. "today" (LocalDate.now(America/Chicago)) is load-bearing for the active/inactive
 * check, so every test below anchors its dates relative to today rather than hardcoding absolute
 * dates, to stay valid regardless of when the suite runs.
 */
class TierCalculatorTest {

    private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("America/Chicago"));

    private PaymentSnapshot payment(String amount, LocalDate date) {
        return new PaymentSnapshot(new BigDecimal(amount), date);
    }

    @Test
    void no_payments_at_all_returns_null() {
        assertThat(TierCalculator.compute(List.of())).isNull();
        assertThat(TierCalculator.compute(null)).isNull();
    }

    @Test
    void only_sub_150_payments_stay_follower_active_no_expiry() {
        TierResult result = TierCalculator.compute(List.of(
                payment("10.00", TODAY.minusYears(3)),
                payment("50.00", TODAY.minusMonths(1))));

        assertThat(result.tierName()).isEqualTo(TierCalculator.FOLLOWER);
        assertThat(result.expiryDate()).isNull();
        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void single_recent_150_payment_is_active_member() {
        LocalDate paymentDate = TODAY.minusMonths(2);
        TierResult result = TierCalculator.compute(List.of(payment("150.00", paymentDate)));

        assertThat(result.tierName()).isEqualTo(TierCalculator.MEMBER);
        assertThat(result.expiryDate()).isEqualTo(paymentDate.plusYears(1));
        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void single_1000_payment_is_benefactor() {
        LocalDate paymentDate = TODAY.minusMonths(2);
        TierResult result = TierCalculator.compute(List.of(payment("1000.00", paymentDate)));

        assertThat(result.tierName()).isEqualTo(TierCalculator.BENEFACTOR);
        assertThat(result.expiryDate()).isEqualTo(paymentDate.plusYears(1));
    }

    @Test
    void lapsed_member_keeps_tier_but_goes_inactive() {
        // Gave $1000 nearly 2 years ago and never again — the exact "Teodora Popov" scenario that
        // prompted this rule change: should show as Benefactor/inactive, not silently downgraded
        // to Follower just because the expiry is in the past.
        LocalDate paymentDate = TODAY.minusMonths(21);
        TierResult result = TierCalculator.compute(List.of(payment("1000.00", paymentDate)));

        assertThat(result.tierName()).isEqualTo(TierCalculator.BENEFACTOR);
        assertThat(result.expiryDate()).isEqualTo(paymentDate.plusYears(1)).isBefore(TODAY);
        assertThat(result.status()).isEqualTo("inactive");
    }

    @Test
    void renewal_while_still_active_chains_from_expiry_not_from_new_payment_date() {
        LocalDate first = TODAY.minusMonths(11); // still active: expires in 1 month
        LocalDate renewal = TODAY; // paid again today, while still active
        TierResult result = TierCalculator.compute(List.of(
                payment("150.00", first),
                payment("150.00", renewal)));

        // Chained: new expiry = (first + 1yr) + 1yr, NOT renewal + 1yr.
        assertThat(result.expiryDate()).isEqualTo(first.plusYears(1).plusYears(1));
        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void renewal_on_the_exact_expiry_date_still_chains() {
        LocalDate first = TODAY.minusYears(1); // expires exactly today
        TierResult result = TierCalculator.compute(List.of(
                payment("150.00", first),
                payment("150.00", TODAY))); // renews exactly on expiry day

        assertThat(result.expiryDate()).isEqualTo(first.plusYears(1).plusYears(1));
        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void payment_after_lapse_starts_a_fresh_period_not_chained() {
        LocalDate first = TODAY.minusYears(3); // long since expired
        LocalDate second = TODAY.minusMonths(2); // a fresh payment, well after the first lapsed
        TierResult result = TierCalculator.compute(List.of(
                payment("150.00", first),
                payment("150.00", second)));

        // Fresh start from the second payment's own date, not chained off the first's expiry.
        assertThat(result.expiryDate()).isEqualTo(second.plusYears(1));
        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void tier_reflects_the_most_recent_periods_amount_not_cumulative_highest() {
        LocalDate benefactorPayment = TODAY.minusMonths(10); // still active
        LocalDate followUp150 = TODAY.minusMonths(1); // arrives while still active -> chains, but only $150
        TierResult result = TierCalculator.compute(List.of(
                payment("1000.00", benefactorPayment),
                payment("150.00", followUp150)));

        // Downgraded to Member for the new chained period — not "sticky" at Benefactor.
        assertThat(result.tierName()).isEqualTo(TierCalculator.MEMBER);
        assertThat(result.expiryDate()).isEqualTo(benefactorPayment.plusYears(1).plusYears(1));
    }

    @Test
    void out_of_order_input_is_still_processed_chronologically() {
        LocalDate first = TODAY.minusMonths(11);
        LocalDate second = TODAY;
        // Passed in reverse chronological order — compute() must sort internally.
        TierResult result = TierCalculator.compute(List.of(
                payment("150.00", second),
                payment("150.00", first)));

        assertThat(result.expiryDate()).isEqualTo(first.plusYears(1).plusYears(1));
    }

    @Test
    void payments_below_150_are_ignored_when_a_qualifying_payment_also_exists() {
        LocalDate qualifying = TODAY.minusMonths(1);
        TierResult result = TierCalculator.compute(List.of(
                payment("10.00", TODAY.minusYears(2)),
                payment("150.00", qualifying)));

        assertThat(result.tierName()).isEqualTo(TierCalculator.MEMBER);
        assertThat(result.expiryDate()).isEqualTo(qualifying.plusYears(1));
    }
}
