package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_webhook_event", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_webhook_event_external_id", columnNames = "zeffy_event_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "zeffy_event_id", nullable = false, unique = true, length = 100)
    private String zeffyEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "zeffy_resource_id", length = 100)
    private String zeffyResourceId;

    @Column(name = "dispatched_at", nullable = false)
    private OffsetDateTime dispatchedAt;

    @Column(name = "signature_timestamp", nullable = false)
    private OffsetDateTime signatureTimestamp;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "delivery_count", nullable = false)
    @Builder.Default
    private Integer deliveryCount = 1;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "last_received_at", nullable = false)
    private OffsetDateTime lastReceivedAt;

    @Column(name = "processing_attempt_count", nullable = false)
    @Builder.Default
    private Integer processingAttemptCount = 0;

    @Column(name = "last_attempted_at")
    private OffsetDateTime lastAttemptedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "processing_summary", length = 1000)
    private String processingSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zeffy_payment_record_id")
    private ZeffyPayment zeffyPayment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (receivedAt == null) receivedAt = now;
        if (lastReceivedAt == null) lastReceivedAt = receivedAt;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
