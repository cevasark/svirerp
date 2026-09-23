package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/zeffy")
@RequiredArgsConstructor
public class ZeffySettingsController {

    private final ZeffyIntegrationService service;

    @GetMapping("/status")
    public ZeffyIntegrationService.StatusResponse status() {
        return service.status();
    }

    @PutMapping("/configuration")
    public ZeffyIntegrationService.StatusResponse saveConfiguration(
            @RequestBody ZeffyIntegrationService.ConfigurationRequest request) {
        return service.saveConfiguration(request);
    }

    @PostMapping("/test-connection")
    public ZeffyIntegrationService.ConnectionTestResponse testConnection() {
        return service.testConnection();
    }

    @PostMapping("/sync-campaigns")
    public ZeffyIntegrationService.CampaignSyncResponse synchronizeCampaigns(Authentication authentication) {
        return service.synchronizeCampaigns(authentication != null ? authentication.getName() : null);
    }

    @GetMapping("/sync-runs")
    public Page<ZeffyIntegrationService.SyncRunResponse> syncRuns(
            @RequestParam(required = false) String syncType,
            @PageableDefault(sort = "startedAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return service.findSyncRuns(syncType, pageable);
    }
}
