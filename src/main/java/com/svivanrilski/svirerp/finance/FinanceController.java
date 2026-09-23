package com.svivanrilski.svirerp.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService service;

    // ── Fund ─────────────────────────────────────────────────────────────────

    @GetMapping("/api/funds")
    public Page<Fund> listFunds(Pageable pageable) {
        return service.findFunds(pageable);
    }

    @GetMapping("/api/funds/{id}")
    public Fund getFund(@PathVariable UUID id) {
        return service.findFundById(id);
    }

    @PostMapping("/api/funds")
    public ResponseEntity<Fund> createFund(@Valid @RequestBody Fund fund) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFund(fund));
    }

    @PutMapping("/api/funds/{id}")
    public Fund updateFund(@PathVariable UUID id, @Valid @RequestBody Fund fund) {
        return service.updateFund(id, fund);
    }

    @DeleteMapping("/api/funds/{id}")
    public ResponseEntity<Void> deleteFund(@PathVariable UUID id) {
        service.deleteFund(id);
        return ResponseEntity.noContent().build();
    }

    /** Powers the project/fund financial-status view: total income, total expense, and balance. */
    @GetMapping("/api/funds/{id}/summary")
    public FundSummary fundSummary(@PathVariable UUID id) {
        return service.fundSummary(id);
    }

    // ── Account ──────────────────────────────────────────────────────────────

    @GetMapping("/api/accounts")
    public Page<Account> listAccounts(Pageable pageable) {
        return service.findAccounts(pageable);
    }

    @GetMapping("/api/accounts/roots")
    public List<Account> rootAccounts() {
        return service.findRootAccounts();
    }

    @GetMapping("/api/accounts/{id}")
    public Account getAccount(@PathVariable UUID id) {
        return service.findAccountById(id);
    }

    @GetMapping("/api/accounts/{id}/balance")
    public BigDecimal accountBalance(@PathVariable UUID id) {
        return service.accountBalance(id);
    }

    @PostMapping("/api/accounts")
    public ResponseEntity<Account> createAccount(@Valid @RequestBody Account account) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAccount(account));
    }

    @PutMapping("/api/accounts/{id}")
    public Account updateAccount(@PathVariable UUID id, @Valid @RequestBody Account account) {
        return service.updateAccount(id, account);
    }

    @DeleteMapping("/api/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        service.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    // ── JournalEntry ──────────────────────────────────────────────────────────

    @GetMapping("/api/journal-entries")
    public Page<JournalEntry> listEntries(
            @RequestParam(required = false) UUID fundId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) LocalDate entryDateFrom,
            @RequestParam(required = false) LocalDate entryDateTo,
            Pageable pageable) {
        return service.findEntries(fundId, paymentMethod, entryDateFrom, entryDateTo, pageable);
    }

    @GetMapping("/api/journal-entries/{id}")
    public JournalEntry getEntry(@PathVariable UUID id) {
        return service.findEntryById(id);
    }

    @PostMapping("/api/journal-entries")
    public ResponseEntity<JournalEntry> createEntry(@Valid @RequestBody JournalEntry entry) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEntry(entry));
    }

    @PutMapping("/api/journal-entries/{id}")
    public JournalEntry updateEntry(@PathVariable UUID id, @Valid @RequestBody JournalEntry entry) {
        return service.updateEntry(id, entry);
    }

    /** Posts a draft entry; supply the approver's person ID as a request parameter. */
    @PostMapping("/api/journal-entries/{id}/post")
    public JournalEntry postEntry(@PathVariable UUID id,
            @RequestParam(required = false) UUID approvedBy) {
        return service.postEntry(id, approvedBy);
    }

    @DeleteMapping("/api/journal-entries/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable UUID id) {
        service.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }

    // ── JournalLine ──────────────────────────────────────────────────────────

    @GetMapping("/api/journal-entries/{entryId}/lines")
    public List<JournalLine> listLines(@PathVariable UUID entryId) {
        return service.findLinesByEntry(entryId);
    }

    @GetMapping("/api/journal-lines/{id}")
    public JournalLine getLine(@PathVariable UUID id) {
        return service.findLineById(id);
    }

    @PostMapping("/api/journal-lines")
    public ResponseEntity<JournalLine> createLine(@Valid @RequestBody JournalLine line) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLine(line));
    }

    @DeleteMapping("/api/journal-lines/{id}")
    public ResponseEntity<Void> deleteLine(@PathVariable UUID id) {
        service.deleteLine(id);
        return ResponseEntity.noContent().build();
    }

    // ── Budget ────────────────────────────────────────────────────────────────

    @GetMapping("/api/budgets")
    public Page<Budget> listBudgets(Pageable pageable) {
        return service.findBudgets(pageable);
    }

    @GetMapping("/api/budgets/{id}")
    public Budget getBudget(@PathVariable UUID id) {
        return service.findBudgetById(id);
    }

    @PostMapping("/api/budgets")
    public ResponseEntity<Budget> createBudget(@Valid @RequestBody Budget budget) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBudget(budget));
    }

    @PutMapping("/api/budgets/{id}")
    public Budget updateBudget(@PathVariable UUID id, @Valid @RequestBody Budget budget) {
        return service.updateBudget(id, budget);
    }

    @DeleteMapping("/api/budgets/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable UUID id) {
        service.deleteBudget(id);
        return ResponseEntity.noContent().build();
    }

    // ── BankAccount ───────────────────────────────────────────────────────────

    @GetMapping("/api/bank-accounts")
    public Page<BankAccount> listBankAccounts(Pageable pageable) {
        return service.findBankAccounts(pageable);
    }

    @GetMapping("/api/bank-accounts/{id}")
    public BankAccount getBankAccount(@PathVariable UUID id) {
        return service.findBankAccountById(id);
    }

    @PostMapping("/api/bank-accounts")
    public ResponseEntity<BankAccount> createBankAccount(@Valid @RequestBody BankAccount bankAccount) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBankAccount(bankAccount));
    }

    @PutMapping("/api/bank-accounts/{id}")
    public BankAccount updateBankAccount(@PathVariable UUID id, @Valid @RequestBody BankAccount bankAccount) {
        return service.updateBankAccount(id, bankAccount);
    }

    @DeleteMapping("/api/bank-accounts/{id}")
    public ResponseEntity<Void> deleteBankAccount(@PathVariable UUID id) {
        service.deleteBankAccount(id);
        return ResponseEntity.noContent().build();
    }

    // ── BankTransaction ───────────────────────────────────────────────────────

    @GetMapping("/api/bank-accounts/{accountId}/transactions")
    public Page<BankTransaction> listTransactions(@PathVariable UUID accountId, Pageable pageable) {
        return service.findTransactionsByAccount(accountId, pageable);
    }

    @GetMapping("/api/bank-transactions/{id}")
    public BankTransaction getTransaction(@PathVariable UUID id) {
        return service.findTransactionById(id);
    }

    @PostMapping("/api/bank-transactions")
    public ResponseEntity<BankTransaction> createTransaction(@Valid @RequestBody BankTransaction tx) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTransaction(tx));
    }

    @PutMapping("/api/bank-transactions/{id}")
    public BankTransaction updateTransaction(@PathVariable UUID id, @Valid @RequestBody BankTransaction tx) {
        return service.updateTransaction(id, tx);
    }

    @DeleteMapping("/api/bank-transactions/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        service.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    // ── BankReconciliation ────────────────────────────────────────────────────

    @GetMapping("/api/bank-accounts/{accountId}/reconciliations")
    public Page<BankReconciliation> listReconciliations(@PathVariable UUID accountId, Pageable pageable) {
        return service.findReconciliationsByAccount(accountId, pageable);
    }

    @GetMapping("/api/bank-reconciliations/{id}")
    public BankReconciliation getReconciliation(@PathVariable UUID id) {
        return service.findReconciliationById(id);
    }

    @PostMapping("/api/bank-reconciliations")
    public ResponseEntity<BankReconciliation> createReconciliation(@Valid @RequestBody BankReconciliation recon) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createReconciliation(recon));
    }

    @PutMapping("/api/bank-reconciliations/{id}")
    public BankReconciliation updateReconciliation(@PathVariable UUID id,
            @Valid @RequestBody BankReconciliation recon) {
        return service.updateReconciliation(id, recon);
    }

    @DeleteMapping("/api/bank-reconciliations/{id}")
    public ResponseEntity<Void> deleteReconciliation(@PathVariable UUID id) {
        service.deleteReconciliation(id);
        return ResponseEntity.noContent().build();
    }

    // ── ReconciliationItem ────────────────────────────────────────────────────

    @GetMapping("/api/bank-reconciliations/{reconciliationId}/items")
    public Page<ReconciliationItem> listItems(@PathVariable UUID reconciliationId, Pageable pageable) {
        return service.findItemsByReconciliation(reconciliationId, pageable);
    }

    @GetMapping("/api/reconciliation-items/{id}")
    public ReconciliationItem getItem(@PathVariable UUID id) {
        return service.findItemById(id);
    }

    @PostMapping("/api/reconciliation-items")
    public ResponseEntity<ReconciliationItem> createItem(@Valid @RequestBody ReconciliationItem item) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createItem(item));
    }

    @PutMapping("/api/reconciliation-items/{id}")
    public ReconciliationItem updateItem(@PathVariable UUID id,
            @Valid @RequestBody ReconciliationItem item) {
        return service.updateItem(id, item);
    }

    @DeleteMapping("/api/reconciliation-items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id) {
        service.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    // ── Vendor ───────────────────────────────────────────────────────────────

    @GetMapping("/api/vendors")
    public Page<Vendor> listVendors(Pageable pageable) {
        return service.findVendors(pageable);
    }

    @GetMapping("/api/vendors/{id}")
    public Vendor getVendor(@PathVariable UUID id) {
        return service.findVendorById(id);
    }

    @PostMapping("/api/vendors")
    public ResponseEntity<Vendor> createVendor(@Valid @RequestBody Vendor vendor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createVendor(vendor));
    }

    @PutMapping("/api/vendors/{id}")
    public Vendor updateVendor(@PathVariable UUID id, @Valid @RequestBody Vendor vendor) {
        return service.updateVendor(id, vendor);
    }

    @DeleteMapping("/api/vendors/{id}")
    public ResponseEntity<Void> deleteVendor(@PathVariable UUID id) {
        service.deleteVendor(id);
        return ResponseEntity.noContent().build();
    }

    // ── ServiceRequest ───────────────────────────────────────────────────────

    @GetMapping("/api/service-requests")
    public Page<ServiceRequest> listServiceRequests(Pageable pageable) {
        return service.findServiceRequests(pageable);
    }

    @GetMapping("/api/service-requests/{id}")
    public ServiceRequest getServiceRequest(@PathVariable UUID id) {
        return service.findServiceRequestById(id);
    }

    @PostMapping("/api/service-requests")
    public ResponseEntity<ServiceRequest> createServiceRequest(@Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createServiceRequest(request));
    }

    @PutMapping("/api/service-requests/{id}")
    public ServiceRequest updateServiceRequest(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
        return service.updateServiceRequest(id, request);
    }

    @DeleteMapping("/api/service-requests/{id}")
    public ResponseEntity<Void> deleteServiceRequest(@PathVariable UUID id) {
        service.deleteServiceRequest(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/service-requests/{id}/balance")
    public BigDecimal serviceRequestBalance(@PathVariable UUID id) {
        return service.serviceRequestBalance(id);
    }

    // ── Record Income / Record Expense ──────────────────────────────────────

    @PostMapping("/api/income-transactions")
    public ResponseEntity<JournalEntry> recordIncome(@RequestBody RecordIncomeRequest body) {
        RecordIncomeRequest req = new RecordIncomeRequest(body.entryDate(), body.amount(),
                body.description(), body.categoryAccountId(), body.depositAccountId(), body.fundId(),
                body.payerId(), body.serviceRequestId(), body.paymentMethod(), body.checkNumber(),
                body.feeAmount(), body.feeAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordIncome(req));
    }

    @PostMapping("/api/expense-transactions")
    public ResponseEntity<JournalEntry> recordExpense(@RequestBody RecordExpenseRequest body) {
        RecordExpenseRequest req = new RecordExpenseRequest(body.entryDate(), body.amount(),
                body.description(), body.categoryAccountId(), body.paymentAccountId(), body.fundId(),
                body.vendorId(), body.paymentMethod(), body.checkNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordExpense(req));
    }

    /** Records a pass-through platform's payout landing in the real bank account — a transfer from
     *  its "Undeposited Funds" clearing account into Checking, no revenue account touched. */
    @PostMapping("/api/transfer-transactions")
    public ResponseEntity<JournalEntry> recordTransfer(@RequestBody RecordTransferRequest body) {
        RecordTransferRequest req = new RecordTransferRequest(body.entryDate(), body.amount(),
                body.description(), body.fromAccountId(), body.toAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordTransfer(req));
    }

    // ── Reports ──────────────────────────────────────────────────────────────

    @GetMapping("/api/reports/statement-of-activities")
    public StatementOfActivities statementOfActivities(
            @RequestParam LocalDate entryDateFrom,
            @RequestParam LocalDate entryDateTo,
            @RequestParam(required = false) UUID fundId) {
        return service.statementOfActivities(entryDateFrom, entryDateTo, fundId);
    }

    @GetMapping("/api/reports/statement-of-financial-position")
    public StatementOfFinancialPosition statementOfFinancialPosition(
            @RequestParam LocalDate asOf) {
        return service.statementOfFinancialPosition(asOf);
    }

    @GetMapping("/api/reports/funds-overview")
    public List<FundOverviewRow> fundsOverview() {
        return service.fundsOverview();
    }
}
