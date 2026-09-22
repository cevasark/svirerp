package com.svivanrilski.svirerp.governance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectChecklistRepository extends JpaRepository<ProjectChecklist, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before Jackson serializes the
    // response, so every lazy path walked by the response body (the project's own org/assignee)
    // must be listed explicitly — same idiom and depth as ProjectTaskRepository.
    // Deliberately unpaginated — matches ProjectTaskRepository#findByProjectIdOrderByCreatedAt: a
    // project's checklists are meant to be seen as one whole list, not paged through.
    @EntityGraph(attributePaths = {"project", "project.org", "project.assignee"})
    List<ProjectChecklist> findByProjectIdOrderByCreatedAt(UUID projectId);

    @EntityGraph(attributePaths = {"project", "project.org", "project.assignee"})
    @Override
    Optional<ProjectChecklist> findById(UUID id);
}
