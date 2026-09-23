package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.Fund;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_campaign", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_campaign_external_id", columnNames = "zeffy_campaign_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "zeffy_campaign_id", nullable = false, length = 100)
    private String zeffyCampaignId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "campaign_type", nullable = false, length = 50)
    private String campaignType;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String locale;

    @Column(name = "public_url", length = 1000)
    private String publicUrl;

    @Column(length = 10)
    private String currency;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean isArchived = false;

    @Column(name = "zeffy_created_at")
    private OffsetDateTime zeffyCreatedAt;

    @Column(name = "zeffy_updated_at")
    private OffsetDateTime zeffyUpdatedAt;

    @Column(name = "zeffy_deleted_at")
    private OffsetDateTime zeffyDeletedAt;

    @Column(name = "last_synced_at", nullable = false)
    private OffsetDateTime lastSyncedAt;

    @Column(name = "mapping_confirmed", nullable = false)
    @Builder.Default
    private Boolean mappingConfirmed = false;

    @Column(name = "processing_action", length = 10)
    private String processingAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_account_id")
    private Account categoryAccount;

    @Column(name = "grants_membership_credit", nullable = false)
    @Builder.Default
    private Boolean grantsMembershipCredit = false;

    @Column(name = "mapping_note", length = 500)
    private String mappingNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (lastSyncedAt == null) lastSyncedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
