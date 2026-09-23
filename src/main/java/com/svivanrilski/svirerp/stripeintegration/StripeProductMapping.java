package com.svivanrilski.svirerp.stripeintegration;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.Fund;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Routes a Stripe Price to what a completed payment against it means — membership dues, a paid
 * church service, an event ticket, or general income — plus where it posts (Fund/Account). One row
 * per (org, stripePriceId), persisted so a recurring WordPress/mobile-app product doesn't need
 * remapping on every payment. Same role as ZeffyCampaignMapping, generalized to four purposes
 * instead of one campaign-to-fund link.
 */
@Entity
@Table(
    name = "stripe_product_mapping",
    uniqueConstraints = @UniqueConstraint(name = "uq_stripe_product_mapping_price",
        columnNames = "stripe_price_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeProductMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "stripe_price_id", nullable = false, length = 100)
    private String stripePriceId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    /** Allowed values (DB CHECK): membership_dues, service_request, event_ticket, general_income. */
    @NotNull
    @Column(nullable = false, length = 30)
    private String purpose;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_account_id")
    private Account categoryAccount;

    /** Only meaningful when purpose = service_request. Allowed values (DB CHECK): wedding,
     *  baptism, funeral, memorial, blessing, other — same set as ServiceRequest#serviceType. */
    @Column(name = "service_type", length = 50)
    private String serviceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
