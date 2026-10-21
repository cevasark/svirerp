package com.svivanrilski.svirerp.roomrental;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.governance.Trustee;
import com.svivanrilski.svirerp.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Church facility room rental agreement.
 * Tracks rental requests, time slots, point-of-contact trustee, and self-contained billing
 * through the 4-step workflow: requested -> slot_confirmed -> deposit_paid -> confirmed.
 */
@Entity
@Table(name = "room_rental")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomRental {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "room_name", nullable = false, length = 100)
    @Builder.Default
    private String roomName = "Parish Hall";

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_person_id", nullable = false)
    private Person renterPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_trustee_id")
    private Trustee contactTrustee;

    @NotBlank
    @Column(name = "event_title", nullable = false, length = 150)
    private String eventTitle;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "estimated_attendees")
    private Integer estimatedAttendees;

    @NotBlank
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "requested";

    @NotNull
    @Column(name = "total_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalFee = BigDecimal.ZERO;

    @NotNull
    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "deposit_paid_date")
    private LocalDate depositPaidDate;

    @Column(name = "deposit_payment_method", length = 30)
    private String depositPaymentMethod;

    @NotNull
    @Column(name = "final_amount_paid", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal finalAmountPaid = BigDecimal.ZERO;

    @Column(name = "final_payment_date")
    private LocalDate finalPaymentDate;

    @Column(name = "final_payment_method", length = 30)
    private String finalPaymentMethod;

    @Column(name = "contract_url", length = 1000)
    private String contractUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (roomName == null || roomName.isBlank()) roomName = "Parish Hall";
        if (status == null || status.isBlank()) status = "requested";
        if (totalFee == null) totalFee = BigDecimal.ZERO;
        if (depositAmount == null) depositAmount = BigDecimal.ZERO;
        if (finalAmountPaid == null) finalAmountPaid = BigDecimal.ZERO;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
