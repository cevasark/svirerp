package com.svivanrilski.svirerp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the controller layer
    // serializes the response, so lazy associations must be eagerly fetched here.
    @EntityGraph(attributePaths = {"createdBy", "approvedBy", "payer", "vendor", "serviceRequest",
            "serviceRequest.requestorPerson", "categoryAccount", "categoryAccount.parentAccount", "fund",
            "correctsJournalEntry"})
    @Override
    Optional<JournalEntry> findById(UUID id);

    @EntityGraph(attributePaths = {"createdBy", "approvedBy", "payer", "vendor", "serviceRequest",
            "serviceRequest.requestorPerson", "categoryAccount", "categoryAccount.parentAccount", "fund",
            "correctsJournalEntry"})
    Page<JournalEntry> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "approvedBy", "payer", "vendor", "serviceRequest",
            "serviceRequest.requestorPerson", "categoryAccount", "categoryAccount.parentAccount", "fund",
            "correctsJournalEntry"})
    Page<JournalEntry> findByStatus(String status, Pageable pageable);

    /** Backs the transaction list's filter bar (fund / payment method / date range), each filter
     *  optional — a single flexible query instead of a combinatorial method per filter combination,
     *  so adding a future filter doesn't double the method count again. */
    @EntityGraph(attributePaths = {"createdBy", "approvedBy", "payer", "vendor", "serviceRequest",
            "serviceRequest.requestorPerson", "categoryAccount", "categoryAccount.parentAccount", "fund",
            "correctsJournalEntry"})
    @Query("SELECT e FROM JournalEntry e WHERE (:fundId IS NULL OR e.fund.id = :fundId) "
            + "AND (:paymentMethod IS NULL OR e.paymentMethod = :paymentMethod) "
            + "AND (:entryDateFrom IS NULL OR e.entryDate >= :entryDateFrom) "
            + "AND (:entryDateTo IS NULL OR e.entryDate <= :entryDateTo)")
    Page<JournalEntry> findByFilters(UUID fundId, String paymentMethod,
            LocalDate entryDateFrom, LocalDate entryDateTo, Pageable pageable);

    /** Payment history for a service request — used to compute the balance still owed. */
    @EntityGraph(attributePaths = {"payer", "categoryAccount", "fund", "correctsJournalEntry"})
    List<JournalEntry> findByServiceRequestId(UUID serviceRequestId);

    /** Sums totalDebit for a service request's posted income entries — the amount paid so far. */
    @Query("SELECT COALESCE(SUM(e.totalDebit), 0) FROM JournalEntry e "
            + "WHERE e.serviceRequest.id = :serviceRequestId AND e.status = 'posted'")
    BigDecimal sumPaidForServiceRequest(UUID serviceRequestId);
}
