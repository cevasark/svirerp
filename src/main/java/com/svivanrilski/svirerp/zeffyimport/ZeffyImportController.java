package com.svivanrilski.svirerp.zeffyimport;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ZeffyImportController {

    private final ZeffyImportService service;

    // ── Batches ──────────────────────────────────────────────────────────────

    @GetMapping("/api/organizations/{orgId}/zeffy-imports")
    public Page<ZeffyImportBatch> listBatches(@PathVariable UUID orgId, Pageable pageable) {
        return service.findBatchesByOrg(orgId, pageable);
    }

    @PostMapping("/api/organizations/{orgId}/zeffy-imports/preview")
    public ResponseEntity<ZeffyImportBatch> preview(
            @PathVariable UUID orgId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.previewImport(orgId, file));
    }

    @GetMapping("/api/zeffy-imports/{batchId}")
    public ZeffyImportBatch getBatch(@PathVariable UUID batchId) {
        return service.findBatchById(batchId);
    }

    @GetMapping("/api/zeffy-imports/{batchId}/summary")
    public ZeffyImportService.ZeffyImportSummary getSummary(@PathVariable UUID batchId) {
        return service.getSummary(batchId);
    }

    @GetMapping("/api/zeffy-imports/{batchId}/rows")
    public Page<ZeffyImportRow> getRows(@PathVariable UUID batchId, Pageable pageable) {
        return service.findRows(batchId, pageable);
    }

    @PostMapping("/api/organizations/{orgId}/zeffy-imports/{batchId}/commit")
    public ZeffyImportService.ZeffyImportCommitResult commit(
            @PathVariable UUID orgId, @PathVariable UUID batchId) {
        return service.commitImport(orgId, batchId);
    }

    /** One-time backfill for already-committed Ticket rows whose campaign has since been flagged
     *  as a membership payment — see ZeffyImportService#reprocessMembershipRows. Idempotent. */
    @PostMapping("/api/organizations/{orgId}/zeffy-imports/reprocess-membership-rows")
    public ZeffyImportService.ReprocessMembershipResult reprocessMembershipRows(@PathVariable UUID orgId) {
        return service.reprocessMembershipRows(orgId);
    }

    // ── Campaign mappings ────────────────────────────────────────────────────

    @GetMapping("/api/organizations/{orgId}/zeffy-campaign-mappings")
    public List<ZeffyCampaignMapping> listMappings(@PathVariable UUID orgId) {
        return service.findMappingsByOrg(orgId);
    }

    @PostMapping("/api/organizations/{orgId}/zeffy-campaign-mappings/bulk")
    public List<ZeffyCampaignMapping> upsertMappings(@PathVariable UUID orgId,
            @Valid @RequestBody List<ZeffyImportService.CampaignMappingRequest> requests) {
        return service.upsertCampaignMappings(orgId, requests);
    }

    @DeleteMapping("/api/zeffy-campaign-mappings/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        service.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }
}
