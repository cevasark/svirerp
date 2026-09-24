package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.membership.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ZeffyPaymentCorrectionServiceTest {

    private ZeffyPaymentRepository payments;
    private ZeffyRefundRepository refunds;
    private ZeffyDisputeRepository disputes;
    private ZeffyPaymentChangeRepository changes;
    private FinanceService finance;
    private MembershipService memberships;
    private ZeffyPaymentCorrectionService service;

    @BeforeEach
    void setUp() {
        payments = mock(ZeffyPaymentRepository.class);
        refunds = mock(ZeffyRefundRepository.class);
        disputes = mock(ZeffyDisputeRepository.class);
        changes = mock(ZeffyPaymentChangeRepository.class);
        finance = mock(FinanceService.class);
        memberships = mock(MembershipService.class);
        service = new ZeffyPaymentCorrectionService(
                payments, refunds, disputes, changes, finance, memberships);
        when(finance.recordJournalCorrection(any())).thenAnswer(invocation ->
                JournalEntry.builder().id(UUID.randomUUID()).build());
        when(memberships.applyZeffyCorrection(any(), any())).thenReturn(
                new MembershipService.ZeffyMembershipCorrection("Member active -> Member active"));
    }

    @Test
    void partialRefundPostsReversalAndReducesMembershipToRetainedAmount() {
        ZeffyPayment payment = appliedPayment("200.00");
        ZeffyRefund refund = refund(payment, "50.00", "AWAITING_CORRECTION");
        arrange(payment, List.of(refund), List.of(), List.of());

        ZeffyPaymentCorrectionService.CorrectionOutcome outcome = service.correctPayment(payment.getId());

        ArgumentCaptor<FinanceService.JournalCorrectionRequest> journal =
                ArgumentCaptor.forClass(FinanceService.JournalCorrectionRequest.class);
        verify(finance).recordJournalCorrection(journal.capture());
        assertThat(journal.getValue().signedAmount()).isEqualByComparingTo("-50.00");
        assertThat(journal.getValue().entryDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 1));
        verify(memberships).applyZeffyCorrection(payment.getMemberPayment().getId(),
                new BigDecimal("150.00"));
        assertThat(refund.getCorrectionStatus()).isEqualTo("CORRECTED");
        assertThat(refund.getCorrectedAmount()).isEqualByComparingTo("50.00");
        assertThat(refund.getCorrectionSummary()).contains("membership");
        assertThat(outcome.corrected()).isEqualTo(1);
    }

    @Test
    void fullRefundMarksTheMembershipContributionRefunded() {
        ZeffyPayment payment = appliedPayment("100.00");
        ZeffyRefund refund = refund(payment, "100.00", "AWAITING_CORRECTION");
        arrange(payment, List.of(refund), List.of(), List.of());

        service.correctPayment(payment.getId());

        verify(memberships).applyZeffyCorrection(payment.getMemberPayment().getId(),
                new BigDecimal("0.00"));
        assertThat(refund.getCorrectionStatus()).isEqualTo("CORRECTED");
        assertThat(refund.getCorrectedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void lostDisputePostsAReversalAndReducesMembership() {
        ZeffyPayment payment = appliedPayment("150.00");
        ZeffyDispute dispute = dispute(payment, "40.00", "AWAITING_CORRECTION");
        arrange(payment, List.of(), List.of(dispute), List.of());

        service.correctPayment(payment.getId());

        ArgumentCaptor<FinanceService.JournalCorrectionRequest> journal =
                ArgumentCaptor.forClass(FinanceService.JournalCorrectionRequest.class);
        verify(finance).recordJournalCorrection(journal.capture());
        assertThat(journal.getValue().signedAmount()).isEqualByComparingTo("-40.00");
        verify(memberships).applyZeffyCorrection(payment.getMemberPayment().getId(),
                new BigDecimal("110.00"));
        assertThat(dispute.getCorrectionStatus()).isEqualTo("CORRECTED");
        assertThat(dispute.getCorrectedAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void lostDisputeCannotDuplicateAnAlreadyCorrectedRefundBeyondPaymentAmount() {
        ZeffyPayment payment = appliedPayment("100.00");
        ZeffyRefund refund = refund(payment, "75.00", "CORRECTED");
        refund.setCorrectedAmount(new BigDecimal("75.00"));
        ZeffyDispute dispute = dispute(payment, "50.00", "AWAITING_CORRECTION");
        arrange(payment, List.of(refund), List.of(dispute), List.of());

        ZeffyPaymentCorrectionService.CorrectionOutcome outcome = service.correctPayment(payment.getId());

        assertThat(dispute.getCorrectionStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(outcome.needsReview()).isEqualTo(1);
        verifyNoInteractions(finance, memberships);
    }

    @Test
    void paymentAmountIncreasePostsAdjustingEntryAndUpdatesContribution() {
        ZeffyPayment payment = appliedPayment("250.00");
        ZeffyPaymentChange change = ZeffyPaymentChange.builder()
                .id(UUID.randomUUID()).zeffyPayment(payment).changeKind("UPDATED")
                .previousAmount(new BigDecimal("200.00"))
                .currentAmount(new BigDecimal("250.00"))
                .correctionStatus("AWAITING_CORRECTION").build();
        arrange(payment, List.of(), List.of(), List.of(change));

        service.correctPayment(payment.getId());

        ArgumentCaptor<FinanceService.JournalCorrectionRequest> journal =
                ArgumentCaptor.forClass(FinanceService.JournalCorrectionRequest.class);
        verify(finance).recordJournalCorrection(journal.capture());
        assertThat(journal.getValue().signedAmount()).isEqualByComparingTo("50.00");
        verify(memberships).applyZeffyCorrection(payment.getMemberPayment().getId(),
                new BigDecimal("250.00"));
        assertThat(change.getCorrectionStatus()).isEqualTo("CORRECTED");
    }

    @Test
    void rerunDoesNotPostASecondCorrection() {
        ZeffyPayment payment = appliedPayment("100.00");
        ZeffyRefund refund = refund(payment, "25.00", "CORRECTED");
        refund.setCorrectedAmount(new BigDecimal("25.00"));
        arrange(payment, List.of(refund), List.of(), List.of());

        ZeffyPaymentCorrectionService.CorrectionOutcome outcome = service.correctPayment(payment.getId());

        assertThat(outcome.corrected()).isZero();
        assertThat(outcome.alreadyCorrected()).isEqualTo(1);
        verifyNoInteractions(finance, memberships);
    }

    private void arrange(ZeffyPayment payment, List<ZeffyRefund> paymentRefunds,
                         List<ZeffyDispute> paymentDisputes,
                         List<ZeffyPaymentChange> paymentChanges) {
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(java.util.Optional.of(payment));
        when(refunds.findByZeffyPayment_IdOrderByRefundCreatedAtAsc(payment.getId()))
                .thenReturn(paymentRefunds);
        when(disputes.findByZeffyPayment_IdOrderByDisputeCreatedAtAsc(payment.getId()))
                .thenReturn(paymentDisputes);
        when(changes.findByZeffyPayment_IdOrderByObservedAtAsc(payment.getId()))
                .thenReturn(paymentChanges);
    }

    private ZeffyPayment appliedPayment(String amount) {
        MemberPayment contribution = MemberPayment.builder().id(UUID.randomUUID()).build();
        return ZeffyPayment.builder()
                .id(UUID.randomUUID()).zeffyPaymentId("pay-1")
                .status("succeeded").amount(new BigDecimal(amount)).currency("USD")
                .paymentCreatedAt(OffsetDateTime.parse("2026-01-01T18:00:00Z"))
                .appliedAt(OffsetDateTime.parse("2026-01-02T12:00:00Z"))
                .journalEntry(JournalEntry.builder().id(UUID.randomUUID()).build())
                .memberPayment(contribution).processingStatus("PROCESSED").build();
    }

    private ZeffyRefund refund(ZeffyPayment payment, String amount, String correctionStatus) {
        return ZeffyRefund.builder().id(UUID.randomUUID()).zeffyRefundId("refund-1")
                .zeffyPayment(payment).amount(new BigDecimal(amount)).currency("USD")
                .status("succeeded").correctionStatus(correctionStatus)
                .refundCreatedAt(OffsetDateTime.parse("2026-02-01T12:00:00Z")).build();
    }

    private ZeffyDispute dispute(ZeffyPayment payment, String amount, String correctionStatus) {
        return ZeffyDispute.builder().id(UUID.randomUUID()).zeffyDisputeId("dispute-1")
                .zeffyPayment(payment).amount(new BigDecimal(amount)).currency("USD")
                .status("lost").correctionStatus(correctionStatus)
                .disputeCreatedAt(OffsetDateTime.parse("2026-02-01T12:00:00Z")).build();
    }
}
