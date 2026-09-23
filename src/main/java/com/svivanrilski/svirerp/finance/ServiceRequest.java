package com.svivanrilski.svirerp.finance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.event.ChurchEvent;
import com.svivanrilski.svirerp.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A requested church service (wedding, baptism, funeral, memorial) tracked independently of
 * scheduling, so a deposit can be recorded before a calendar date is confirmed. Payments against
 * it are ordinary income JournalEntry rows tagged with this request's id — see
 * FinanceService#recordIncome / #serviceRequestBalance.
 */
@Entity
@Table(name = "service_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestor_person_id")
    private Person requestorPerson;

    /** Allowed values (DB CHECK): wedding, baptism, funeral, memorial, blessing, other. */
    @NotNull
    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "agreed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal agreedAmount = BigDecimal.ZERO;

    /** Allowed values (DB CHECK): requested, scheduled, completed, cancelled. */
    @Column(nullable = false, length = 30)
    private String status = "requested";

    /** Nullable: set once the request has a confirmed calendar entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "church_event_id")
    private ChurchEvent churchEvent;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
