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

    @GetMapping("/api/zeffy-imports")
    public Page<ZeffyImportBatch> listBatches(Pageable pageable) {
        return service.findBatches(pageable);
    }

    @PostMapping("/api/zeffy-imports/preview")
    public ResponseEntity<ZeffyImportBatch> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.previewImport(file));
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

    @PostMapping("/api/zeffy-imports/{batchId}/commit")
    public ZeffyImportService.ZeffyImportCommitResult commit(@PathVariable UUID batchId) {
        return service.commitImport(batchId);
    }

    /** One-time backfill for already-committed Ticket rows whose campaign has since been flagged
     *  as a membership payment — see ZeffyImportService#reprocessMembershipRows. Idempotent. */
    @PostMapping("/api/zeffy-imports/reprocess-membership-rows")
    public ZeffyImportService.ReprocessMembershipResult reprocessMembershipRows() {
        return service.reprocessMembershipRows();
    }

    // ── Campaign mappings ────────────────────────────────────────────────────

    @GetMapping("/api/zeffy-campaign-mappings")
    public List<ZeffyCampaignMapping> listMappings() {
        return service.findMappings();
    }

    @PostMapping("/api/zeffy-campaign-mappings/bulk")
    public List<ZeffyCampaignMapping> upsertMappings(
            @Valid @RequestBody List<ZeffyImportService.CampaignMappingRequest> requests) {
        return service.upsertCampaignMappings(requests);
    }

    @DeleteMapping("/api/zeffy-campaign-mappings/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        service.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }
}
