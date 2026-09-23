package com.svivanrilski.svirerp.stripeintegration;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.finance.ServiceRequest;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.person.Person;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per Stripe webhook delivery, inserted before any dispatch logic runs — the unique
 * constraint on {@link #stripeEventId} is the idempotency guard (Stripe redelivers on any non-2xx
 * response, and can occasionally duplicate a delivery outright). Doubles as the permanent audit
 * trail linking back to whatever it produced.
 */
@Entity
@Table(name = "stripe_webhook_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "stripe_event_id", nullable = false, unique = true, length = 255)
    private String stripeEventId;

    @NotBlank
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "stripe_price_id", length = 100)
    private String stripePriceId;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    /** Stripe's processing fee (from the underlying charge's BalanceTransaction), when resolvable —
     *  null if it couldn't be looked up. Drives the fee-expense split in {@link StripeWebhookEventApplier}. */
    @Column(precision = 15, scale = 2)
    private BigDecimal fee;

    /** Captured from the Stripe event at receipt time (not re-derived from the payer's Person
     *  record) so a later "reprocess" action can re-run {@link StripeWebhookEventApplier#applyEvent}
     *  without re-parsing the raw Stripe payload. */
    @Column(length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    /** Allowed values (DB CHECK): received, processed, needs_mapping, error, ignored. */
    @Column(nullable = false, length = 20)
    private String status = "received";

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    // --- Stamped once the event is successfully applied (audit trail) ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_payment_id")
    private MemberPayment memberPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id")
    private ServiceRequest serviceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @PrePersist
    private void prePersist() {
        if (receivedAt == null) receivedAt = OffsetDateTime.now();
    }
}
