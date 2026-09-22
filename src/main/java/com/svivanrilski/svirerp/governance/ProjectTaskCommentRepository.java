package com.svivanrilski.svirerp.governance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectTaskCommentRepository extends JpaRepository<ProjectTaskComment, UUID> {

    // See ProjectCommentRepository for why the EntityGraph is required — "projectTask.project.assignee"
    // (the *project's* own assignee) must be listed explicitly too, same gotcha as
    // ProjectTaskRepository. Deliberately unpaginated.
    @EntityGraph(attributePaths = {
        "projectTask", "projectTask.project", "projectTask.project.org",
        "projectTask.project.assignee", "projectTask.assignee",
    })
    List<ProjectTaskComment> findByProjectTaskIdOrderByCreatedAt(UUID projectTaskId);
}
