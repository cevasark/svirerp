package com.svivanrilski.svirerp.governance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Standing and ad hoc committees with lifecycle tracking. */
@Entity
@Table(
    name = "committee",
    uniqueConstraints = @UniqueConstraint(name = "uq_committee_name", columnNames = "name")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Committee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "committee_type", length = 100)
    private String committeeType;

    @Column(name = "is_standing", nullable = false)
    private Boolean isStanding = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "formed_date")
    private LocalDate formedDate;

    @Column(name = "dissolved_date")
    private LocalDate dissolvedDate;

    @Column(name = "meeting_schedule", length = 255)
    private String meetingSchedule;

    @Column(columnDefinition = "TEXT")
    private String mandate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
