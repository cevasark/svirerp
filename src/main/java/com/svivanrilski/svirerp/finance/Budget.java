package com.svivanrilski.svirerp.finance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Annual budget allocations per account and fund. */
@Entity
@Table(
    name = "budget",
    uniqueConstraints = @UniqueConstraint(name = "uq_budget_account_fund_year_period",
        columnNames = {"account_id", "fund_id", "fiscal_year", "period"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** Nullable: budget lines not tied to a specific fund apply to the general ledger. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;

    @NotNull
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "budgeted_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetedAmount = BigDecimal.ZERO;

    /** Allowed values (DB CHECK): annual, q1, q2, q3, q4, monthly. */
    @Column(nullable = false, length = 20)
    private String period = "annual";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_approved", nullable = false)
    private Boolean isApproved = false;

    @Column(name = "approved_date")
    private LocalDate approvedDate;
}
