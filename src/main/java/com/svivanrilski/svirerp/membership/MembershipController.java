package com.svivanrilski.svirerp.membership;

import com.svivanrilski.svirerp.zeffyintegration.ZeffyMembershipReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService service;
    private final MemberImportService importService;
    private final ZeffyMembershipReconciliationService zeffyMembershipReconciliationService;

    // ── MembershipType ──────────────────────────────────────────────────────

    @GetMapping("/api/membership-types")
    public Page<MembershipType> listTypes(Pageable pageable) {
        return service.findAllTypes(pageable);
    }

    @GetMapping("/api/membership-types/{id}")
    public MembershipType getType(@PathVariable UUID id) {
        return service.findTypeById(id);
    }

    @PostMapping("/api/membership-types")
    public ResponseEntity<MembershipType> createType(@Valid @RequestBody MembershipType type) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createType(type));
    }

    @PutMapping("/api/membership-types/{id}")
    public MembershipType updateType(@PathVariable UUID id, @Valid @RequestBody MembershipType type) {
        return service.updateType(id, type);
    }

    @DeleteMapping("/api/membership-types/{id}")
    public ResponseEntity<Void> deleteType(@PathVariable UUID id) {
        service.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    // ── Member ───────────────────────────────────────────────────────────────

    @GetMapping("/api/members")
    public Page<Member> listMembers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID membershipTypeId,
            Pageable pageable) {
        return service.findAllMembers(status, membershipTypeId, pageable);
    }

    @GetMapping("/api/members/summary")
    public MembershipService.MemberSummary getMemberSummary() {
        return service.getMemberSummary();
    }

    @GetMapping("/api/members/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] csv = importService.buildImportTemplateCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("member-import-template.csv")
                        .build()
                        .toString())
                .body(csv);
    }

    @PostMapping("/api/members/import")
    public MemberImportService.MemberImportResult importMembers(@RequestParam("file") MultipartFile file) {
        return importService.importMembers(file);
    }

    @GetMapping("/api/members/{id}")
    public Member getMember(@PathVariable UUID id) {
        return service.findMemberById(id);
    }

    @GetMapping("/api/members/expired")
    public List<Member> expiredMembers() {
        return service.findExpiredMembers();
    }

    /** Manual re-run of tier computation for every member in the org — tier can go stale purely
     *  from time passing (a qualifying payment ages past its 1-year window with no new payment
     *  event), so this does not depend on a new external payment event to catch up. */
    @PostMapping("/api/members/recompute-tiers")
    public RecomputeTiersResult recomputeTiers() {
        return new RecomputeTiersResult(service.recomputeAllTiers());
    }

    @PostMapping("/api/members/rebuild-from-zeffy")
    public ZeffyMembershipReconciliationService.RebuildResult rebuildFromZeffy() {
        return zeffyMembershipReconciliationService.rebuild();
    }

    public record RecomputeTiersResult(int membersProcessed) {
    }

    @PostMapping("/api/members")
    public ResponseEntity<Member> createMember(@Valid @RequestBody Member member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMember(member));
    }

    @PutMapping("/api/members/{id}")
    public Member updateMember(@PathVariable UUID id, @Valid @RequestBody Member member) {
        return service.updateMember(id, member);
    }

    @DeleteMapping("/api/members/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable UUID id) {
        service.deleteMember(id);
        return ResponseEntity.noContent().build();
    }

    // ── MemberPayment ────────────────────────────────────────────────────────

    @GetMapping("/api/member-payments")
    public Page<MemberPayment> listPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            Pageable pageable) {
        return service.findPayments(fromDate, pageable);
    }

    @GetMapping("/api/members/{memberId}/payments")
    public Page<MemberPayment> listPayments(@PathVariable UUID memberId, Pageable pageable) {
        return service.findPaymentsByMember(memberId, pageable);
    }

    @GetMapping("/api/member-payments/{id}")
    public MemberPayment getPayment(@PathVariable UUID id) {
        return service.findPaymentById(id);
    }

    @PostMapping("/api/member-payments")
    public ResponseEntity<MemberPayment> createPayment(@Valid @RequestBody MemberPayment payment) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPayment(payment));
    }

    @PutMapping("/api/member-payments/{id}")
    public MemberPayment updatePayment(@PathVariable UUID id, @Valid @RequestBody MemberPayment payment) {
        return service.updatePayment(id, payment);
    }

    @DeleteMapping("/api/member-payments/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        service.deletePayment(id);
        return ResponseEntity.noContent().build();
    }
}
