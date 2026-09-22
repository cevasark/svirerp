package com.svivanrilski.svirerp.governance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectChecklistItemRepository extends JpaRepository<ProjectChecklistItem, UUID> {

    // Same EntityGraph gotcha as ProjectTaskCommentRepository — spring.jpa.open-in-view=false
    // closes the Hibernate session before Jackson serializes the response, so every lazy path
    // walked by the response body (checklist -> its project -> that project's own org/assignee)
    // must be listed explicitly. This chain is 3 hops deep from the item's own root, same depth as
    // ProjectTaskCommentRepository's proven-working "projectTask.project.org" — the declarative
    // dotted-string form is reliable at this depth (unlike the 4-hop chain this feature had before
    // moving the checklist off ProjectTask, which needed an explicit JOIN FETCH @Query instead).
    @EntityGraph(attributePaths = {"checklist", "checklist.project", "checklist.project.org", "checklist.project.assignee"})
    List<ProjectChecklistItem> findByChecklistIdOrderByCreatedAt(UUID checklistId);

    // Needed so the Done/Skip/Re-open status-transition methods in GovernanceService can return
    // the updated item without a LazyInitializationException once the transaction (and its
    // Hibernate session) has closed by the time the controller layer serializes the response.
    @EntityGraph(attributePaths = {"checklist", "checklist.project", "checklist.project.org", "checklist.project.assignee"})
    @Override
    Optional<ProjectChecklistItem> findById(UUID id);
}
