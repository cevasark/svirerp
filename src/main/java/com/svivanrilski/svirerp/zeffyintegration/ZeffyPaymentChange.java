package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_payment_change", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_payment_change_event", columnNames = "webhook_event_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyPaymentChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zeffy_payment_record_id", nullable = false)
    private ZeffyPayment zeffyPayment;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_event_id", nullable = false, unique = true)
    private ZeffyWebhookEvent webhookEvent;

    @Column(name = "change_kind", nullable = false, length = 30)
    private String changeKind;

    @Column(name = "changed_fields", length = 1000)
    private String changedFields;

    @Column(name = "previous_payload_sha256", length = 64)
    private String previousPayloadSha256;

    @Column(name = "current_payload_sha256", length = 64)
    private String currentPayloadSha256;

    @Column(name = "payment_snapshot", nullable = false, columnDefinition = "LONGTEXT")
    private String paymentSnapshot;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (observedAt == null) observedAt = OffsetDateTime.now();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
