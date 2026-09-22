package com.svivanrilski.svirerp.governance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectCommentRepository extends JpaRepository<ProjectComment, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the controller layer
    // serializes the response, so these LAZY associations must be eagerly fetched here or Jackson
    // hits a LazyInitializationException walking project -> org / assignee.
    // Deliberately unpaginated — a project's comment thread is meant to be read in full, oldest
    // first, same as ActionItem's meeting-scoped list.
    @EntityGraph(attributePaths = {"project", "project.org", "project.assignee"})
    List<ProjectComment> findByProjectIdOrderByCreatedAt(UUID projectId);
}
