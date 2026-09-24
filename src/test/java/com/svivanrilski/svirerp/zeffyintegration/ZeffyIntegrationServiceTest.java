package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.settings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ZeffyIntegrationServiceTest {

    private AppSettingService settings;
    private ZeffyApiClient apiClient;
    private ZeffyCampaignRepository campaigns;
    private ZeffySyncRunService runs;
    private ZeffyIntegrationService service;

    @BeforeEach
    void setUp() {
        settings = mock(AppSettingService.class);
        apiClient = mock(ZeffyApiClient.class);
        campaigns = mock(ZeffyCampaignRepository.class);
        runs = mock(ZeffySyncRunService.class);
        service = new ZeffyIntegrationService(settings, apiClient, mock(ZeffyCampaignSyncWriter.class),
                campaigns, runs, mock(ZeffyPaymentSyncService.class),
                mock(ZeffyContactSyncService.class), mock(ZeffyContactRepository.class),
                mock(ZeffySyncPaymentResultService.class), mock(FinanceService.class));
        when(settings.getDecryptedValue("zeffy.integration-mode"))
                .thenReturn(java.util.Optional.of("DISABLED"));
    }

    @Test
    void rejectedCandidateKeyDoesNotReplaceStoredKey() {
        doThrow(new ZeffyApiException(401, "Zeffy rejected the API key"))
                .when(apiClient).testConnection("bad-key");

        assertThatThrownBy(() -> service.saveConfiguration(
                new ZeffyIntegrationService.ConfigurationRequest("bad-key", null, true, null)))
                .isInstanceOf(ZeffyApiException.class);

        verify(settings, never()).updateValue("zeffy.api-key", "bad-key");
    }

    @Test
    void validatesCandidateBeforeSavingIt() {
        service.saveConfiguration(
                new ZeffyIntegrationService.ConfigurationRequest("new-key", null, true, null));

        InOrder order = inOrder(apiClient, settings);
        order.verify(apiClient).testConnection("new-key");
        order.verify(settings).updateValue("zeffy.api-key", "new-key");
    }

    @Test
    void recordOnlyModeRequiresWebhookSecret() {
        when(settings.hasValue("zeffy.webhook-signing-secret")).thenReturn(false);

        assertThatThrownBy(() -> service.saveConfiguration(
                new ZeffyIntegrationService.ConfigurationRequest(null, null, false, "RECORD_ONLY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhook signing secret");
    }

    @Test
    void savesSecretBeforeEnablingRecordOnlyMode() {
        service.saveConfiguration(new ZeffyIntegrationService.ConfigurationRequest(
                null, "whsec_new", false, "RECORD_ONLY"));

        InOrder order = inOrder(settings);
        order.verify(settings).updateValue("zeffy.webhook-signing-secret", "whsec_new");
        order.verify(settings).updateValue("zeffy.integration-mode", "RECORD_ONLY");
    }

    @Test
    void liveModeRequiresSynchronizedAndConfirmedCampaigns() {
        when(settings.hasValue("zeffy.api-key")).thenReturn(true);
        when(settings.hasValue("zeffy.webhook-signing-secret")).thenReturn(true);
        when(campaigns.count()).thenReturn(2L);
        when(campaigns.countByMappingConfirmedFalseAndStatusIgnoreCaseAndIsArchivedFalseAndZeffyDeletedAtIsNull("active"))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.saveConfiguration(
                new ZeffyIntegrationService.ConfigurationRequest(null, null, false, "LIVE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confirm every current");

        verify(settings, never()).updateValue("zeffy.integration-mode", "LIVE");
    }

    @Test
    void liveModeTestsConnectionBeforeSavingMode() {
        when(settings.hasValue("zeffy.api-key")).thenReturn(true);
        when(settings.hasValue("zeffy.webhook-signing-secret")).thenReturn(true);
        when(campaigns.count()).thenReturn(2L);

        service.saveConfiguration(
                new ZeffyIntegrationService.ConfigurationRequest(null, null, false, "LIVE"));

        InOrder order = inOrder(apiClient, settings);
        order.verify(apiClient).testConnection();
        order.verify(settings).updateValue("zeffy.integration-mode", "LIVE");
    }

    @Test
    void failedCampaignSyncIsRecordedBeforeErrorIsRethrown() {
        ZeffySyncRun run = ZeffySyncRun.builder().id(UUID.randomUUID()).build();
        when(runs.start("CAMPAIGNS", "MANUAL", "admin@example.com")).thenReturn(run);
        doThrow(new ZeffyApiException(502, "Could not reach the Zeffy API"))
                .when(apiClient).fetchAllCampaigns();

        assertThatThrownBy(() -> service.synchronizeCampaigns("admin@example.com"))
                .isInstanceOf(ZeffyApiException.class);

        verify(runs).fail(run.getId(), "Could not reach the Zeffy API");
    }
}
