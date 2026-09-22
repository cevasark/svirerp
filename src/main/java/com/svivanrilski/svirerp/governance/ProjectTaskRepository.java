package com.svivanrilski.svirerp.governance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the controller layer
    // serializes the response — "project.assignee" (the *project's* own assignee, not the task's)
    // must be listed explicitly too, or Jackson hits a LazyInitializationException walking the
    // nested Project object whenever that project happens to have an assignee set.
    // Deliberately unpaginated — matches ActionItemRepository#findByMeetingMinutesId: a project's
    // tasks are meant to be seen as one whole list on the project's detail page, not paged through.
    @EntityGraph(attributePaths = {"project", "project.org", "project.assignee", "assignee"})
    List<ProjectTask> findByProjectIdOrderByCreatedAt(UUID projectId);

    @EntityGraph(attributePaths = {"project", "project.org", "project.assignee", "assignee"})
    @Override
    Optional<ProjectTask> findById(UUID id);
}
