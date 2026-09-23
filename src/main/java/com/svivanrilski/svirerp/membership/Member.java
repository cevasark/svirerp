package com.svivanrilski.svirerp.membership;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.person.Person;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Links a Person to the Organisation under a specific MembershipType.
 * A person may hold at most one active membership per organisation at a time —
 * enforce this in the service layer since the DB allows historical rows.
 */
@Entity
@Table(name = "member")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_type_id", nullable = false)
    private MembershipType membershipType;

    @Column(name = "member_number", unique = true, length = 50)
    private String memberNumber;

    @NotNull
    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * Allowed values (enforced by DB CHECK): active, inactive, suspended, expired, pending.
     * Stored as a plain String to match the VARCHAR column; validated in service before save.
     */
    @Column(nullable = false, length = 30)
    private String status = "active";

    @Column(name = "email_opt_in", nullable = false)
    private Boolean emailOptIn = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
