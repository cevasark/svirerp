package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_sync_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffySyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sync_type", nullable = false, length = 20)
    private String syncType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(name = "initiated_by", length = 255)
    private String initiatedBy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "requested_from")
    private OffsetDateTime requestedFrom;

    @Column(name = "requested_to")
    private OffsetDateTime requestedTo;

    @Column(name = "starting_cursor", length = 500)
    private String startingCursor;

    @Column(name = "ending_cursor", length = 500)
    private String endingCursor;

    @Column(name = "fetched_count", nullable = false)
    @Builder.Default
    private Integer fetchedCount = 0;

    @Column(name = "inserted_count", nullable = false)
    @Builder.Default
    private Integer insertedCount = 0;

    @Column(name = "updated_count", nullable = false)
    @Builder.Default
    private Integer updatedCount = 0;

    @Column(name = "ignored_count", nullable = false)
    @Builder.Default
    private Integer ignoredCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (startedAt == null) startedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
