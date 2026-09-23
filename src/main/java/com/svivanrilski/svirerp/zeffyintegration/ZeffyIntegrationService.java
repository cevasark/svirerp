package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.settings.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class ZeffyIntegrationService {

    private static final String API_KEY = "zeffy.api-key";
    private static final String WEBHOOK_SECRET = "zeffy.webhook-signing-secret";
    private static final String MODE = "zeffy.integration-mode";
    private static final String CAMPAIGNS = "CAMPAIGNS";
    private static final String PAYMENTS = "PAYMENTS";
    private static final String WEBHOOK_PATH = "/api/webhooks/zeffy";

    private final AppSettingService settingService;
    private final ZeffyApiClient apiClient;
    private final ZeffyCampaignSyncWriter syncWriter;
    private final ZeffyCampaignRepository campaignRepository;
    private final ZeffySyncRunService syncRunService;
    private final ZeffyPaymentSyncService paymentSyncService;
    private final ZeffySyncPaymentResultService paymentResultService;
    private final FinanceService financeService;
    private final AtomicBoolean campaignSyncRunning = new AtomicBoolean(false);

    public record ConfigurationRequest(String apiKey, String webhookSigningSecret, Boolean validateApiKey,
                                       String integrationMode) {
    }

    public record ConnectionTestResponse(boolean connected, String testedAt) {
    }

    public record StatusResponse(
            boolean apiKeyConfigured,
            boolean webhookSecretConfigured,
            String integrationMode,
            String webhookPath,
            long campaignCount,
            long confirmedMappingCount,
            SyncRunResponse latestCampaignSync,
            SyncRunResponse latestPaymentPreview,
            SyncRunResponse latestPaymentSync) {
    }

    public record CampaignSyncResponse(SyncRunResponse run) {
    }

    public record PaymentSyncRequest(String mode, LocalDate createdFrom, LocalDate createdThrough,
                                     UUID previewRunId) {
    }

    public record PaymentSyncResponse(SyncRunResponse run) {
    }

    public record PaymentSyncResultResponse(
            UUID id,
            String zeffyPaymentId,
            UUID zeffyPaymentRecordId,
            String outcome,
            String detail,
            String payloadSha256,
            String observedAt,
            java.math.BigDecimal amount,
            String currency,
            String campaignTitle,
            String buyerEmail) {
    }

    public record CampaignMappingRequest(
            String action,
            UUID fundId,
            UUID categoryAccountId,
            Boolean grantsMembershipCredit,
            String note) {
    }

    public record ReferenceResponse(UUID id, String code, String name) {
    }

    public record CampaignResponse(
            UUID id,
            String zeffyCampaignId,
            String title,
            String campaignType,
            String category,
            String status,
            String currency,
            boolean archived,
            String publicUrl,
            String zeffyCreatedAt,
            String zeffyUpdatedAt,
            String zeffyDeletedAt,
            String lastSyncedAt,
            boolean mappingConfirmed,
            String processingAction,
            ReferenceResponse fund,
            ReferenceResponse categoryAccount,
            boolean grantsMembershipCredit,
            String mappingNote,
            String suggestedAction,
            boolean suggestedMembershipCredit) {
    }

    public record SyncRunResponse(
            UUID id,
            String syncType,
            String status,
            String triggerType,
            String initiatedBy,
            String startedAt,
            String completedAt,
            String requestedFrom,
            String requestedTo,
            String startingCursor,
            String endingCursor,
            String executionMode,
            UUID previewRunId,
            int fetchedCount,
            int insertedCount,
            int updatedCount,
            int ignoredCount,
            int failedCount,
            int alreadyAppliedCount,
            int eligibleCount,
            int needsMappingCount,
            int needsReviewCount,
            int processedCount,
            String errorSummary) {
    }

    public StatusResponse status() {
        return new StatusResponse(
                settingService.hasValue(API_KEY),
                settingService.hasValue(WEBHOOK_SECRET),
                settingService.getDecryptedValue(MODE).orElse("DISABLED"),
                WEBHOOK_PATH,
                campaignRepository.count(),
                campaignRepository.countByMappingConfirmedTrue(),
                toResponse(syncRunService.latest(CAMPAIGNS)),
                toResponse(syncRunService.latest(PAYMENTS, "PREVIEW")),
                toResponse(syncRunService.latest(PAYMENTS)));
    }

    public StatusResponse saveConfiguration(ConfigurationRequest request) {
        String apiKey = trimToNull(request.apiKey());
        String webhookSecret = trimToNull(request.webhookSigningSecret());
        if (webhookSecret != null && !webhookSecret.startsWith("whsec_")) {
            throw new IllegalArgumentException("Zeffy webhook signing secret must start with whsec_");
        }
        String mode = trimToNull(request.integrationMode());
        if (mode != null) {
            mode = mode.toUpperCase(Locale.ROOT);
            if (!"DISABLED".equals(mode) && !"RECORD_ONLY".equals(mode) && !"LIVE".equals(mode)) {
                throw new IllegalArgumentException("Zeffy integration mode must be DISABLED, RECORD_ONLY, or LIVE");
            }
            if (!"DISABLED".equals(mode) && webhookSecret == null && !settingService.hasValue(WEBHOOK_SECRET)) {
                throw new IllegalArgumentException("Configure the Zeffy webhook signing secret before enabling webhook receipt");
            }
        }
        boolean candidateTested = false;
        if (apiKey != null && Boolean.TRUE.equals(request.validateApiKey())) {
            apiClient.testConnection(apiKey);
            candidateTested = true;
        }
        if ("LIVE".equals(mode)) {
            if (apiKey == null && !settingService.hasValue(API_KEY)) {
                throw new IllegalArgumentException("Configure the Zeffy API key before enabling LIVE mode");
            }
            if (campaignRepository.count() == 0) {
                throw new IllegalArgumentException("Synchronize Zeffy campaigns before enabling LIVE mode");
            }
            if (campaignRepository
                    .countByMappingConfirmedFalseAndStatusIgnoreCaseAndIsArchivedFalseAndZeffyDeletedAtIsNull("active") > 0) {
                throw new IllegalArgumentException("Confirm every current Zeffy campaign mapping before enabling LIVE mode");
            }
            if (!candidateTested) {
                if (apiKey != null) apiClient.testConnection(apiKey);
                else apiClient.testConnection();
            }
        }
        if (apiKey != null) settingService.updateValue(API_KEY, apiKey);

        if (webhookSecret != null) settingService.updateValue(WEBHOOK_SECRET, webhookSecret);
        if (mode != null) settingService.updateValue(MODE, mode);
        return status();
    }

    public ConnectionTestResponse testConnection() {
        apiClient.testConnection();
        return new ConnectionTestResponse(true, OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    public CampaignSyncResponse synchronizeCampaigns(String initiatedBy) {
        if (!campaignSyncRunning.compareAndSet(false, true)) {
            throw new IllegalArgumentException("A campaign synchronization is already running");
        }
        ZeffySyncRun run = null;
        try {
            run = syncRunService.start(CAMPAIGNS, "MANUAL", initiatedBy);
            ZeffyApiClient.CampaignFetch fetch = apiClient.fetchAllCampaigns();
            ZeffyCampaignSyncWriter.Result result = syncWriter.apply(fetch.campaigns());
            return new CampaignSyncResponse(toResponse(syncRunService.finish(
                    run.getId(), result, fetch.endingCursor())));
        } catch (RuntimeException ex) {
            if (run != null) {
                try {
                    syncRunService.fail(run.getId(), safeError(ex));
                } catch (RuntimeException recordingFailure) {
                    ex.addSuppressed(recordingFailure);
                }
            }
            throw ex;
        } finally {
            campaignSyncRunning.set(false);
        }
    }

    public PaymentSyncResponse synchronizePayments(PaymentSyncRequest request, String initiatedBy) {
        if (request == null || request.mode() == null) {
            throw new IllegalArgumentException("Payment synchronization mode is required");
        }
        ZeffySyncRun run = switch (request.mode().trim().toUpperCase(Locale.ROOT)) {
            case "PREVIEW" -> paymentSyncService.preview(
                    request.createdFrom(), request.createdThrough(), initiatedBy);
            case "APPLY" -> paymentSyncService.apply(request.previewRunId(), initiatedBy);
            default -> throw new IllegalArgumentException("Payment synchronization mode must be PREVIEW or APPLY");
        };
        return new PaymentSyncResponse(toResponse(run));
    }

    @Transactional(readOnly = true)
    public Page<PaymentSyncResultResponse> findPaymentSyncResults(UUID runId, Pageable pageable) {
        return paymentResultService.find(runId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CampaignResponse> findCampaigns(Pageable pageable) {
        return campaignRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SyncRunResponse> findSyncRuns(String syncType, Pageable pageable) {
        return syncRunService.find(syncType, pageable).map(this::toResponse);
    }

    @Transactional
    public CampaignResponse updateMapping(String zeffyCampaignId, CampaignMappingRequest request) {
        ZeffyCampaign campaign = campaignRepository.findWithMappingByZeffyCampaignId(zeffyCampaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Zeffy campaign not found: " + zeffyCampaignId));
        String action = request.action() == null ? null : request.action().trim().toUpperCase(Locale.ROOT);
        if (!"APPLY".equals(action) && !"IGNORE".equals(action)) {
            throw new IllegalArgumentException("Campaign action must be APPLY or IGNORE");
        }

        if ("APPLY".equals(action)) {
            if (request.fundId() == null) throw new IllegalArgumentException("An APPLY mapping requires a Fund");
            if (request.categoryAccountId() == null) {
                throw new IllegalArgumentException("An APPLY mapping requires an income Account");
            }
            Fund fund = financeService.findFundById(request.fundId());
            Account account = financeService.findAccountById(request.categoryAccountId());
            if (!"revenue".equalsIgnoreCase(account.getAccountType())) {
                throw new IllegalArgumentException("The mapped Account must be a revenue account");
            }
            campaign.setFund(fund);
            campaign.setCategoryAccount(account);
            campaign.setGrantsMembershipCredit(Boolean.TRUE.equals(request.grantsMembershipCredit()));
        } else {
            campaign.setFund(null);
            campaign.setCategoryAccount(null);
            campaign.setGrantsMembershipCredit(false);
        }
        campaign.setProcessingAction(action);
        campaign.setMappingNote(trimToNull(request.note()));
        campaign.setMappingConfirmed(true);
        return toResponse(campaignRepository.save(campaign));
    }

    private CampaignResponse toResponse(ZeffyCampaign campaign) {
        return new CampaignResponse(
                campaign.getId(), campaign.getZeffyCampaignId(), campaign.getTitle(),
                campaign.getCampaignType(), campaign.getCategory(), campaign.getStatus(),
                campaign.getCurrency(), Boolean.TRUE.equals(campaign.getIsArchived()), campaign.getPublicUrl(),
                text(campaign.getZeffyCreatedAt()), text(campaign.getZeffyUpdatedAt()),
                text(campaign.getZeffyDeletedAt()), text(campaign.getLastSyncedAt()),
                Boolean.TRUE.equals(campaign.getMappingConfirmed()), campaign.getProcessingAction(),
                fundRef(campaign.getFund()), accountRef(campaign.getCategoryAccount()),
                Boolean.TRUE.equals(campaign.getGrantsMembershipCredit()), campaign.getMappingNote(),
                Boolean.TRUE.equals(campaign.getIsArchived()) || campaign.getZeffyDeletedAt() != null
                        ? "IGNORE" : "APPLY",
                "donation_form".equalsIgnoreCase(campaign.getCampaignType()));
    }

    private SyncRunResponse toResponse(ZeffySyncRun run) {
        return run == null ? null : new SyncRunResponse(
                run.getId(), run.getSyncType(), run.getStatus(), run.getTriggerType(), run.getInitiatedBy(),
                text(run.getStartedAt()), text(run.getCompletedAt()), text(run.getRequestedFrom()),
                text(run.getRequestedTo()), run.getStartingCursor(), run.getEndingCursor(),
                run.getExecutionMode(), run.getPreviewRun() == null ? null : run.getPreviewRun().getId(),
                run.getFetchedCount(), run.getInsertedCount(), run.getUpdatedCount(),
                run.getIgnoredCount(), run.getFailedCount(), run.getAlreadyAppliedCount(),
                run.getEligibleCount(), run.getNeedsMappingCount(), run.getNeedsReviewCount(),
                run.getProcessedCount(), run.getErrorSummary());
    }

    private PaymentSyncResultResponse toResponse(ZeffySyncPaymentResult result) {
        ZeffyPayment payment = result.getZeffyPayment();
        return new PaymentSyncResultResponse(
                result.getId(), result.getZeffyPaymentId(), payment == null ? null : payment.getId(),
                result.getOutcome(), result.getDetail(), result.getPayloadSha256(), text(result.getObservedAt()),
                payment == null ? null : payment.getAmount(), payment == null ? null : payment.getCurrency(),
                payment == null ? null : payment.getCampaignTitle(), payment == null ? null : payment.getBuyerEmail());
    }

    private ReferenceResponse fundRef(Fund fund) {
        return fund == null ? null : new ReferenceResponse(fund.getId(), fund.getFundCode(), fund.getFundName());
    }

    private ReferenceResponse accountRef(Account account) {
        return account == null ? null
                : new ReferenceResponse(account.getId(), account.getAccountNumber(), account.getAccountName());
    }

    private String safeError(RuntimeException ex) {
        if (ex instanceof ZeffyApiException || ex instanceof IllegalArgumentException) {
            return ex.getMessage();
        }
        return "Unexpected synchronization failure";
    }

    private String text(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
