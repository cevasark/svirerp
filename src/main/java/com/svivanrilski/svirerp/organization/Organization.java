package com.svivanrilski.svirerp.organization;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Installation-wide organization profile. The database permits at most one row. Domain records
 * implicitly belong to this organization and do not carry organization keys.
 */
@Entity
@Table(name = "organization")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "tax_id_ein", unique = true, length = 20)
    private String taxIdEin;

    @Column(name = "nonprofit_type", length = 50)
    private String nonprofitType;

    @Column(name = "mission_statement", columnDefinition = "TEXT")
    private String missionStatement;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String zip;

    @Column(nullable = false, length = 100)
    private String country = "US";

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String website;

    @Column(name = "founded_date")
    private LocalDate foundedDate;

    @Column(name = "fiscal_year_start")
    private LocalDate fiscalYearStart;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

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
