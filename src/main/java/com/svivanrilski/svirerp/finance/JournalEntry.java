package com.svivanrilski.svirerp.finance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Header record for every double-entry transaction.
 * The DB enforces total_debit = total_credit; the service validates this before posting.
 *
 * Two separate FKs reference person(id) via different column names:
 *   - created_by: the person who recorded the entry
 *   - approved_by: the person who approved/posted it (nullable until posted)
 */
@Entity
@Table(name = "journal_entry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Person createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Person approvedBy;

    @Column(name = "entry_number", unique = true, length = 50)
    private String entryNumber;

    @NotNull
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(length = 150)
    private String reference;

    /** Allowed values (DB CHECK): general, adjusting, closing, reversing, opening. */
    @Column(name = "entry_type", nullable = false, length = 50)
    private String entryType = "general";

    /** Allowed values (DB CHECK): draft, posted, void. */
    @Column(nullable = false, length = 30)
    private String status = "draft";

    @Column(name = "total_debit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    // ── Transaction tags (set by FinanceService#recordIncome/#recordExpense) ───────────────────
    // Denormalized so the transaction list can show category/fund/vendor/payer without joining
    // journal_line; the ledger truth remains the JournalLine rows below.

    /** Allowed values (DB CHECK): cash, check, zeffy, bank_transfer, card, other. */
    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "check_number", length = 20)
    private String checkNumber;

    /** The donor/payer for an income entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private Person payer;

    /** The payee for an expense entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    /** Set when this entry is a payment toward a pre-paid church service. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id")
    private ServiceRequest serviceRequest;

    /** The revenue/expense account on the non-cash side of a simple 2-line entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_account_id")
    private Account categoryAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
