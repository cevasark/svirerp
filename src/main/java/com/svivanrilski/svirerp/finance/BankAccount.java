package com.svivanrilski.svirerp.finance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Organisation bank account linked to a GL account for reconciliation. */
@Entity
@Table(name = "bank_account")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The corresponding GL account in the chart of accounts (must be an asset account). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id", nullable = false)
    private Account glAccount;

    @NotBlank
    @Column(name = "institution_name", nullable = false, length = 150)
    private String institutionName;

    @NotBlank
    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "account_number_masked", length = 20)
    private String accountNumberMasked;

    @Column(name = "routing_number_masked", length = 20)
    private String routingNumberMasked;

    /** Allowed values (DB CHECK): checking, savings, money_market, cd, investment. */
    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType = "checking";

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "opened_date")
    private LocalDate openedDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
