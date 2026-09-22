package com.svivanrilski.svirerp.governance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One line item within a {@link ProjectChecklist}. Deliberately 3-state, not a boolean checkbox:
 *  "done" and "skipped" are both terminal/resolved outcomes reachable from "new", and either one
 *  can be walked back to "new" (re-enabling Done/Skip) via the single "Re-open" action — see
 *  GovernanceService#reopenChecklistItem. {@code detail} is an optional one-line note captured at
 *  the moment an item is marked Done/Skipped (e.g. why it was skipped); Re-open always clears it. */
@Entity
@Table(name = "project_checklist_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private ProjectChecklist checklist;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String text;

    @Column(length = 500)
    private String detail;

    /** Allowed values (enforced by DB CHECK): new, done, skipped. */
    @Column(nullable = false, length = 20)
    private String status = "new";

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
