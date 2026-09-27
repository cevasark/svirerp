package com.svivanrilski.svirerp.membership;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Formula;
import com.svivanrilski.svirerp.person.Person;

import java.math.BigDecimal;
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

    /** Lifetime net payments for the person, independent of membership tier or campaign.
     *  A database expression keeps display and future server-side sorting on the same total. */
    @Formula("coalesce((select paid.total_paid from person_payment_total paid where paid.person_id = person_id), 0)")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Setter(AccessLevel.NONE)
    private BigDecimal totalPaid;

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
