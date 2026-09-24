package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_refund", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_refund_external_id", columnNames = "zeffy_refund_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyRefund {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(updatable = false, nullable = false)
    private UUID id;
    @Column(name = "zeffy_refund_id", nullable = false, unique = true, length = 100)
    private String zeffyRefundId;
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
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "refund_created_at", nullable = false)
    private OffsetDateTime refundCreatedAt;
    @Column(name = "correction_status", nullable = false, length = 30)
    private String correctionStatus;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_journal_entry_id")
    private JournalEntry correctionJournalEntry;
    @Column(name = "corrected_amount", precision = 15, scale = 2)
    private BigDecimal correctedAmount;
    @Column(name = "correction_summary", length = 1000)
    private String correctionSummary;
    @Column(name = "corrected_at")
    private OffsetDateTime correctedAt;
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
