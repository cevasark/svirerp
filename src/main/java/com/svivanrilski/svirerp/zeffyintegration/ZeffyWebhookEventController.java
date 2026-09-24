package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/zeffy-webhook-events")
@RequiredArgsConstructor
public class ZeffyWebhookEventController {

    private final ZeffyWebhookService service;
    private final ZeffyPaymentCorrectionCoordinator correctionCoordinator;

    @GetMapping
    public Page<ZeffyWebhookService.EventResponse> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedTo,
            @PageableDefault(sort = "receivedAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return service.findEvents(eventType, status, resourceId, receivedFrom, receivedTo, pageable);
    }

    @PostMapping("/{id}/reprocess")
    public ZeffyWebhookService.EventResponse reprocess(@PathVariable UUID id) {
        return service.reprocess(id);
    }

    @GetMapping("/{id}/lifecycle")
    public ZeffyWebhookService.LifecycleResponse lifecycle(@PathVariable UUID id) {
        return service.lifecycle(id);
    }

    @PostMapping("/corrections/apply-pending")
    public ZeffyPaymentCorrectionCoordinator.ApplyPendingResult applyPendingCorrections() {
        return correctionCoordinator.applyPending();
    }
}
