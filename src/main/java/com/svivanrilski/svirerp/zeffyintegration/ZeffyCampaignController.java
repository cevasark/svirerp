package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zeffy-campaigns")
@RequiredArgsConstructor
public class ZeffyCampaignController {

    private final ZeffyIntegrationService service;

    @GetMapping
    public Page<ZeffyIntegrationService.CampaignResponse> list(
            @PageableDefault(sort = "title") Pageable pageable) {
        return service.findCampaigns(pageable);
    }

    @PutMapping("/{campaignId}/mapping")
    public ZeffyIntegrationService.CampaignResponse updateMapping(
            @PathVariable String campaignId,
            @RequestBody ZeffyIntegrationService.CampaignMappingRequest request) {
        return service.updateMapping(campaignId, request);
    }
}
