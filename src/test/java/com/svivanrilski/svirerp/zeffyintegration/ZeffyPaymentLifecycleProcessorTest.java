package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ZeffyPaymentLifecycleProcessorTest {

    private ZeffyWebhookEventRepository events;
    private ZeffyPaymentRepository payments;
    private ZeffyPaymentChangeRepository changes;
    private ZeffyRefundRepository refunds;
    private ZeffyDisputeRepository disputes;
    private ZeffyPaymentCorrectionService corrections;
    private ZeffyPaymentLifecycleProcessor processor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        events = mock(ZeffyWebhookEventRepository.class);
        payments = mock(ZeffyPaymentRepository.class);
        changes = mock(ZeffyPaymentChangeRepository.class);
        refunds = mock(ZeffyRefundRepository.class);
        disputes = mock(ZeffyDisputeRepository.class);
        corrections = mock(ZeffyPaymentCorrectionService.class);
        processor = new ZeffyPaymentLifecycleProcessor(events, payments, changes, refunds, disputes, corrections,
                new ZeffyPaymentPayload(objectMapper));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(changes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(refunds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(disputes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(corrections.applyCorrections(any())).thenReturn(
                new ZeffyPaymentCorrectionService.CorrectionOutcome(
                        0, 0, 0, "No pending Zeffy corrections", null));
    }

    @Test
    void succeededRefundOnAppliedPaymentDelegatesAutomaticCorrection() throws Exception {
        ZeffyWebhookEvent event = updatedEvent();
        ZeffyPayment payment = appliedPayment();
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.of(payment));
        when(changes.findByWebhookEvent_Id(event.getId())).thenReturn(Optional.empty());
        when(refunds.findByZeffyRefundId("refund-1")).thenReturn(Optional.empty());
        when(corrections.applyCorrections(payment)).thenReturn(
                new ZeffyPaymentCorrectionService.CorrectionOutcome(
                        1, 0, 0, "Applied 1 Zeffy correction(s)", null));

        processor.recordUpdated(event.getId(), paymentWithRefund());

        ArgumentCaptor<ZeffyRefund> savedRefund = ArgumentCaptor.forClass(ZeffyRefund.class);
        verify(refunds).save(savedRefund.capture());
        assertThat(savedRefund.getValue().getCorrectionStatus()).isEqualTo("AWAITING_CORRECTION");
        assertThat(savedRefund.getValue().getAmount()).isEqualByComparingTo("25.00");
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        assertThat(event.getProcessingSummary()).contains("Applied 1");
        assertThat(payment.getJournalEntry()).isNotNull();
        verify(corrections).applyCorrections(payment);
    }

    @Test
    void lostDisputeOnAppliedPaymentDelegatesAutomaticCorrection() throws Exception {
        ZeffyWebhookEvent event = updatedEvent();
        ZeffyPayment payment = appliedPayment();
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.of(payment));
        when(changes.findByWebhookEvent_Id(event.getId())).thenReturn(Optional.empty());
        when(disputes.findByZeffyDisputeId("dispute-1")).thenReturn(Optional.empty());
        when(corrections.applyCorrections(payment)).thenReturn(
                new ZeffyPaymentCorrectionService.CorrectionOutcome(
                        1, 0, 0, "Applied 1 Zeffy correction(s)", null));

        processor.recordUpdated(event.getId(), paymentWithLostDispute());

        ArgumentCaptor<ZeffyDispute> savedDispute = ArgumentCaptor.forClass(ZeffyDispute.class);
        verify(disputes).save(savedDispute.capture());
        assertThat(savedDispute.getValue().getCorrectionStatus()).isEqualTo("AWAITING_CORRECTION");
        assertThat(savedDispute.getValue().getStatus()).isEqualTo("lost");
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        assertThat(event.getProcessingSummary()).contains("Applied 1");
    }

    @Test
    void deletingAppliedPaymentCreatesTombstoneAndReviewInsteadOfDeletingHistory() {
        ZeffyWebhookEvent event = ZeffyWebhookEvent.builder()
                .id(UUID.randomUUID()).eventType("payment.deleted").schemaVersion(1)
                .zeffyResourceId("pay-1").status("RECEIVED").processingAttemptCount(0)
                .dispatchedAt(OffsetDateTime.parse("2026-09-23T12:00:00Z")).build();
        ZeffyPayment payment = appliedPayment();
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.of(payment));
        when(changes.findByWebhookEvent_Id(event.getId())).thenReturn(Optional.empty());

        processor.recordDeleted(event.getId(), false);

        assertThat(payment.getDeletedAt()).isEqualTo(event.getDispatchedAt());
        assertThat(payment.getProcessingStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(event.getStatus()).isEqualTo("NEEDS_REVIEW");
        verify(payments, never()).delete(any());
    }

    @Test
    void olderDeleteDeliveryDoesNotTombstoneNewerPaymentState() {
        ZeffyWebhookEvent event = ZeffyWebhookEvent.builder()
                .id(UUID.randomUUID()).eventType("payment.deleted").schemaVersion(1)
                .zeffyResourceId("pay-1").status("RECEIVED").processingAttemptCount(0)
                .dispatchedAt(OffsetDateTime.parse("2026-09-23T12:00:00Z")).build();
        ZeffyPayment payment = appliedPayment();
        payment.setLastEventAt(OffsetDateTime.parse("2026-09-23T13:00:00Z"));
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.of(payment));
        when(changes.findByWebhookEvent_Id(event.getId())).thenReturn(Optional.empty());

        processor.recordDeleted(event.getId(), false);

        assertThat(payment.getDeletedAt()).isNull();
        assertThat(payment.getProcessingStatus()).isEqualTo("PROCESSED");
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        assertThat(event.getProcessingSummary()).contains("newer payment data");
    }

    private ZeffyWebhookEvent updatedEvent() {
        return ZeffyWebhookEvent.builder()
                .id(UUID.randomUUID()).eventType("payment.updated").schemaVersion(1)
                .zeffyResourceId("pay-1").status("RECEIVED").processingAttemptCount(0)
                .dispatchedAt(OffsetDateTime.parse("2026-09-23T12:00:00Z")).build();
    }

    private ZeffyPayment appliedPayment() {
        return ZeffyPayment.builder()
                .id(UUID.randomUUID()).zeffyPaymentId("pay-1").status("succeeded")
                .refundStatus("none").amount(new BigDecimal("100.00"))
                .eligibleAmount(new BigDecimal("100.00")).currency("USD")
                .paymentType("online").paymentCreatedAt(OffsetDateTime.parse("2026-01-01T12:00:00Z"))
                .campaignId("campaign-1").campaignTitle("Membership").contactId("contact-1")
                .latestPayload("{}").latestPayloadSha256("old-hash")
                .processingStatus("PROCESSED").appliedAt(OffsetDateTime.parse("2026-01-02T12:00:00Z"))
                .journalEntry(com.svivanrilski.svirerp.finance.JournalEntry.builder()
                        .id(UUID.randomUUID()).build())
                .build();
    }

    private JsonNode paymentWithRefund() throws Exception {
        return objectMapper.readTree("""
                {"id":"pay-1","status":"succeeded","refund_status":"partial",
                 "amount":10000,"eligible_amount":10000,"currency":"usd","type":"online",
                 "created":1767268800,"campaign_id":"campaign-1","description":"Membership",
                 "contact":"contact-1","refunds":[{"id":"refund-1","amount":2500,
                 "currency":"usd","status":"succeeded","created":1789981200}],"dispute":null}
                """);
    }

    private JsonNode paymentWithLostDispute() throws Exception {
        return objectMapper.readTree("""
                {"id":"pay-1","status":"succeeded","refund_status":"none",
                 "amount":10000,"eligible_amount":10000,"currency":"usd","type":"online",
                 "created":1767268800,"campaign_id":"campaign-1","description":"Membership",
                 "contact":"contact-1","refunds":[],"dispute":{"id":"dispute-1","amount":10000,
                 "currency":"usd","status":"lost","reason":"fraudulent","created":1789981200}}
                """);
    }
}
