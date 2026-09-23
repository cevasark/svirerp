package com.svivanrilski.svirerp.finance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chart of accounts with parent-child hierarchy for double-entry bookkeeping.
 * Root accounts have no parent.  The self-referential FK means child accounts
 * reference the same table — Jackson @JsonIgnore on childAccounts prevents
 * infinite recursion during JSON serialisation.
 */
@Entity
@Table(
    name = "account",
    uniqueConstraints = @UniqueConstraint(name = "uq_account_number", columnNames = "account_number")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Nullable for root-level accounts; not null for sub-accounts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private Account parentAccount;

    /** Excluded from JSON output to prevent serialisation cycles via parentAccount. */
    @JsonIgnore
    @OneToMany(mappedBy = "parentAccount", fetch = FetchType.LAZY)
    private List<Account> childAccounts = new ArrayList<>();

    @NotBlank
    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @NotBlank
    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    /** Allowed values (DB CHECK): asset, liability, equity, revenue, expense. */
    @NotBlank
    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType;

    @Column(name = "account_subtype", length = 100)
    private String accountSubtype;

    /** Allowed values (DB CHECK): debit, credit. */
    @Column(name = "normal_balance", nullable = false, length = 6)
    private String normalBalance = "debit";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** System accounts are created by seed data and should not be deleted via the API. */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
