package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_sync_payment_result", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_sync_payment_result",
                columnNames = {"sync_run_id", "zeffy_payment_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffySyncPaymentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_run_id", nullable = false)
    private ZeffySyncRun syncRun;

    @Column(name = "zeffy_payment_id", nullable = false, length = 100)
    private String zeffyPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zeffy_payment_record_id")
    private ZeffyPayment zeffyPayment;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(length = 1000)
    private String detail;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (observedAt == null) observedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
