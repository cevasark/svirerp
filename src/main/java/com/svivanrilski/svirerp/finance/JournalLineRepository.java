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
public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the controller layer
    // serializes the response, so lazy associations must be eagerly fetched here.
    @EntityGraph(attributePaths = {"journalEntry", "journalEntry.createdBy",
            "journalEntry.approvedBy", "journalEntry.payer", "journalEntry.vendor",
            "journalEntry.serviceRequest", "journalEntry.categoryAccount", "journalEntry.fund",
            "account", "account.parentAccount", "fund"})
    @Override
    Optional<JournalLine> findById(UUID id);

    @EntityGraph(attributePaths = {"journalEntry", "journalEntry.createdBy",
            "journalEntry.approvedBy", "journalEntry.payer", "journalEntry.vendor",
            "journalEntry.serviceRequest", "journalEntry.categoryAccount", "journalEntry.fund",
            "account", "account.parentAccount", "fund"})
    List<JournalLine> findByJournalEntryId(UUID journalEntryId);

    @EntityGraph(attributePaths = {"journalEntry", "journalEntry.createdBy",
            "journalEntry.approvedBy", "journalEntry.payer", "journalEntry.vendor",
            "journalEntry.serviceRequest", "journalEntry.categoryAccount", "journalEntry.fund",
            "account", "account.parentAccount", "fund"})
    Page<JournalLine> findByAccountId(UUID accountId, Pageable pageable);

    /** Sums debits minus credits for an account — used for account balance calculations. */
    @Query("SELECT COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0) " +
           "FROM JournalLine l WHERE l.account.id = :accountId AND l.journalEntry.status = 'posted'")
    BigDecimal netBalanceForAccount(UUID accountId);

    /**
     * Sums debits minus credits for a fund, restricted to accounts of one accountType (revenue or
     * expense) — used to compute a project/fund's total income or total expense.
     */
    @Query("SELECT COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0) " +
           "FROM JournalLine l WHERE l.fund.id = :fundId AND l.account.accountType = :accountType " +
           "AND l.journalEntry.status = 'posted'")
    BigDecimal netAmountForFundAndAccountType(UUID fundId, String accountType);

    /** Per-account debit-minus-credit for one org/accountType, restricted to entries dated within
     *  [from, to] and (optionally) one fund — powers the Statement of Activities report. */
    @Query("SELECT l.account.id AS accountId, l.account.accountNumber AS accountNumber, "
            + "l.account.accountName AS accountName, "
            + "COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0) AS amount "
            + "FROM JournalLine l WHERE l.account.accountType = :accountType "
            + "AND l.journalEntry.status = 'posted' AND l.journalEntry.entryDate BETWEEN :from AND :to "
            + "AND (:fundId IS NULL OR l.fund.id = :fundId) "
            + "GROUP BY l.account.id, l.account.accountNumber, l.account.accountName "
            + "ORDER BY l.account.accountNumber")
    List<AccountAmount> sumByAccountForTypeAndDateRange(String accountType,
            LocalDate from, LocalDate to, UUID fundId);

    /** Per-account debit-minus-credit for one org/accountType, cumulative through :asOf — powers the
     *  Statement of Financial Position report (a snapshot, not a period range). */
    @Query("SELECT l.account.id AS accountId, l.account.accountNumber AS accountNumber, "
            + "l.account.accountName AS accountName, "
            + "COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0) AS amount "
            + "FROM JournalLine l WHERE l.account.accountType = :accountType "
            + "AND l.journalEntry.status = 'posted' AND l.journalEntry.entryDate <= :asOf "
            + "GROUP BY l.account.id, l.account.accountNumber, l.account.accountName "
            + "ORDER BY l.account.accountNumber")
    List<AccountAmount> sumByAccountForTypeAsOfDate(String accountType, LocalDate asOf);

    /** Per-fund debit-minus-credit for one org/accountType, all-time — powers Funds Overview without
     *  an N+1 per-fund call. */
    @Query("SELECT l.fund.id AS fundId, "
            + "COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0) AS amount "
            + "FROM JournalLine l WHERE l.account.accountType = :accountType "
            + "AND l.journalEntry.status = 'posted' AND l.fund IS NOT NULL "
            + "GROUP BY l.fund.id")
    List<FundAmount> sumByFundAndAccountType(String accountType);
}
