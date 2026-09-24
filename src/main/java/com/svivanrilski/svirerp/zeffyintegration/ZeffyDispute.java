package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_dispute", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_dispute_external_id", columnNames = "zeffy_dispute_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyDispute {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(updatable = false, nullable = false)
    private UUID id;
    @Column(name = "zeffy_dispute_id", nullable = false, unique = true, length = 100)
    private String zeffyDisputeId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zeffy_payment_record_id", nullable = false)
    private ZeffyPayment zeffyPayment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_webhook_event_id")
    private ZeffyWebhookEvent latestWebhookEvent;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 10)
    private String currency;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(length = 255)
    private String reason;
    @Column(name = "dispute_created_at", nullable = false)
    private OffsetDateTime disputeCreatedAt;
    @Column(name = "correction_status", nullable = false, length = 30)
    private String correctionStatus;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_journal_entry_id")
    private JournalEntry correctionJournalEntry;
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private OffsetDateTime firstSeenAt;
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (firstSeenAt == null) firstSeenAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
    @PreUpdate private void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
