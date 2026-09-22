package com.svivanrilski.svirerp.zeffyimport;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per line of an uploaded Zeffy CSV. Persisted at preview time (before anything else is
 * written) with a computed {@link #outcome}; commit only applies rows still in {@code ready}.
 * This table is both the preview/commit staging area and the permanent import audit trail.
 */
@Entity
@Table(name = "zeffy_import_row")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ZeffyImportBatch batch;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    // Column is "csv_row_number", not "row_number" — ROW_NUMBER is a reserved MariaDB 10.2+ keyword.
    @Column(name = "csv_row_number", nullable = false)
    private Integer rowNumber;

    // --- Raw/parsed Zeffy Transactions-export columns ---

    /** Zeffy's own transaction Id (e.g. "txn_..."), from the "Id" column — always present, and
     *  the dedupe key (see ZeffyImportService#computeOutcome). */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    /** Usually "Donation" or "Ticket", from the "Category" column — drives purpose routing in
     *  ZeffyImportRowApplier (Donation earns membership tier credit, Ticket doesn't). Zeffy can also
     *  send other values here (e.g. "Dispute pending" for a chargeback, with a negative Amount) —
     *  ZeffyImportService#computeOutcome flags anything outside {Donation, Ticket} as outcome='error'
     *  for manual review rather than guessing how to post it. Deliberately not DB-CHECK-constrained
     *  (see V46) so an unexpected value can still be saved and made visible on the error row. */
    @Column(length = 30)
    private String category;

    /** "Eligible amount" — the tax-deductible portion; blank for non-donation rows (e.g. Ticket).
     *  Informational only, not read by any downstream logic. */
    @Column(name = "eligible_amount", precision = 10, scale = 2)
    private BigDecimal eligibleAmount;

    /** From "Creation Date (America/Chicago)" — the date the transaction happened. */
    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    /** From "Available on (America/Chicago)" — purely informational, when Zeffy pays this out to
     *  the bank; never read by any membership/tier/finance logic. Parsed leniently (see
     *  ZeffyImportService#parseDateLenient) since it can plausibly show a placeholder for a
     *  transaction that hasn't been paid out yet. */
    @Column(name = "available_date")
    private LocalDate availableDate;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(name = "campaign_title", length = 255)
    private String campaignTitle;

    // --- Computed during preview ---

    /** Zeffy's transaction Id — always present, so no composite fallback key is needed. */
    @Column(name = "dedupe_key", length = 500)
    private String dedupeKey;

    /**
     * Allowed values (DB CHECK): pending_preview, ready, duplicate, skipped_status,
     * unmapped_campaign, error, committed.
     */
    @Column(nullable = false, length = 30)
    private String outcome = "pending_preview";

    @Column(name = "outcome_detail", length = 500)
    private String outcomeDetail;

    @Column(name = "is_new_person", nullable = false)
    private Boolean isNewPerson = false;

    @Column(name = "is_new_member", nullable = false)
    private Boolean isNewMember = false;

    // --- Stamped on commit ---

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;
}
