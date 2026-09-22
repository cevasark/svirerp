package com.svivanrilski.svirerp.governance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A checklist attached to a {@link Project} — a sibling of {@link ProjectTask}, not nested under
 *  one (V50; originally 1:1 on a task in V49, reworked after real usage showed "create a task
 *  first just to attach a checklist" was one step too many). A project can hold multiple
 *  independent checklists, each with its own {@code title} and a user-set target
 *  {@code completionDate} (not auto-derived from item state); the actual line items live in
 *  {@link ProjectChecklistItem}. */
@Entity
@Table(name = "project_checklist")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectChecklist {

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
    private String title;

    @Column(name = "completion_date")
    private LocalDate completionDate;

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
