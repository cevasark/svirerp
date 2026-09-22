package com.svivanrilski.svirerp.governance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.person.Person;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Lightweight governance task-tracking project — distinct from Finance's Fund/"project"
 *  restricted-fund-accounting concept (see FinanceService class docs). Owns a list of
 *  {@link ProjectTask} and its own {@link ProjectComment} thread. */
@Entity
@Table(name = "project")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Allowed values (enforced by DB CHECK): planning, in_progress, on_hold, completed, cancelled. */
    @Column(nullable = false, length = 20)
    private String status = "planning";

    @Column(name = "due_date")
    private LocalDate dueDate;

    // Optional — a project can be logged before anyone's assigned to it.
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
