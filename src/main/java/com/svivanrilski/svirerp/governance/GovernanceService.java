package com.svivanrilski.svirerp.governance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.organization.OrganizationService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GovernanceService {

    // Default term length used when renewing a lapsed trustee term.
    private static final int TRUSTEE_TERM_YEARS = 2;

    private static final Set<String> MEETING_TYPES = Set.of("regular", "special", "emergency", "annual");
    private static final Set<String> MEETING_STATUSES = Set.of("scheduled", "completed", "cancelled", "postponed");
    private static final Set<String> RESOLUTION_STATUSES = Set.of("passed", "failed", "tabled", "withdrawn");
    private static final Set<String> ACTION_ITEM_PRIORITIES = Set.of("high", "normal", "low");
    private static final Set<String> ACTION_ITEM_STATUSES = Set.of("new", "planned", "done");
    private static final Set<String> PROJECT_STATUSES =
            Set.of("planning", "in_progress", "on_hold", "completed", "cancelled");
    private static final Set<String> PROJECT_TASK_STATUSES = Set.of("todo", "in_progress", "blocked", "done");

    private final TrusteeRepository trusteeRepo;
    private final TrusteeDocumentRepository trusteeDocRepo;
    private final CommitteeRepository committeeRepo;
    private final CommitteeMemberRepository committeeMemberRepo;
    private final CommitteeMeetingRepository meetingRepo;
    private final CommitteeResolutionRepository resolutionRepo;
    private final MeetingMinutesRepository meetingMinutesRepo;
    private final ActionItemRepository actionItemRepo;
    private final ProjectRepository projectRepo;
    private final ProjectTaskRepository projectTaskRepo;
    private final ProjectCommentRepository projectCommentRepo;
    private final ProjectTaskCommentRepository projectTaskCommentRepo;
    private final ProjectChecklistRepository projectChecklistRepo;
    private final ProjectChecklistItemRepository projectChecklistItemRepo;
    private final OrganizationService orgService;
    private final PersonService personService;

    // ── Trustee ──────────────────────────────────────────────────────────────

    public Page<Trustee> findTrusteesByOrg(UUID orgId, Pageable pageable) {
        return trusteeRepo.findByOrgId(orgId, pageable);
    }

    public Trustee findTrusteeById(UUID id) {
        return trusteeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trustee", id));
    }

    @Transactional
    public Trustee createTrustee(Trustee trustee) {
        Person person = personService.findById(trustee.getPerson().getId());
        Organization org = orgService.findById(trustee.getOrg().getId());
        trustee.setPerson(person);
        trustee.setOrg(org);
        return trusteeRepo.save(trustee);
    }

    @Transactional
    public Trustee updateTrustee(UUID id, Trustee patch) {
        Trustee existing = findTrusteeById(id);
        existing.setTitle(patch.getTitle());
        existing.setRole(patch.getRole());
        existing.setTermStart(patch.getTermStart());
        existing.setTermEnd(patch.getTermEnd());
        existing.setIsActive(patch.getIsActive());
        existing.setIsOfficer(patch.getIsOfficer());
        existing.setNotes(patch.getNotes());
        return trusteeRepo.save(existing);
    }

    @Transactional
    public void deleteTrustee(UUID id) {
        if (!trusteeRepo.existsById(id)) throw new ResourceNotFoundException("Trustee", id);
        trusteeRepo.deleteById(id);
    }

    /**
     * Re-elects the same trustee for a fresh term, rather than letting
     * term_end silently drift forward — a lapsed term auto-renews in the
     * sense that the trustee keeps serving, but bumping the dates is always
     * an explicit action tied to an actual election.
     */
    @Transactional
    public Trustee renewTrustee(UUID id) {
        Trustee existing = findTrusteeById(id);
        LocalDate today = LocalDate.now();
        existing.setTermStart(today);
        existing.setTermEnd(today.plusYears(TRUSTEE_TERM_YEARS));
        return trusteeRepo.save(existing);
    }

    // ── TrusteeDocument ──────────────────────────────────────────────────────

    public Page<TrusteeDocument> findDocumentsByTrustee(UUID trusteeId, Pageable pageable) {
        return trusteeDocRepo.findByTrusteeId(trusteeId, pageable);
    }

    public TrusteeDocument findDocumentById(UUID id) {
        return trusteeDocRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TrusteeDocument", id));
    }

    @Transactional
    public TrusteeDocument createDocument(TrusteeDocument doc) {
        Trustee trustee = findTrusteeById(doc.getTrustee().getId());
        doc.setTrustee(trustee);
        return trusteeDocRepo.save(doc);
    }

    @Transactional
    public TrusteeDocument updateDocument(UUID id, TrusteeDocument patch) {
        TrusteeDocument existing = findDocumentById(id);
        existing.setDocumentType(patch.getDocumentType());
        existing.setFileUrl(patch.getFileUrl());
        existing.setUploadedAt(patch.getUploadedAt());
        existing.setNotes(patch.getNotes());
        return trusteeDocRepo.save(existing);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        if (!trusteeDocRepo.existsById(id)) throw new ResourceNotFoundException("TrusteeDocument", id);
        trusteeDocRepo.deleteById(id);
    }

    // ── Committee ─────────────────────────────────────────────────────────────

    public Page<Committee> findCommitteesByOrg(UUID orgId, Pageable pageable) {
        return committeeRepo.findByOrgId(orgId, pageable);
    }

    public Committee findCommitteeById(UUID id) {
        return committeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Committee", id));
    }

    @Transactional
    public Committee createCommittee(Committee committee) {
        Organization org = orgService.findById(committee.getOrg().getId());
        committee.setOrg(org);
        return committeeRepo.save(committee);
    }

    @Transactional
    public Committee updateCommittee(UUID id, Committee patch) {
        Committee existing = findCommitteeById(id);
        existing.setName(patch.getName());
        existing.setDescription(patch.getDescription());
        existing.setCommitteeType(patch.getCommitteeType());
        existing.setIsStanding(patch.getIsStanding());
        existing.setIsActive(patch.getIsActive());
        existing.setFormedDate(patch.getFormedDate());
        existing.setDissolvedDate(patch.getDissolvedDate());
        existing.setMeetingSchedule(patch.getMeetingSchedule());
        existing.setMandate(patch.getMandate());
        return committeeRepo.save(existing);
    }

    @Transactional
    public void deleteCommittee(UUID id) {
        if (!committeeRepo.existsById(id)) throw new ResourceNotFoundException("Committee", id);
        committeeRepo.deleteById(id);
    }

    // ── CommitteeMember ───────────────────────────────────────────────────────

    public Page<CommitteeMember> findMembersByCommittee(UUID committeeId, Pageable pageable) {
        return committeeMemberRepo.findByCommitteeId(committeeId, pageable);
    }

    public CommitteeMember findCommitteeMemberById(UUID id) {
        return committeeMemberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommitteeMember", id));
    }

    @Transactional
    public CommitteeMember createCommitteeMember(CommitteeMember cm) {
        Committee committee = findCommitteeById(cm.getCommittee().getId());
        Person person = personService.findById(cm.getPerson().getId());
        cm.setCommittee(committee);
        cm.setPerson(person);
        return committeeMemberRepo.save(cm);
    }

    @Transactional
    public CommitteeMember updateCommitteeMember(UUID id, CommitteeMember patch) {
        CommitteeMember existing = findCommitteeMemberById(id);
        existing.setRole(patch.getRole());
        existing.setStartDate(patch.getStartDate());
        existing.setEndDate(patch.getEndDate());
        existing.setIsActive(patch.getIsActive());
        existing.setIsChair(patch.getIsChair());
        existing.setNotes(patch.getNotes());
        return committeeMemberRepo.save(existing);
    }

    @Transactional
    public void deleteCommitteeMember(UUID id) {
        if (!committeeMemberRepo.existsById(id)) throw new ResourceNotFoundException("CommitteeMember", id);
        committeeMemberRepo.deleteById(id);
    }

    // ── CommitteeMeeting ─────────────────────────────────────────────────────

    public Page<CommitteeMeeting> findMeetingsByCommittee(UUID committeeId, Pageable pageable) {
        return meetingRepo.findByCommitteeId(committeeId, pageable);
    }

    public CommitteeMeeting findMeetingById(UUID id) {
        return meetingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommitteeMeeting", id));
    }

    @Transactional
    public CommitteeMeeting createMeeting(CommitteeMeeting meeting) {
        validateMeetingType(meeting.getMeetingType());
        validateMeetingStatus(meeting.getStatus());
        Committee committee = findCommitteeById(meeting.getCommittee().getId());
        meeting.setCommittee(committee);
        return meetingRepo.save(meeting);
    }

    @Transactional
    public CommitteeMeeting updateMeeting(UUID id, CommitteeMeeting patch) {
        validateMeetingType(patch.getMeetingType());
        validateMeetingStatus(patch.getStatus());
        CommitteeMeeting existing = findMeetingById(id);
        existing.setMeetingDatetime(patch.getMeetingDatetime());
        existing.setLocation(patch.getLocation());
        existing.setMeetingType(patch.getMeetingType());
        existing.setAgendaUrl(patch.getAgendaUrl());
        existing.setMinutesUrl(patch.getMinutesUrl());
        existing.setStatus(patch.getStatus());
        existing.setNotes(patch.getNotes());
        return meetingRepo.save(existing);
    }

    @Transactional
    public void deleteMeeting(UUID id) {
        if (!meetingRepo.existsById(id)) throw new ResourceNotFoundException("CommitteeMeeting", id);
        meetingRepo.deleteById(id);
    }

    // ── CommitteeResolution ───────────────────────────────────────────────────

    public Page<CommitteeResolution> findResolutionsByCommittee(UUID committeeId, Pageable pageable) {
        return resolutionRepo.findByCommitteeId(committeeId, pageable);
    }

    public CommitteeResolution findResolutionById(UUID id) {
        return resolutionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommitteeResolution", id));
    }

    @Transactional
    public CommitteeResolution createResolution(CommitteeResolution resolution) {
        validateResolutionStatus(resolution.getStatus());
        Committee committee = findCommitteeById(resolution.getCommittee().getId());
        CommitteeMeeting meeting = findMeetingById(resolution.getMeeting().getId());
        resolution.setCommittee(committee);
        resolution.setMeeting(meeting);
        return resolutionRepo.save(resolution);
    }

    @Transactional
    public CommitteeResolution updateResolution(UUID id, CommitteeResolution patch) {
        validateResolutionStatus(patch.getStatus());
        CommitteeResolution existing = findResolutionById(id);
        existing.setResolutionNumber(patch.getResolutionNumber());
        existing.setTitle(patch.getTitle());
        existing.setDescription(patch.getDescription());
        existing.setStatus(patch.getStatus());
        existing.setPassedDate(patch.getPassedDate());
        existing.setVotesFor(patch.getVotesFor());
        existing.setVotesAgainst(patch.getVotesAgainst());
        existing.setAbstentions(patch.getAbstentions());
        return resolutionRepo.save(existing);
    }

    @Transactional
    public void deleteResolution(UUID id) {
        if (!resolutionRepo.existsById(id)) throw new ResourceNotFoundException("CommitteeResolution", id);
        resolutionRepo.deleteById(id);
    }

    // ── MeetingMinutes ───────────────────────────────────────────────────────

    public Page<MeetingMinutes> findMeetingMinutesByOrg(UUID orgId, LocalDate fromDate,
            boolean openActionItemsOnly, Pageable pageable) {
        if (fromDate == null && !openActionItemsOnly) {
            return meetingMinutesRepo.findByOrgId(orgId, pageable);
        }
        return meetingMinutesRepo.search(orgId, fromDate, openActionItemsOnly, pageable);
    }

    public MeetingMinutes findMeetingMinutesById(UUID id) {
        return meetingMinutesRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MeetingMinutes", id));
    }

    @Transactional
    public MeetingMinutes createMeetingMinutes(MeetingMinutes minutes) {
        Organization org = orgService.findById(minutes.getOrg().getId());
        minutes.setOrg(org);
        return meetingMinutesRepo.save(minutes);
    }

    @Transactional
    public MeetingMinutes updateMeetingMinutes(UUID id, MeetingMinutes patch) {
        MeetingMinutes existing = findMeetingMinutesById(id);
        existing.setMeetingDate(patch.getMeetingDate());
        existing.setTitle(patch.getTitle());
        existing.setSummary(patch.getSummary());
        return meetingMinutesRepo.save(existing);
    }

    @Transactional
    public void deleteMeetingMinutes(UUID id) {
        if (!meetingMinutesRepo.existsById(id)) throw new ResourceNotFoundException("MeetingMinutes", id);
        meetingMinutesRepo.deleteById(id);
    }

    // ── ActionItem ────────────────────────────────────────────────────────────

    public List<ActionItem> findActionItemsByMeeting(UUID meetingMinutesId) {
        return actionItemRepo.findByMeetingMinutesIdOrderByCreatedAt(meetingMinutesId);
    }

    public ActionItem findActionItemById(UUID id) {
        return actionItemRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActionItem", id));
    }

    @Transactional
    public ActionItem createActionItem(ActionItem item) {
        validateActionItemPriority(item.getPriority());
        validateActionItemStatus(item.getStatus());
        MeetingMinutes minutes = findMeetingMinutesById(item.getMeetingMinutes().getId());
        item.setMeetingMinutes(minutes);
        if (item.getAssigneeTrustee() != null) {
            item.setAssigneeTrustee(findTrusteeById(item.getAssigneeTrustee().getId()));
        }
        return actionItemRepo.save(item);
    }

    @Transactional
    public ActionItem updateActionItem(UUID id, ActionItem patch) {
        validateActionItemPriority(patch.getPriority());
        validateActionItemStatus(patch.getStatus());
        ActionItem existing = findActionItemById(id);
        existing.setNote(patch.getNote());
        existing.setAssigneeTrustee(patch.getAssigneeTrustee() != null
                ? findTrusteeById(patch.getAssigneeTrustee().getId())
                : null);
        existing.setPriority(patch.getPriority());
        existing.setDueDate(patch.getDueDate());
        existing.setStatus(patch.getStatus());
        existing.setNotes(patch.getNotes());
        return actionItemRepo.save(existing);
    }

    @Transactional
    public void deleteActionItem(UUID id) {
        if (!actionItemRepo.existsById(id)) throw new ResourceNotFoundException("ActionItem", id);
        actionItemRepo.deleteById(id);
    }

    // ── Project ──────────────────────────────────────────────────────────────

    public Page<Project> findProjectsByOrg(UUID orgId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            validateProjectStatus(status);
            return projectRepo.findByOrgIdAndStatus(orgId, status, pageable);
        }
        return projectRepo.findByOrgId(orgId, pageable);
    }

    public Project findProjectById(UUID id) {
        return projectRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    @Transactional
    public Project createProject(Project project) {
        validateProjectStatus(project.getStatus());
        Organization org = orgService.findById(project.getOrg().getId());
        project.setOrg(org);
        project.setAssignee(resolveAssignee(project.getAssignee()));
        return projectRepo.save(project);
    }

    @Transactional
    public Project updateProject(UUID id, Project patch) {
        validateProjectStatus(patch.getStatus());
        Project existing = findProjectById(id);
        existing.setName(patch.getName());
        existing.setDescription(patch.getDescription());
        existing.setStatus(patch.getStatus());
        existing.setDueDate(patch.getDueDate());
        existing.setAssignee(resolveAssignee(patch.getAssignee()));
        return projectRepo.save(existing);
    }

    @Transactional
    public void deleteProject(UUID id) {
        if (!projectRepo.existsById(id)) throw new ResourceNotFoundException("Project", id);
        projectRepo.deleteById(id);
    }

    // ── ProjectTask ──────────────────────────────────────────────────────────

    public List<ProjectTask> findTasksByProject(UUID projectId) {
        return projectTaskRepo.findByProjectIdOrderByCreatedAt(projectId);
    }

    public ProjectTask findProjectTaskById(UUID id) {
        return projectTaskRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectTask", id));
    }

    @Transactional
    public ProjectTask createProjectTask(ProjectTask task) {
        validateProjectTaskStatus(task.getStatus());
        Project project = findProjectById(task.getProject().getId());
        task.setProject(project);
        task.setAssignee(resolveAssignee(task.getAssignee()));
        return projectTaskRepo.save(task);
    }

    @Transactional
    public ProjectTask updateProjectTask(UUID id, ProjectTask patch) {
        validateProjectTaskStatus(patch.getStatus());
        ProjectTask existing = findProjectTaskById(id);
        existing.setName(patch.getName());
        existing.setDescription(patch.getDescription());
        existing.setStatus(patch.getStatus());
        existing.setAssignee(resolveAssignee(patch.getAssignee()));
        return projectTaskRepo.save(existing);
    }

    @Transactional
    public void deleteProjectTask(UUID id) {
        if (!projectTaskRepo.existsById(id)) throw new ResourceNotFoundException("ProjectTask", id);
        projectTaskRepo.deleteById(id);
    }

    // ── ProjectComment / ProjectTaskComment ─────────────────────────────────
    // authorName always comes from the caller's authenticated session (resolved in
    // GovernanceController), never client input — see ProjectComment's class doc.

    public List<ProjectComment> findCommentsByProject(UUID projectId) {
        return projectCommentRepo.findByProjectIdOrderByCreatedAt(projectId);
    }

    @Transactional
    public ProjectComment createProjectComment(UUID projectId, String comment, String authorName) {
        Project project = findProjectById(projectId);
        return projectCommentRepo.save(ProjectComment.builder()
                .project(project)
                .comment(comment)
                .authorName(authorName)
                .build());
    }

    @Transactional
    public void deleteProjectComment(UUID id) {
        if (!projectCommentRepo.existsById(id)) throw new ResourceNotFoundException("ProjectComment", id);
        projectCommentRepo.deleteById(id);
    }

    public List<ProjectTaskComment> findCommentsByTask(UUID taskId) {
        return projectTaskCommentRepo.findByProjectTaskIdOrderByCreatedAt(taskId);
    }

    @Transactional
    public ProjectTaskComment createProjectTaskComment(UUID taskId, String comment, String authorName) {
        ProjectTask task = findProjectTaskById(taskId);
        return projectTaskCommentRepo.save(ProjectTaskComment.builder()
                .projectTask(task)
                .comment(comment)
                .authorName(authorName)
                .build());
    }

    @Transactional
    public void deleteProjectTaskComment(UUID id) {
        if (!projectTaskCommentRepo.existsById(id)) throw new ResourceNotFoundException("ProjectTaskComment", id);
        projectTaskCommentRepo.deleteById(id);
    }

    // ── ProjectChecklist / ProjectChecklistItem ─────────────────────────────
    // A sibling of ProjectTask under Project (V50; reworked off a 1:1-on-task design after real
    // usage showed "create a task first just to attach a checklist" was one step too many) — a
    // project can hold multiple independent checklists, each with its own title/items.

    public List<ProjectChecklist> findChecklistsByProject(UUID projectId) {
        return projectChecklistRepo.findByProjectIdOrderByCreatedAt(projectId);
    }

    public ProjectChecklist findChecklistById(UUID id) {
        return projectChecklistRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectChecklist", id));
    }

    @Transactional
    public ProjectChecklist createChecklist(UUID projectId, String title, LocalDate completionDate) {
        Project project = findProjectById(projectId);
        ProjectChecklist checklist = ProjectChecklist.builder()
                .project(project)
                .title(title)
                .completionDate(completionDate)
                .build();
        return projectChecklistRepo.save(checklist);
    }

    @Transactional
    public ProjectChecklist updateChecklist(UUID id, String title, LocalDate completionDate) {
        ProjectChecklist existing = findChecklistById(id);
        existing.setTitle(title);
        existing.setCompletionDate(completionDate);
        return projectChecklistRepo.save(existing);
    }

    @Transactional
    public void deleteChecklist(UUID id) {
        if (!projectChecklistRepo.existsById(id)) throw new ResourceNotFoundException("ProjectChecklist", id);
        projectChecklistRepo.deleteById(id);
    }

    public List<ProjectChecklistItem> findChecklistItems(UUID checklistId) {
        return projectChecklistItemRepo.findByChecklistIdOrderByCreatedAt(checklistId);
    }

    @Transactional
    public ProjectChecklistItem addChecklistItem(UUID checklistId, String text) {
        ProjectChecklist checklist = findChecklistById(checklistId);
        return projectChecklistItemRepo.save(ProjectChecklistItem.builder()
                .checklist(checklist)
                .text(text)
                .status("new")
                .build());
    }

    @Transactional
    public void deleteChecklistItem(UUID id) {
        if (!projectChecklistItemRepo.existsById(id)) throw new ResourceNotFoundException("ProjectChecklistItem", id);
        projectChecklistItemRepo.deleteById(id);
    }

    @Transactional
    public ProjectChecklistItem markChecklistItemDone(UUID id, String detail) {
        return setChecklistItemStatus(id, "done", detail);
    }

    @Transactional
    public ProjectChecklistItem markChecklistItemSkipped(UUID id, String detail) {
        return setChecklistItemStatus(id, "skipped", detail);
    }

    /** The only transition available once an item is done or skipped — always returns it to
     *  "new", re-enabling the Done/Skip actions, per the checklist's 3-state design. Always clears
     *  {@code detail} too (not just a frontend-side clear) — a reopened item is "new" again, and a
     *  stale note from the previous Done/Skip would be misleading. */
    @Transactional
    public ProjectChecklistItem reopenChecklistItem(UUID id) {
        return setChecklistItemStatus(id, "new", null);
    }

    private ProjectChecklistItem setChecklistItemStatus(UUID id, String status, String detail) {
        ProjectChecklistItem item = projectChecklistItemRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectChecklistItem", id));
        item.setStatus(status);
        item.setDetail(detail);
        return projectChecklistItemRepo.save(item);
    }

    /** Assignee is optional on both Project and ProjectTask — resolves the full Person only when
     *  one was actually supplied, rather than forcing every caller to null-check first. */
    private Person resolveAssignee(Person assignee) {
        return assignee != null ? personService.findById(assignee.getId()) : null;
    }

    // ── Validators ────────────────────────────────────────────────────────────

    private void validateMeetingType(String type) {
        if (type != null && !MEETING_TYPES.contains(type))
            throw new IllegalArgumentException("Invalid meeting type: " + type + ". Allowed: " + MEETING_TYPES);
    }

    private void validateMeetingStatus(String status) {
        if (status != null && !MEETING_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid meeting status: " + status + ". Allowed: " + MEETING_STATUSES);
    }

    private void validateResolutionStatus(String status) {
        if (status != null && !RESOLUTION_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid resolution status: " + status + ". Allowed: " + RESOLUTION_STATUSES);
    }

    private void validateActionItemPriority(String priority) {
        if (priority != null && !ACTION_ITEM_PRIORITIES.contains(priority))
            throw new IllegalArgumentException("Invalid action item priority: " + priority + ". Allowed: " + ACTION_ITEM_PRIORITIES);
    }

    private void validateActionItemStatus(String status) {
        if (status != null && !ACTION_ITEM_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid action item status: " + status + ". Allowed: " + ACTION_ITEM_STATUSES);
    }

    private void validateProjectStatus(String status) {
        if (status != null && !PROJECT_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid project status: " + status + ". Allowed: " + PROJECT_STATUSES);
    }

    private void validateProjectTaskStatus(String status) {
        if (status != null && !PROJECT_TASK_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid project task status: " + status + ". Allowed: " + PROJECT_TASK_STATUSES);
    }
}
