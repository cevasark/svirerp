package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.person.Person;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_contact", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_contact_external_id", columnNames = "zeffy_contact_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "zeffy_contact_id", nullable = false, unique = true, length = 100)
    private String zeffyContactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_webhook_event_id")
    private ZeffyWebhookEvent latestWebhookEvent;

    @Column(length = 255) private String email;
    @Column(name = "first_name", length = 100) private String firstName;
    @Column(name = "last_name", length = 100) private String lastName;
    @Column(name = "phone_number", length = 30) private String phoneNumber;
    @Column(name = "address_line1", length = 255) private String addressLine1;
    @Column(length = 100) private String city;
    @Column(length = 100) private String state;
    @Column(name = "postal_code", length = 20) private String postalCode;
    @Column(length = 10) private String country;
    @Column(name = "donor_type", length = 50) private String donorType;
    @Column(name = "total_contribution", precision = 15, scale = 2) private BigDecimal totalContribution;
    @Column(length = 10) private String currency;
    @Column(name = "donation_count") private Integer donationCount;
    @Column(name = "first_donation_at") private OffsetDateTime firstDonationAt;
    @Column(name = "last_donation_at") private OffsetDateTime lastDonationAt;
    @Column(name = "zeffy_created_at") private OffsetDateTime zeffyCreatedAt;
    @Column(name = "zeffy_updated_at") private OffsetDateTime zeffyUpdatedAt;
    @Column(name = "latest_payload_sha256", length = 64) private String latestPayloadSha256;
    @Column(name = "processing_status", nullable = false, length = 20) private String processingStatus;
    @Column(name = "outcome_reason", length = 1000) private String outcomeReason;
    @Column(name = "first_seen_source", nullable = false, length = 20) private String firstSeenSource;
    @Column(name = "first_seen_at", nullable = false, updatable = false) private OffsetDateTime firstSeenAt;
    @Column(name = "last_event_at") private OffsetDateTime lastEventAt;
    @Column(name = "last_synced_at") private OffsetDateTime lastSyncedAt;
    @Column(name = "deleted_at") private OffsetDateTime deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (firstSeenAt == null) firstSeenAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
