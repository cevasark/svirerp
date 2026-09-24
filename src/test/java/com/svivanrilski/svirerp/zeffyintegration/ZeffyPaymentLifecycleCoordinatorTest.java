package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class ZeffyPaymentLifecycleCoordinatorTest {

    private ZeffyWebhookEventRepository events;
    private ZeffyApiClient apiClient;
    private ZeffyPaymentProcessingCoordinator paymentCoordinator;
    private ZeffyPaymentLifecycleProcessor processor;
    private ZeffyPaymentLifecycleCoordinator coordinator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        events = mock(ZeffyWebhookEventRepository.class);
        apiClient = mock(ZeffyApiClient.class);
        paymentCoordinator = mock(ZeffyPaymentProcessingCoordinator.class);
        processor = mock(ZeffyPaymentLifecycleProcessor.class);
        coordinator = new ZeffyPaymentLifecycleCoordinator(events, apiClient,
                new ZeffyPaymentPayload(objectMapper), paymentCoordinator, processor);
    }

    @Test
    void paymentUpdateFetchesAuthoritativeStateBeforeRecording() throws Exception {
        ZeffyWebhookEvent event = event("payment.updated", "{\"data\":{\"id\":\"pay-1\"}}");
        JsonNode payment = objectMapper.readTree("{\"id\":\"pay-1\",\"status\":\"pending\"}");
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(apiClient.fetchPayment("pay-1")).thenReturn(payment);

        coordinator.process(event.getId());

        verify(apiClient).fetchPayment("pay-1");
        verify(processor).recordUpdated(event.getId(), payment);
        verify(processor, never()).markError(any(), any());
    }

    @Test
    void missingUpdatedPaymentIsRecordedAsConfirmedDeletion() {
        ZeffyWebhookEvent event = event("payment.updated", "{\"data\":{\"id\":\"pay-1\"}}");
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(apiClient.fetchPayment("pay-1"))
                .thenThrow(new ZeffyApiException(404, "Zeffy payment was not found"));

        coordinator.process(event.getId());

        verify(processor).recordDeleted(event.getId(), true);
        verify(processor, never()).markError(any(), any());
    }

    @Test
    void succeededCreatedPaymentUsesInitialApplicationThenAddsLifecycleAudit() {
        ZeffyWebhookEvent event = event("payment.created", """
                {"data":{"id":"pay-1","status":"succeeded","amount":1000,
                 "eligible_amount":1000,"currency":"usd","created":1767268800,"refunds":[]}}
                """);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));

        coordinator.process(event.getId());

        verify(paymentCoordinator).process(event.getId());
        verify(processor).auditCreatedApplication(event.getId());
        verifyNoInteractions(apiClient);
    }

    private ZeffyWebhookEvent event(String type, String rawPayload) {
        return ZeffyWebhookEvent.builder()
                .id(UUID.randomUUID()).eventType(type).schemaVersion(1)
                .zeffyResourceId("pay-1").status("RECEIVED")
                .dispatchedAt(OffsetDateTime.parse("2026-09-23T12:00:00Z"))
                .rawPayload(rawPayload).build();
    }
}
