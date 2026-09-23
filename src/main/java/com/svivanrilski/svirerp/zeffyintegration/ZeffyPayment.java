package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.person.Person;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zeffy_payment", uniqueConstraints =
        @UniqueConstraint(name = "uq_zeffy_payment_external_id", columnNames = "zeffy_payment_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "zeffy_payment_id", nullable = false, unique = true, length = 100)
    private String zeffyPaymentId;

    @Column(length = 30)
    private String status;

    @Column(name = "refund_status", length = 30)
    private String refundStatus;

    @Column(name = "dispute_status", length = 50)
    private String disputeStatus;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "eligible_amount", precision = 15, scale = 2)
    private BigDecimal eligibleAmount;

    @Column(length = 10)
    private String currency;

    @Column(name = "payment_type", length = 30)
    private String paymentType;

    @Column(name = "payment_created_at")
    private OffsetDateTime paymentCreatedAt;

    @Column(name = "campaign_id", length = 100)
    private String campaignId;

    @Column(name = "campaign_title", length = 255)
    private String campaignTitle;

    @Column(name = "mapping_action", length = 10)
    private String mappingAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapped_fund_id")
    private Fund mappedFund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapped_account_id")
    private Account mappedAccount;

    @Column(name = "membership_credit", nullable = false)
    @Builder.Default
    private Boolean membershipCredit = false;

    @Column(name = "contact_id", length = 100)
    private String contactId;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(name = "buyer_first_name", length = 100)
    private String buyerFirstName;

    @Column(name = "buyer_last_name", length = 100)
    private String buyerLastName;

    @Column(name = "latest_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String latestPayload;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus;

    @Column(name = "outcome_reason", length = 1000)
    private String outcomeReason;

    @Column(name = "first_seen_source", nullable = false, length = 20)
    private String firstSeenSource;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_event_at", nullable = false)
    private OffsetDateTime lastEventAt;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

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
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (firstSeenAt == null) firstSeenAt = now;
        if (lastEventAt == null) lastEventAt = firstSeenAt;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
