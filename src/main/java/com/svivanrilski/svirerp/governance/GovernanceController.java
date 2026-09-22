package com.svivanrilski.svirerp.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GovernanceController {

    private final GovernanceService service;

    // ── Trustee ──────────────────────────────────────────────────────────────

    @GetMapping("/api/organizations/{orgId}/trustees")
    public Page<Trustee> listTrustees(@PathVariable UUID orgId, Pageable pageable) {
        return service.findTrusteesByOrg(orgId, pageable);
    }

    @GetMapping("/api/trustees/{id}")
    public Trustee getTrustee(@PathVariable UUID id) {
        return service.findTrusteeById(id);
    }

    @PostMapping("/api/trustees")
    public ResponseEntity<Trustee> createTrustee(@Valid @RequestBody Trustee trustee) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTrustee(trustee));
    }

    @PutMapping("/api/trustees/{id}")
    public Trustee updateTrustee(@PathVariable UUID id, @Valid @RequestBody Trustee trustee) {
        return service.updateTrustee(id, trustee);
    }

    @DeleteMapping("/api/trustees/{id}")
    public ResponseEntity<Void> deleteTrustee(@PathVariable UUID id) {
        service.deleteTrustee(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/trustees/{id}/renew")
    public Trustee renewTrustee(@PathVariable UUID id) {
        return service.renewTrustee(id);
    }

    // ── TrusteeDocument ──────────────────────────────────────────────────────

    @GetMapping("/api/trustees/{trusteeId}/documents")
    public Page<TrusteeDocument> listDocuments(@PathVariable UUID trusteeId, Pageable pageable) {
        return service.findDocumentsByTrustee(trusteeId, pageable);
    }

    @GetMapping("/api/trustee-documents/{id}")
    public TrusteeDocument getDocument(@PathVariable UUID id) {
        return service.findDocumentById(id);
    }

    @PostMapping("/api/trustee-documents")
    public ResponseEntity<TrusteeDocument> createDocument(@Valid @RequestBody TrusteeDocument doc) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDocument(doc));
    }

    @PutMapping("/api/trustee-documents/{id}")
    public TrusteeDocument updateDocument(@PathVariable UUID id, @Valid @RequestBody TrusteeDocument doc) {
        return service.updateDocument(id, doc);
    }

    @DeleteMapping("/api/trustee-documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        service.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // ── Committee ─────────────────────────────────────────────────────────────

    @GetMapping("/api/organizations/{orgId}/committees")
    public Page<Committee> listCommittees(@PathVariable UUID orgId, Pageable pageable) {
        return service.findCommitteesByOrg(orgId, pageable);
    }

    @GetMapping("/api/committees/{id}")
    public Committee getCommittee(@PathVariable UUID id) {
        return service.findCommitteeById(id);
    }

    @PostMapping("/api/committees")
    public ResponseEntity<Committee> createCommittee(@Valid @RequestBody Committee committee) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCommittee(committee));
    }

    @PutMapping("/api/committees/{id}")
    public Committee updateCommittee(@PathVariable UUID id, @Valid @RequestBody Committee committee) {
        return service.updateCommittee(id, committee);
    }

    @DeleteMapping("/api/committees/{id}")
    public ResponseEntity<Void> deleteCommittee(@PathVariable UUID id) {
        service.deleteCommittee(id);
        return ResponseEntity.noContent().build();
    }

    // ── CommitteeMember ───────────────────────────────────────────────────────

    @GetMapping("/api/committees/{committeeId}/members")
    public Page<CommitteeMember> listCommitteeMembers(@PathVariable UUID committeeId, Pageable pageable) {
        return service.findMembersByCommittee(committeeId, pageable);
    }

    @GetMapping("/api/committee-members/{id}")
    public CommitteeMember getCommitteeMember(@PathVariable UUID id) {
        return service.findCommitteeMemberById(id);
    }

    @PostMapping("/api/committee-members")
    public ResponseEntity<CommitteeMember> createCommitteeMember(@Valid @RequestBody CommitteeMember cm) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCommitteeMember(cm));
    }

    @PutMapping("/api/committee-members/{id}")
    public CommitteeMember updateCommitteeMember(@PathVariable UUID id, @Valid @RequestBody CommitteeMember cm) {
        return service.updateCommitteeMember(id, cm);
    }

    @DeleteMapping("/api/committee-members/{id}")
    public ResponseEntity<Void> deleteCommitteeMember(@PathVariable UUID id) {
        service.deleteCommitteeMember(id);
        return ResponseEntity.noContent().build();
    }

    // ── CommitteeMeeting ─────────────────────────────────────────────────────

    @GetMapping("/api/committees/{committeeId}/meetings")
    public Page<CommitteeMeeting> listMeetings(@PathVariable UUID committeeId, Pageable pageable) {
        return service.findMeetingsByCommittee(committeeId, pageable);
    }

    @GetMapping("/api/committee-meetings/{id}")
    public CommitteeMeeting getMeeting(@PathVariable UUID id) {
        return service.findMeetingById(id);
    }

    @PostMapping("/api/committee-meetings")
    public ResponseEntity<CommitteeMeeting> createMeeting(@Valid @RequestBody CommitteeMeeting meeting) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMeeting(meeting));
    }

    @PutMapping("/api/committee-meetings/{id}")
    public CommitteeMeeting updateMeeting(@PathVariable UUID id, @Valid @RequestBody CommitteeMeeting meeting) {
        return service.updateMeeting(id, meeting);
    }

    @DeleteMapping("/api/committee-meetings/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable UUID id) {
        service.deleteMeeting(id);
        return ResponseEntity.noContent().build();
    }

    // ── CommitteeResolution ───────────────────────────────────────────────────

    @GetMapping("/api/committees/{committeeId}/resolutions")
    public Page<CommitteeResolution> listResolutions(@PathVariable UUID committeeId, Pageable pageable) {
        return service.findResolutionsByCommittee(committeeId, pageable);
    }

    @GetMapping("/api/committee-resolutions/{id}")
    public CommitteeResolution getResolution(@PathVariable UUID id) {
        return service.findResolutionById(id);
    }

    @PostMapping("/api/committee-resolutions")
    public ResponseEntity<CommitteeResolution> createResolution(@Valid @RequestBody CommitteeResolution resolution) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createResolution(resolution));
    }

    @PutMapping("/api/committee-resolutions/{id}")
    public CommitteeResolution updateResolution(@PathVariable UUID id, @Valid @RequestBody CommitteeResolution resolution) {
        return service.updateResolution(id, resolution);
    }

    @DeleteMapping("/api/committee-resolutions/{id}")
    public ResponseEntity<Void> deleteResolution(@PathVariable UUID id) {
        service.deleteResolution(id);
        return ResponseEntity.noContent().build();
    }

    // ── MeetingMinutes ───────────────────────────────────────────────────────

    @GetMapping("/api/organizations/{orgId}/meeting-minutes")
    public Page<MeetingMinutes> listMeetingMinutes(@PathVariable UUID orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false, defaultValue = "false") boolean openActionItemsOnly,
            Pageable pageable) {
        return service.findMeetingMinutesByOrg(orgId, fromDate, openActionItemsOnly, pageable);
    }

    @GetMapping("/api/meeting-minutes/{id}")
    public MeetingMinutes getMeetingMinutes(@PathVariable UUID id) {
        return service.findMeetingMinutesById(id);
    }

    @PostMapping("/api/meeting-minutes")
    public ResponseEntity<MeetingMinutes> createMeetingMinutes(@Valid @RequestBody MeetingMinutes minutes) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMeetingMinutes(minutes));
    }

    @PutMapping("/api/meeting-minutes/{id}")
    public MeetingMinutes updateMeetingMinutes(@PathVariable UUID id, @Valid @RequestBody MeetingMinutes minutes) {
        return service.updateMeetingMinutes(id, minutes);
    }

    @DeleteMapping("/api/meeting-minutes/{id}")
    public ResponseEntity<Void> deleteMeetingMinutes(@PathVariable UUID id) {
        service.deleteMeetingMinutes(id);
        return ResponseEntity.noContent().build();
    }

    // ── ActionItem ────────────────────────────────────────────────────────────

    @GetMapping("/api/meeting-minutes/{meetingMinutesId}/action-items")
    public List<ActionItem> listActionItems(@PathVariable UUID meetingMinutesId) {
        return service.findActionItemsByMeeting(meetingMinutesId);
    }

    @PostMapping("/api/action-items")
    public ResponseEntity<ActionItem> createActionItem(@Valid @RequestBody ActionItem item) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createActionItem(item));
    }

    @PutMapping("/api/action-items/{id}")
    public ActionItem updateActionItem(@PathVariable UUID id, @Valid @RequestBody ActionItem item) {
        return service.updateActionItem(id, item);
    }

    @DeleteMapping("/api/action-items/{id}")
    public ResponseEntity<Void> deleteActionItem(@PathVariable UUID id) {
        service.deleteActionItem(id);
        return ResponseEntity.noContent().build();
    }

    // ── Project ──────────────────────────────────────────────────────────────

    @GetMapping("/api/organizations/{orgId}/projects")
    public Page<Project> listProjects(@PathVariable UUID orgId,
            @RequestParam(required = false) String status, Pageable pageable) {
        return service.findProjectsByOrg(orgId, status, pageable);
    }

    @GetMapping("/api/projects/{id}")
    public Project getProject(@PathVariable UUID id) {
        return service.findProjectById(id);
    }

    @PostMapping("/api/projects")
    public ResponseEntity<Project> createProject(@Valid @RequestBody Project project) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProject(project));
    }

    @PutMapping("/api/projects/{id}")
    public Project updateProject(@PathVariable UUID id, @Valid @RequestBody Project project) {
        return service.updateProject(id, project);
    }

    @DeleteMapping("/api/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        service.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    // ── ProjectTask ──────────────────────────────────────────────────────────

    @GetMapping("/api/projects/{projectId}/tasks")
    public List<ProjectTask> listProjectTasks(@PathVariable UUID projectId) {
        return service.findTasksByProject(projectId);
    }

    @PostMapping("/api/project-tasks")
    public ResponseEntity<ProjectTask> createProjectTask(@Valid @RequestBody ProjectTask task) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProjectTask(task));
    }

    @PutMapping("/api/project-tasks/{id}")
    public ProjectTask updateProjectTask(@PathVariable UUID id, @Valid @RequestBody ProjectTask task) {
        return service.updateProjectTask(id, task);
    }

    @DeleteMapping("/api/project-tasks/{id}")
    public ResponseEntity<Void> deleteProjectTask(@PathVariable UUID id) {
        service.deleteProjectTask(id);
        return ResponseEntity.noContent().build();
    }

    // ── ProjectChecklist / ProjectChecklistItem ─────────────────────────────
    // A sibling of ProjectTask under Project, not nested under one — see GovernanceService's
    // ProjectChecklist section for why (V50).

    @GetMapping("/api/projects/{projectId}/checklists")
    public List<ProjectChecklist> listChecklists(@PathVariable UUID projectId) {
        return service.findChecklistsByProject(projectId);
    }

    // A dedicated request record rather than @Valid ProjectChecklist itself — the entity's own
    // project field is @NotNull (correct for the entity), but the client must never supply it here
    // since it's already resolved from the projectId path variable; reusing the entity for the
    // request body would force the client to redundantly echo it back, same reasoning as
    // CommentRequest below.
    public record ChecklistRequest(@NotBlank String title, LocalDate completionDate) {
    }

    @PostMapping("/api/projects/{projectId}/checklists")
    public ResponseEntity<ProjectChecklist> createChecklist(@PathVariable UUID projectId,
            @Valid @RequestBody ChecklistRequest request) {
        ProjectChecklist created = service.createChecklist(projectId, request.title(), request.completionDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/project-checklists/{id}")
    public ProjectChecklist updateChecklist(@PathVariable UUID id, @Valid @RequestBody ChecklistRequest request) {
        return service.updateChecklist(id, request.title(), request.completionDate());
    }

    @DeleteMapping("/api/project-checklists/{id}")
    public ResponseEntity<Void> deleteChecklist(@PathVariable UUID id) {
        service.deleteChecklist(id);
        return ResponseEntity.noContent().build();
    }

    public record ChecklistItemRequest(@NotBlank String text) {
    }

    @GetMapping("/api/project-checklists/{checklistId}/items")
    public List<ProjectChecklistItem> listChecklistItems(@PathVariable UUID checklistId) {
        return service.findChecklistItems(checklistId);
    }

    @PostMapping("/api/project-checklists/{checklistId}/items")
    public ResponseEntity<ProjectChecklistItem> addChecklistItem(@PathVariable UUID checklistId,
            @Valid @RequestBody ChecklistItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addChecklistItem(checklistId, request.text()));
    }

    @DeleteMapping("/api/project-checklist-items/{id}")
    public ResponseEntity<Void> deleteChecklistItem(@PathVariable UUID id) {
        service.deleteChecklistItem(id);
        return ResponseEntity.noContent().build();
    }

    // detail is optional (no @NotBlank) — Done/Skip both work with no note at all.
    public record ChecklistItemActionRequest(String detail) {
    }

    @PostMapping("/api/project-checklist-items/{id}/done")
    public ProjectChecklistItem markChecklistItemDone(@PathVariable UUID id,
            @RequestBody(required = false) ChecklistItemActionRequest request) {
        return service.markChecklistItemDone(id, request != null ? request.detail() : null);
    }

    @PostMapping("/api/project-checklist-items/{id}/skip")
    public ProjectChecklistItem markChecklistItemSkipped(@PathVariable UUID id,
            @RequestBody(required = false) ChecklistItemActionRequest request) {
        return service.markChecklistItemSkipped(id, request != null ? request.detail() : null);
    }

    @PostMapping("/api/project-checklist-items/{id}/reopen")
    public ProjectChecklistItem reopenChecklistItem(@PathVariable UUID id) {
        return service.reopenChecklistItem(id);
    }

    // ── ProjectComment / ProjectTaskComment ─────────────────────────────────
    // POST bodies only ever carry the comment text — authorName is always resolved server-side
    // from the caller's own session (see resolveAuthorName), never trusted from the client.

    public record CommentRequest(@NotBlank String comment) {
    }

    @GetMapping("/api/projects/{projectId}/comments")
    public List<ProjectComment> listProjectComments(@PathVariable UUID projectId) {
        return service.findCommentsByProject(projectId);
    }

    @PostMapping("/api/projects/{projectId}/comments")
    public ResponseEntity<ProjectComment> createProjectComment(@PathVariable UUID projectId,
            @Valid @RequestBody CommentRequest request, Authentication authentication) {
        ProjectComment created = service.createProjectComment(
                projectId, request.comment(), resolveAuthorName(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/api/project-comments/{id}")
    public ResponseEntity<Void> deleteProjectComment(@PathVariable UUID id) {
        service.deleteProjectComment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/project-tasks/{taskId}/comments")
    public List<ProjectTaskComment> listProjectTaskComments(@PathVariable UUID taskId) {
        return service.findCommentsByTask(taskId);
    }

    @PostMapping("/api/project-tasks/{taskId}/comments")
    public ResponseEntity<ProjectTaskComment> createProjectTaskComment(@PathVariable UUID taskId,
            @Valid @RequestBody CommentRequest request, Authentication authentication) {
        ProjectTaskComment created = service.createProjectTaskComment(
                taskId, request.comment(), resolveAuthorName(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/api/project-task-comments/{id}")
    public ResponseEntity<Void> deleteProjectTaskComment(@PathVariable UUID id) {
        service.deleteProjectTaskComment(id);
        return ResponseEntity.noContent().build();
    }

    /** Same logic as AuthController#me — Google's display name, or the local-admin username —
     *  since a comment's author is always "whoever is logged in right now," never client-supplied. */
    private String resolveAuthorName(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getFullName();
        }
        return authentication.getName();
    }
}
