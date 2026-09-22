package com.svivanrilski.svirerp.governance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.person.Person;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A task within a {@link Project}, with its own {@link ProjectTaskComment} thread. */
@Entity
@Table(name = "project_task")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Allowed values (enforced by DB CHECK): todo, in_progress, blocked, done. */
    @Column(nullable = false, length = 20)
    private String status = "todo";

    // Optional — a task can be logged before anyone's assigned to it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_person_id")
    private Person assignee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
