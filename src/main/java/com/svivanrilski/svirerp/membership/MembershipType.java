package com.svivanrilski.svirerp.membership;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Membership tiers (e.g. General, Sustaining, Honorary) with associated fee and duration. */
@Entity
@Table(
    name = "membership_type",
    uniqueConstraints = @UniqueConstraint(name = "uq_membership_type_name", columnNames = "name")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "annual_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal annualFee = BigDecimal.ZERO;

    @Min(1)
    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths = 12;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Whether members holding this type are eligible to vote (e.g. in governance resolutions). */
    @Column(name = "can_vote", nullable = false)
    private Boolean canVote = false;

    @Column(columnDefinition = "TEXT")
    private String benefits;

    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
