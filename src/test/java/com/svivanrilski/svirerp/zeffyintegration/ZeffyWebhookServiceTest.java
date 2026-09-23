package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svivanrilski.svirerp.settings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ZeffyWebhookServiceTest {

    private AppSettingService settings;
    private ZeffyWebhookSignatureVerifier verifier;
    private ZeffyWebhookEventStore store;
    private ZeffyWebhookEventRepository repository;
    private ZeffyPaymentProcessingCoordinator coordinator;
    private ZeffyWebhookService service;

    @BeforeEach
    void setUp() {
        settings = mock(AppSettingService.class);
        verifier = mock(ZeffyWebhookSignatureVerifier.class);
        store = mock(ZeffyWebhookEventStore.class);
        repository = mock(ZeffyWebhookEventRepository.class);
        coordinator = mock(ZeffyPaymentProcessingCoordinator.class);
        service = new ZeffyWebhookService(settings, verifier, store,
                repository, new ObjectMapper(),
                coordinator);
        when(settings.getDecryptedValue("zeffy.integration-mode"))
                .thenReturn(Optional.of("RECORD_ONLY"));
        when(settings.getDecryptedValue("zeffy.webhook-signing-secret"))
                .thenReturn(Optional.of("whsec_test"));
        when(verifier.verify(any(), any(), eq("whsec_test")))
                .thenReturn(new ZeffyWebhookSignatureVerifier.VerifiedSignature(OffsetDateTime.now()));
        when(store.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsAllDocumentedEventTypesWithoutApplyingDomainEffects() {
        String[] types = {"payment.completed", "payment.created", "payment.updated", "payment.deleted",
                "contact.created", "contact.updated", "contact.deleted"};

        for (String type : types) {
            ZeffyWebhookService.ReceiptResponse response = service.receive(payload(type), "valid");
            assertThat(response.status()).isEqualTo("RECEIVED");
        }

        verify(store, times(types.length)).insert(argThat(event ->
                event.getProcessingAttemptCount() == 0 && event.getRawPayload() != null));
    }

    @Test
    void retainsUnknownTypeAsUnsupported() {
        assertThat(service.receive(payload("campaign.updated"), "valid").status())
                .isEqualTo("UNSUPPORTED");
        verify(store).insert(argThat(event -> "UNSUPPORTED".equals(event.getStatus())));
    }

    @Test
    void retainsUnknownSchemaVersionAsUnsupported() {
        byte[] payload = new String(payload("payment.completed"), StandardCharsets.UTF_8)
                .replace("\"version\":1", "\"version\":2")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(service.receive(payload, "valid").status()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void disabledModeRejectsBeforeSignatureVerificationOrStorage() {
        when(settings.getDecryptedValue("zeffy.integration-mode"))
                .thenReturn(Optional.of("DISABLED"));

        assertThatThrownBy(() -> service.receive(payload("payment.completed"), "valid"))
                .isInstanceOf(ZeffyWebhookException.class)
                .hasMessageContaining("disabled");
        verifyNoInteractions(verifier, store);
    }

    @Test
    void duplicateDeliveryReturnsSuccessAndUpdatesExistingAuditRow() {
        UUID localId = UUID.randomUUID();
        ZeffyWebhookEvent existing = ZeffyWebhookEvent.builder()
                .id(localId).status("RECEIVED").build();
        when(store.insert(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(store.recordDuplicate(any(), any(), any(), any())).thenReturn(existing);

        ZeffyWebhookService.ReceiptResponse response =
                service.receive(payload("payment.completed"), "valid");

        assertThat(response.id()).isEqualTo(localId);
        assertThat(response.duplicate()).isTrue();
        verify(store).recordDuplicate(any(), any(), any(), any());
    }

    @Test
    void malformedEnvelopeIsRejectedAfterSignatureVerification() {
        byte[] malformed = "{\"type\":\"payment.completed\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.receive(malformed, "valid"))
                .isInstanceOf(ZeffyWebhookException.class)
                .hasMessageContaining("id is required");
        verify(store, never()).insert(any());
    }

    @Test
    void recordOnlyModeNeverInvokesPaymentProcessing() {
        service.receive(payload("payment.completed"), "valid");

        verifyNoInteractions(coordinator);
    }

    @Test
    void liveModeProcessesNewCompletedPaymentAfterDurableInsert() {
        UUID id = UUID.randomUUID();
        when(settings.getDecryptedValue("zeffy.integration-mode")).thenReturn(Optional.of("LIVE"));
        when(store.insert(any())).thenAnswer(invocation -> {
            ZeffyWebhookEvent event = invocation.getArgument(0);
            event.setId(id);
            return event;
        });
        when(repository.findById(id)).thenReturn(Optional.of(
                ZeffyWebhookEvent.builder().id(id).status("PROCESSED").build()));

        ZeffyWebhookService.ReceiptResponse response =
                service.receive(payload("payment.completed"), "valid");

        verify(coordinator).process(id);
        assertThat(response.status()).isEqualTo("PROCESSED");
    }

    @Test
    void reprocessRequiresLiveMode() {
        assertThatThrownBy(() -> service.reprocess(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIVE mode");
        verifyNoInteractions(coordinator);
    }

    private byte[] payload(String type) {
        return ("{\"id\":\"6bd0ce41-3f6f-49ac-a797-c0a81ee10911\","
                + "\"type\":\"" + type + "\",\"version\":1,"
                + "\"dispatchedAt\":\"2026-09-23T12:00:00Z\","
                + "\"data\":{\"id\":\"b77ed0ad-2d24-445f-b601-35930c3564fe\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
