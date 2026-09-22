package com.svivanrilski.svirerp.zeffyimport;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.organization.Organization;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persists which Fund a Zeffy "Campaign Title" should post income to, so recurring campaigns
 * don't need to be remapped on every import. One row per (org, campaignTitle).
 */
@Entity
@Table(
    name = "zeffy_campaign_mapping",
    uniqueConstraints = @UniqueConstraint(name = "uq_zeffy_campaign_mapping_org_title",
        columnNames = {"org_id", "campaign_title"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyCampaignMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @NotBlank
    @Column(name = "campaign_title", nullable = false, length = 255)
    private String campaignTitle;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    /** Overrides the Ticket-category-skips-membership default (see ZeffyImportRowApplier#applyRow)
     *  — Zeffy implements fixed-price membership registration as a Ticket-type product, not
     *  Donation, so a campaign that's actually collecting dues needs this explicitly set. */
    @Column(name = "is_membership_payment", nullable = false)
    @Builder.Default
    private Boolean isMembershipPayment = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
