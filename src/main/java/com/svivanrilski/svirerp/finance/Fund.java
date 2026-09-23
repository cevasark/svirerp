package com.svivanrilski.svirerp.finance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fund accounting: restricted vs unrestricted funds for nonprofit compliance.
 * Journal lines reference a fund so revenue/expense can be tracked per fund.
 */
@Entity
@Table(
    name = "fund",
    uniqueConstraints = @UniqueConstraint(name = "uq_fund_code", columnNames = "fund_code")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "fund_name", nullable = false, length = 150)
    private String fundName;

    @NotBlank
    @Column(name = "fund_code", nullable = false, length = 30)
    private String fundCode;

    /** Allowed values (DB CHECK): unrestricted, temporarily_restricted, permanently_restricted. */
    @Column(name = "fund_type", nullable = false, length = 50)
    private String fundType = "unrestricted";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_restricted", nullable = false)
    private Boolean isRestricted = false;

    @Column(name = "restriction_purpose", columnDefinition = "TEXT")
    private String restrictionPurpose;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;
}
