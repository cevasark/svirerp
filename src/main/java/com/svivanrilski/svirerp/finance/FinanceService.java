package com.svivanrilski.svirerp.finance;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.event.EventService;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.organization.OrganizationService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FinanceService {

    private static final Set<String> FUND_TYPES =
            Set.of("unrestricted", "temporarily_restricted", "permanently_restricted");
    private static final Set<String> ACCOUNT_TYPES =
            Set.of("asset", "liability", "equity", "revenue", "expense");
    private static final Set<String> NORMAL_BALANCES = Set.of("debit", "credit");
    private static final Set<String> ENTRY_TYPES =
            Set.of("general", "adjusting", "closing", "reversing", "opening");
    private static final Set<String> ENTRY_STATUSES = Set.of("draft", "posted", "void");
    private static final Set<String> TX_TYPES =
            Set.of("debit", "credit", "check", "transfer", "fee", "interest", "other");
    private static final Set<String> TX_STATUSES = Set.of("pending", "cleared", "void");
    private static final Set<String> RECON_STATUSES =
            Set.of("in_progress", "completed", "discrepancy");
    private static final Set<String> ITEM_TYPES =
            Set.of("transaction", "deposit_in_transit", "outstanding_check", "adjustment");
    private static final Set<String> BUDGET_PERIODS =
            Set.of("annual", "q1", "q2", "q3", "q4", "monthly");
    private static final Set<String> PAYMENT_METHODS =
            Set.of("cash", "check", "zeffy", "bank_transfer", "card", "other", "stripe", "zelle", "facebook");
    private static final Set<String> SERVICE_TYPES =
            Set.of("wedding", "baptism", "funeral", "memorial", "blessing", "other");
    private static final Set<String> SERVICE_REQUEST_STATUSES =
            Set.of("requested", "scheduled", "completed", "cancelled");

    /** account_number, account_name, account_type — seeded lazily on an org's first accounts request. */
    private static final String[][] DEFAULT_ACCOUNTS = {
        {"1000", "Cash on Hand", "asset"},
        {"1010", "Checking Account", "asset"},
        // Pass-through payment platforms hold donations and pay out to Checking in periodic lump
        // sums, net of their own fees — these clearing accounts let a platform's transactions post
        // the moment they happen without overstating Checking until the real payout lands. See
        // StripeWebhookEventApplier#resolveDepositAccount / ZeffyImportService#commitImport.
        {"1020", "Undeposited Funds – Zeffy", "asset"},
        {"1021", "Undeposited Funds – Stripe", "asset"},
        {"1022", "Undeposited Funds – Facebook", "asset"},
        {"3000", "Net Assets", "equity"},
        {"4000", "Membership Dues Income", "revenue"},
        {"4010", "Donation Income", "revenue"},
        {"4020", "Fundraising Income", "revenue"},
        {"4030", "Service Fees Income", "revenue"},
        {"4090", "Other Income", "revenue"},
        {"5000", "Insurance Expense", "expense"},
        {"5010", "Utilities Expense", "expense"},
        {"5020", "Office Supplies Expense", "expense"},
        {"5030", "Candles & Church Supplies Expense", "expense"},
        {"5100", "Priest Compensation", "expense"},
        {"5110", "Priest Housing Expense", "expense"},
        {"5200", "Event Food & Beverage Expense", "expense"},
        {"5210", "Event Services Expense", "expense"},
        {"5300", "Construction & Repairs Expense", "expense"},
        {"5310", "Consulting & Professional Fees Expense", "expense"},
        {"5320", "Payment Processing Fees", "expense"},
        {"5900", "Other Expense", "expense"},
    };

    private final FundRepository fundRepo;
    private final AccountRepository accountRepo;
    private final JournalEntryRepository journalEntryRepo;
    private final JournalLineRepository journalLineRepo;
    private final BudgetRepository budgetRepo;
    private final BankAccountRepository bankAccountRepo;
    private final BankTransactionRepository bankTxRepo;
    private final BankReconciliationRepository reconciliationRepo;
    private final ReconciliationItemRepository reconItemRepo;
    private final VendorRepository vendorRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final OrganizationService orgService;
    private final PersonService personService;
    private final EventService eventService;
    private final EntityManager entityManager;

    // ── Fund ─────────────────────────────────────────────────────────────────

    public Page<Fund> findFundsByOrg(UUID orgId, Pageable pageable) {
        return fundRepo.findByOrgId(orgId, pageable);
    }

    public Fund findFundById(UUID id) {
        return fundRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fund", id));
    }

    @Transactional
    public Fund createFund(Fund fund) {
        validate(FUND_TYPES, fund.getFundType(), "fund type");
        Organization org = orgService.findById(fund.getOrg().getId());
        fund.setOrg(org);
        return fundRepo.save(fund);
    }

    @Transactional
    public Fund updateFund(UUID id, Fund patch) {
        validate(FUND_TYPES, patch.getFundType(), "fund type");
        Fund existing = findFundById(id);
        existing.setFundName(patch.getFundName());
        existing.setFundCode(patch.getFundCode());
        existing.setFundType(patch.getFundType());
        existing.setDescription(patch.getDescription());
        existing.setIsRestricted(patch.getIsRestricted());
        existing.setRestrictionPurpose(patch.getRestrictionPurpose());
        existing.setIsActive(patch.getIsActive());
        existing.setOpeningBalance(patch.getOpeningBalance());
        return fundRepo.save(existing);
    }

    @Transactional
    public void deleteFund(UUID id) {
        if (!fundRepo.existsById(id)) throw new ResourceNotFoundException("Fund", id);
        fundRepo.deleteById(id);
    }

    // ── Account ──────────────────────────────────────────────────────────────

    /** Not read-only: may lazily seed a default chart of accounts on an org's first request. */
    @Transactional
    public Page<Account> findAccountsByOrg(UUID orgId, Pageable pageable) {
        Page<Account> page = accountRepo.findByOrgId(orgId, pageable);
        if (page.isEmpty() && pageable.getPageNumber() == 0) {
            seedDefaultChartOfAccounts(orgId);
            page = accountRepo.findByOrgId(orgId, pageable);
        }
        return page;
    }

    /** Returns root accounts; clients can traverse childAccounts for the full hierarchy. */
    @Transactional
    public List<Account> findRootAccounts(UUID orgId) {
        List<Account> accounts = accountRepo.findByOrgIdAndParentAccountIsNull(orgId);
        if (accounts.isEmpty()) {
            seedDefaultChartOfAccounts(orgId);
            accounts = accountRepo.findByOrgIdAndParentAccountIsNull(orgId);
        }
        return accounts;
    }

    private void seedDefaultChartOfAccounts(UUID orgId) {
        Organization org = orgService.findById(orgId);
        for (String[] def : DEFAULT_ACCOUNTS) {
            if (!accountRepo.existsByOrgIdAndAccountNumber(orgId, def[0])) {
                String accountType = def[2];
                String normalBalance = "asset".equals(accountType) || "expense".equals(accountType)
                        ? "debit" : "credit";
                accountRepo.save(Account.builder()
                        .org(org)
                        .accountNumber(def[0])
                        .accountName(def[1])
                        .accountType(accountType)
                        .normalBalance(normalBalance)
                        .isActive(true)
                        .isSystem(false)
                        .build());
            }
        }
    }

    public Account findAccountById(UUID id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    /** Looks up one of the lazily-seeded default accounts (e.g. "4010", "1010") by number. */
    public Account findAccountByNumber(UUID orgId, String accountNumber) {
        return accountRepo.findByOrgIdAndAccountNumber(orgId, accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account " + accountNumber + " not found for org " + orgId));
    }

    /**
     * Finds an account by number, creating it if missing — for an account introduced to
     * {@link #DEFAULT_ACCOUNTS} after an org's chart of accounts was already seeded (seeding only
     * runs once, on an org's very first accounts request — see {@link #findAccountsByOrg}), so an
     * already-established org would otherwise never pick up a newly-added default account.
     */
    @Transactional
    public Account findOrCreateAccountByNumber(UUID orgId, String accountNumber, String accountName, String accountType) {
        return accountRepo.findByOrgIdAndAccountNumber(orgId, accountNumber).orElseGet(() -> {
            Organization org = orgService.findById(orgId);
            String normalBalance = "asset".equals(accountType) || "expense".equals(accountType) ? "debit" : "credit";
            return accountRepo.save(Account.builder()
                    .org(org)
                    .accountNumber(accountNumber)
                    .accountName(accountName)
                    .accountType(accountType)
                    .normalBalance(normalBalance)
                    .isActive(true)
                    .isSystem(false)
                    .build());
        });
    }

    @Transactional
    public Account createAccount(Account account) {
        validate(ACCOUNT_TYPES, account.getAccountType(), "account type");
        validate(NORMAL_BALANCES, account.getNormalBalance(), "normal balance");
        Organization org = orgService.findById(account.getOrg().getId());
        account.setOrg(org);
        if (account.getParentAccount() != null) {
            Account parent = findAccountById(account.getParentAccount().getId());
            account.setParentAccount(parent);
        }
        return accountRepo.save(account);
    }

    @Transactional
    public Account updateAccount(UUID id, Account patch) {
        validate(ACCOUNT_TYPES, patch.getAccountType(), "account type");
        validate(NORMAL_BALANCES, patch.getNormalBalance(), "normal balance");
        Account existing = findAccountById(id);
        if (existing.getIsSystem()) {
            throw new IllegalArgumentException("System accounts cannot be modified");
        }
        existing.setAccountNumber(patch.getAccountNumber());
        existing.setAccountName(patch.getAccountName());
        existing.setAccountType(patch.getAccountType());
        existing.setAccountSubtype(patch.getAccountSubtype());
        existing.setNormalBalance(patch.getNormalBalance());
        existing.setIsActive(patch.getIsActive());
        existing.setDescription(patch.getDescription());
        return accountRepo.save(existing);
    }

    @Transactional
    public void deleteAccount(UUID id) {
        Account account = findAccountById(id);
        if (account.getIsSystem()) throw new IllegalArgumentException("System accounts cannot be deleted");
        accountRepo.deleteById(id);
    }

    // ── JournalEntry ──────────────────────────────────────────────────────────

    public Page<JournalEntry> findEntriesByOrg(UUID orgId, UUID fundId, String paymentMethod, LocalDate from,
            LocalDate to, Pageable pageable) {
        return journalEntryRepo.findByOrgIdAndFilters(orgId, fundId, paymentMethod, from, to, pageable);
    }

    public JournalEntry findEntryById(UUID id) {
        return journalEntryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", id));
    }

    @Transactional
    public JournalEntry createEntry(JournalEntry entry) {
        validate(ENTRY_TYPES, entry.getEntryType(), "entry type");
        validate(ENTRY_STATUSES, entry.getStatus(), "entry status");
        Organization org = orgService.findById(entry.getOrg().getId());
        entry.setOrg(org);
        if (entry.getCreatedBy() != null) {
            entry.setCreatedBy(personService.findById(entry.getCreatedBy().getId()));
        }
        return journalEntryRepo.save(entry);
    }

    /**
     * Posts a draft journal entry.  Verifies that the lines balance (totalDebit == totalCredit)
     * before changing status to 'posted'.  The DB CHECK constraint is the final guard.
     */
    @Transactional
    public JournalEntry postEntry(UUID id, UUID approvedById) {
        JournalEntry entry = findEntryById(id);
        if (!"draft".equals(entry.getStatus())) {
            throw new IllegalArgumentException("Only draft entries can be posted");
        }
        if (entry.getTotalDebit().compareTo(entry.getTotalCredit()) != 0) {
            throw new IllegalArgumentException("Journal entry does not balance: debit="
                    + entry.getTotalDebit() + " credit=" + entry.getTotalCredit());
        }
        entry.setStatus("posted");
        entry.setApprovedAt(OffsetDateTime.now());
        if (approvedById != null) {
            Person approver = personService.findById(approvedById);
            entry.setApprovedBy(approver);
        }
        return journalEntryRepo.save(entry);
    }

    @Transactional
    public JournalEntry updateEntry(UUID id, JournalEntry patch) {
        JournalEntry existing = findEntryById(id);
        if ("posted".equals(existing.getStatus())) {
            throw new IllegalArgumentException("Posted entries cannot be edited — void and re-enter");
        }
        validate(ENTRY_TYPES, patch.getEntryType(), "entry type");
        existing.setEntryDate(patch.getEntryDate());
        existing.setDescription(patch.getDescription());
        existing.setReference(patch.getReference());
        existing.setEntryType(patch.getEntryType());
        existing.setTotalDebit(patch.getTotalDebit());
        existing.setTotalCredit(patch.getTotalCredit());
        return journalEntryRepo.save(existing);
    }

    @Transactional
    public void deleteEntry(UUID id) {
        JournalEntry entry = findEntryById(id);
        if ("posted".equals(entry.getStatus())) {
            throw new IllegalArgumentException("Posted entries cannot be deleted — void instead");
        }
        journalEntryRepo.deleteById(id);
    }

    // ── JournalLine ──────────────────────────────────────────────────────────

    public List<JournalLine> findLinesByEntry(UUID journalEntryId) {
        return journalLineRepo.findByJournalEntryId(journalEntryId);
    }

    public JournalLine findLineById(UUID id) {
        return journalLineRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalLine", id));
    }

    @Transactional
    public JournalLine createLine(JournalLine line) {
        validateJournalLineSides(line);
        JournalEntry entry = findEntryById(line.getJournalEntry().getId());
        if ("posted".equals(entry.getStatus())) {
            throw new IllegalArgumentException("Cannot add lines to a posted journal entry");
        }
        Account account = findAccountById(line.getAccount().getId());
        line.setJournalEntry(entry);
        line.setAccount(account);
        if (line.getFund() != null) {
            line.setFund(findFundById(line.getFund().getId()));
        }
        return journalLineRepo.save(line);
    }

    @Transactional
    public void deleteLine(UUID id) {
        JournalLine line = findLineById(id);
        if ("posted".equals(line.getJournalEntry().getStatus())) {
            throw new IllegalArgumentException("Cannot remove lines from a posted journal entry");
        }
        journalLineRepo.deleteById(id);
    }

    // ── Budget ────────────────────────────────────────────────────────────────

    public Page<Budget> findBudgetsByOrg(UUID orgId, Pageable pageable) {
        return budgetRepo.findByOrgId(orgId, pageable);
    }

    public Budget findBudgetById(UUID id) {
        return budgetRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", id));
    }

    @Transactional
    public Budget createBudget(Budget budget) {
        validate(BUDGET_PERIODS, budget.getPeriod(), "budget period");
        Organization org = orgService.findById(budget.getOrg().getId());
        Account account = findAccountById(budget.getAccount().getId());
        budget.setOrg(org);
        budget.setAccount(account);
        if (budget.getFund() != null) {
            budget.setFund(findFundById(budget.getFund().getId()));
        }
        return budgetRepo.save(budget);
    }

    @Transactional
    public Budget updateBudget(UUID id, Budget patch) {
        validate(BUDGET_PERIODS, patch.getPeriod(), "budget period");
        Budget existing = findBudgetById(id);
        existing.setBudgetedAmount(patch.getBudgetedAmount());
        existing.setPeriod(patch.getPeriod());
        existing.setNotes(patch.getNotes());
        existing.setIsApproved(patch.getIsApproved());
        existing.setApprovedDate(patch.getApprovedDate());
        return budgetRepo.save(existing);
    }

    @Transactional
    public void deleteBudget(UUID id) {
        if (!budgetRepo.existsById(id)) throw new ResourceNotFoundException("Budget", id);
        budgetRepo.deleteById(id);
    }

    // ── BankAccount ───────────────────────────────────────────────────────────

    public Page<BankAccount> findBankAccountsByOrg(UUID orgId, Pageable pageable) {
        return bankAccountRepo.findByOrgId(orgId, pageable);
    }

    public BankAccount findBankAccountById(UUID id) {
        return bankAccountRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", id));
    }

    @Transactional
    public BankAccount createBankAccount(BankAccount bankAccount) {
        Organization org = orgService.findById(bankAccount.getOrg().getId());
        Account glAccount = findAccountById(bankAccount.getGlAccount().getId());
        bankAccount.setOrg(org);
        bankAccount.setGlAccount(glAccount);
        return bankAccountRepo.save(bankAccount);
    }

    @Transactional
    public BankAccount updateBankAccount(UUID id, BankAccount patch) {
        BankAccount existing = findBankAccountById(id);
        existing.setInstitutionName(patch.getInstitutionName());
        existing.setAccountName(patch.getAccountName());
        existing.setAccountNumberMasked(patch.getAccountNumberMasked());
        existing.setRoutingNumberMasked(patch.getRoutingNumberMasked());
        existing.setAccountType(patch.getAccountType());
        existing.setCurrency(patch.getCurrency());
        existing.setCurrentBalance(patch.getCurrentBalance());
        existing.setOpenedDate(patch.getOpenedDate());
        existing.setIsActive(patch.getIsActive());
        existing.setIsPrimary(patch.getIsPrimary());
        existing.setContactName(patch.getContactName());
        existing.setContactPhone(patch.getContactPhone());
        existing.setNotes(patch.getNotes());
        return bankAccountRepo.save(existing);
    }

    @Transactional
    public void deleteBankAccount(UUID id) {
        if (!bankAccountRepo.existsById(id)) throw new ResourceNotFoundException("BankAccount", id);
        bankAccountRepo.deleteById(id);
    }

    // ── BankTransaction ───────────────────────────────────────────────────────

    public Page<BankTransaction> findTransactionsByAccount(UUID bankAccountId, Pageable pageable) {
        return bankTxRepo.findByBankAccountId(bankAccountId, pageable);
    }

    public BankTransaction findTransactionById(UUID id) {
        return bankTxRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BankTransaction", id));
    }

    @Transactional
    public BankTransaction createTransaction(BankTransaction tx) {
        validate(TX_TYPES, tx.getTransactionType(), "transaction type");
        validate(TX_STATUSES, tx.getStatus(), "transaction status");
        BankAccount bankAccount = findBankAccountById(tx.getBankAccount().getId());
        tx.setBankAccount(bankAccount);
        if (tx.getJournalEntry() != null) {
            tx.setJournalEntry(findEntryById(tx.getJournalEntry().getId()));
        }
        return bankTxRepo.save(tx);
    }

    @Transactional
    public BankTransaction updateTransaction(UUID id, BankTransaction patch) {
        validate(TX_TYPES, patch.getTransactionType(), "transaction type");
        validate(TX_STATUSES, patch.getStatus(), "transaction status");
        BankTransaction existing = findTransactionById(id);
        existing.setTransactionRef(patch.getTransactionRef());
        existing.setTransactionDate(patch.getTransactionDate());
        existing.setPostedDate(patch.getPostedDate());
        existing.setAmount(patch.getAmount());
        existing.setTransactionType(patch.getTransactionType());
        existing.setDescription(patch.getDescription());
        existing.setPayee(patch.getPayee());
        existing.setCheckNumber(patch.getCheckNumber());
        existing.setStatus(patch.getStatus());
        existing.setIsReconciled(patch.getIsReconciled());
        if (patch.getJournalEntry() != null) {
            existing.setJournalEntry(findEntryById(patch.getJournalEntry().getId()));
        }
        return bankTxRepo.save(existing);
    }

    @Transactional
    public void deleteTransaction(UUID id) {
        BankTransaction tx = findTransactionById(id);
        if (tx.getIsReconciled()) {
            throw new IllegalArgumentException("Cannot delete a reconciled transaction");
        }
        bankTxRepo.deleteById(id);
    }

    // ── BankReconciliation ────────────────────────────────────────────────────

    public Page<BankReconciliation> findReconciliationsByAccount(UUID bankAccountId, Pageable pageable) {
        return reconciliationRepo.findByBankAccountId(bankAccountId, pageable);
    }

    public BankReconciliation findReconciliationById(UUID id) {
        return reconciliationRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BankReconciliation", id));
    }

    @Transactional
    public BankReconciliation createReconciliation(BankReconciliation recon) {
        validate(RECON_STATUSES, recon.getStatus(), "reconciliation status");
        BankAccount bankAccount = findBankAccountById(recon.getBankAccount().getId());
        recon.setBankAccount(bankAccount);
        if (recon.getReconciledBy() != null) {
            recon.setReconciledBy(personService.findById(recon.getReconciledBy().getId()));
        }
        BankReconciliation saved = reconciliationRepo.save(recon);
        // Refresh so the GENERATED ALWAYS 'difference' column is populated from the DB.
        entityManager.refresh(saved);
        return saved;
    }

    @Transactional
    public BankReconciliation updateReconciliation(UUID id, BankReconciliation patch) {
        validate(RECON_STATUSES, patch.getStatus(), "reconciliation status");
        BankReconciliation existing = findReconciliationById(id);
        existing.setStatementDate(patch.getStatementDate());
        existing.setStatementBalance(patch.getStatementBalance());
        existing.setBookBalance(patch.getBookBalance());
        existing.setStatus(patch.getStatus());
        existing.setReconciledAt(patch.getReconciledAt());
        existing.setNotes(patch.getNotes());
        BankReconciliation saved = reconciliationRepo.save(existing);
        // Refresh to get the recomputed 'difference' value.
        entityManager.refresh(saved);
        return saved;
    }

    @Transactional
    public void deleteReconciliation(UUID id) {
        if (!reconciliationRepo.existsById(id)) throw new ResourceNotFoundException("BankReconciliation", id);
        reconciliationRepo.deleteById(id);
    }

    // ── ReconciliationItem ────────────────────────────────────────────────────

    public Page<ReconciliationItem> findItemsByReconciliation(UUID reconciliationId, Pageable pageable) {
        return reconItemRepo.findByReconciliationId(reconciliationId, pageable);
    }

    public ReconciliationItem findItemById(UUID id) {
        return reconItemRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReconciliationItem", id));
    }

    @Transactional
    public ReconciliationItem createItem(ReconciliationItem item) {
        validate(ITEM_TYPES, item.getItemType(), "item type");
        BankReconciliation recon = findReconciliationById(item.getReconciliation().getId());
        BankTransaction tx = findTransactionById(item.getBankTransaction().getId());
        if (reconItemRepo.existsByReconciliationIdAndBankTransactionId(recon.getId(), tx.getId())) {
            throw new IllegalArgumentException("Transaction " + tx.getId()
                    + " is already in this reconciliation");
        }
        item.setReconciliation(recon);
        item.setBankTransaction(tx);
        return reconItemRepo.save(item);
    }

    @Transactional
    public ReconciliationItem updateItem(UUID id, ReconciliationItem patch) {
        ReconciliationItem existing = findItemById(id);
        existing.setIsCleared(patch.getIsCleared());
        existing.setItemType(patch.getItemType());
        existing.setNotes(patch.getNotes());
        return reconItemRepo.save(existing);
    }

    @Transactional
    public void deleteItem(UUID id) {
        if (!reconItemRepo.existsById(id)) throw new ResourceNotFoundException("ReconciliationItem", id);
        reconItemRepo.deleteById(id);
    }

    // ── Vendor ───────────────────────────────────────────────────────────────

    public Page<Vendor> findVendorsByOrg(UUID orgId, Pageable pageable) {
        return vendorRepo.findByOrgId(orgId, pageable);
    }

    public Vendor findVendorById(UUID id) {
        return vendorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", id));
    }

    @Transactional
    public Vendor createVendor(Vendor vendor) {
        Organization org = orgService.findById(vendor.getOrg().getId());
        vendor.setOrg(org);
        return vendorRepo.save(vendor);
    }

    @Transactional
    public Vendor updateVendor(UUID id, Vendor patch) {
        Vendor existing = findVendorById(id);
        existing.setName(patch.getName());
        existing.setCategory(patch.getCategory());
        existing.setContactName(patch.getContactName());
        existing.setPhone(patch.getPhone());
        existing.setEmail(patch.getEmail());
        existing.setAddressLine1(patch.getAddressLine1());
        existing.setCity(patch.getCity());
        existing.setState(patch.getState());
        existing.setZip(patch.getZip());
        existing.setNotes(patch.getNotes());
        existing.setIsActive(patch.getIsActive());
        return vendorRepo.save(existing);
    }

    @Transactional
    public void deleteVendor(UUID id) {
        if (!vendorRepo.existsById(id)) throw new ResourceNotFoundException("Vendor", id);
        vendorRepo.deleteById(id);
    }

    // ── ServiceRequest ───────────────────────────────────────────────────────

    public Page<ServiceRequest> findServiceRequestsByOrg(UUID orgId, Pageable pageable) {
        return serviceRequestRepo.findByOrgId(orgId, pageable);
    }

    public ServiceRequest findServiceRequestById(UUID id) {
        return serviceRequestRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceRequest", id));
    }

    @Transactional
    public ServiceRequest createServiceRequest(ServiceRequest request) {
        validate(SERVICE_TYPES, request.getServiceType(), "service type");
        validate(SERVICE_REQUEST_STATUSES, request.getStatus(), "service request status");
        Organization org = orgService.findById(request.getOrg().getId());
        request.setOrg(org);
        if (request.getRequestorPerson() != null) {
            request.setRequestorPerson(personService.findById(request.getRequestorPerson().getId()));
        }
        if (request.getChurchEvent() != null) {
            request.setChurchEvent(eventService.findChurchEventById(request.getChurchEvent().getId()));
        }
        return serviceRequestRepo.save(request);
    }

    @Transactional
    public ServiceRequest updateServiceRequest(UUID id, ServiceRequest patch) {
        validate(SERVICE_TYPES, patch.getServiceType(), "service type");
        validate(SERVICE_REQUEST_STATUSES, patch.getStatus(), "service request status");
        ServiceRequest existing = findServiceRequestById(id);
        if (patch.getRequestorPerson() != null) {
            existing.setRequestorPerson(personService.findById(patch.getRequestorPerson().getId()));
        } else {
            existing.setRequestorPerson(null);
        }
        existing.setServiceType(patch.getServiceType());
        existing.setRequestedDate(patch.getRequestedDate());
        existing.setAgreedAmount(patch.getAgreedAmount());
        existing.setStatus(patch.getStatus());
        if (patch.getChurchEvent() != null) {
            existing.setChurchEvent(eventService.findChurchEventById(patch.getChurchEvent().getId()));
        } else {
            existing.setChurchEvent(null);
        }
        existing.setNotes(patch.getNotes());
        return serviceRequestRepo.save(existing);
    }

    @Transactional
    public void deleteServiceRequest(UUID id) {
        if (!serviceRequestRepo.existsById(id)) throw new ResourceNotFoundException("ServiceRequest", id);
        serviceRequestRepo.deleteById(id);
    }

    /** Amount still owed: agreedAmount minus every posted income entry tagged with this request. */
    public BigDecimal serviceRequestBalance(UUID serviceRequestId) {
        ServiceRequest request = findServiceRequestById(serviceRequestId);
        BigDecimal paid = journalEntryRepo.sumPaidForServiceRequest(serviceRequestId);
        return request.getAgreedAmount().subtract(paid);
    }

    // ── Record Income / Record Expense ──────────────────────────────────────
    // Simple transaction entry points: build a balanced, immediately-posted 2-line JournalEntry
    // (no draft/approval step — see FinanceService class docs / the Finance Phase 1 plan) so a
    // treasurer never has to think in debits and credits.

    @Transactional
    public JournalEntry recordIncome(RecordIncomeRequest req) {
        validate(PAYMENT_METHODS, req.paymentMethod(), "payment method");
        Organization org = orgService.findById(req.orgId());
        Account category = findAccountById(req.categoryAccountId());
        requireAccountType(category, "revenue", "Income category account");
        Account deposit = findAccountById(req.depositAccountId());
        requireAccountType(deposit, "asset", "Deposit account");
        Fund fund = req.fundId() != null ? findFundById(req.fundId()) : null;
        Person payer = req.payerId() != null ? personService.findById(req.payerId()) : null;
        ServiceRequest serviceRequest = req.serviceRequestId() != null
                ? findServiceRequestById(req.serviceRequestId()) : null;

        // A processing fee (e.g. Stripe) deducted before deposit means the amount actually
        // banked is less than the gross amount recorded as income — split the deposit line into
        // net + a fee expense line rather than (incorrectly) depositing the full gross. Guards
        // against a fee that's zero/negative/>= the gross amount (shouldn't happen with a real
        // Stripe fee, but a bad value here should degrade to the plain 2-line entry, not corrupt
        // the books or violate the one-sided-JournalLine DB constraint).
        boolean hasFee = req.feeAmount() != null && req.feeAmount().signum() > 0
                && req.feeAmount().compareTo(req.amount()) < 0;
        BigDecimal depositAmount = hasFee ? req.amount().subtract(req.feeAmount()) : req.amount();
        Account feeAccount = hasFee ? findAccountById(req.feeAccountId()) : null;
        if (hasFee) {
            requireAccountType(feeAccount, "expense", "Fee account");
        }

        JournalEntry entry = journalEntryRepo.save(JournalEntry.builder()
                .org(org)
                .entryDate(req.entryDate())
                .description(req.description())
                .entryType("general")
                .status("draft")
                .totalDebit(req.amount())
                .totalCredit(req.amount())
                .paymentMethod(req.paymentMethod())
                .checkNumber(req.checkNumber())
                .payer(payer)
                .serviceRequest(serviceRequest)
                .categoryAccount(category)
                .fund(fund)
                .build());

        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(deposit).fund(fund)
                .debitAmount(depositAmount).creditAmount(BigDecimal.ZERO)
                .memo(req.description()).build());
        if (hasFee) {
            journalLineRepo.save(JournalLine.builder()
                    .journalEntry(entry).account(feeAccount).fund(fund)
                    .debitAmount(req.feeAmount()).creditAmount(BigDecimal.ZERO)
                    .memo("Payment processing fee").build());
        }
        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(category).fund(fund)
                .debitAmount(BigDecimal.ZERO).creditAmount(req.amount())
                .memo(req.description()).build());

        return postEntry(entry.getId(), null);
    }

    @Transactional
    public JournalEntry recordExpense(RecordExpenseRequest req) {
        validate(PAYMENT_METHODS, req.paymentMethod(), "payment method");
        Organization org = orgService.findById(req.orgId());
        Account category = findAccountById(req.categoryAccountId());
        requireAccountType(category, "expense", "Expense category account");
        Account payment = findAccountById(req.paymentAccountId());
        requireAccountType(payment, "asset", "Payment account");
        Fund fund = req.fundId() != null ? findFundById(req.fundId()) : null;
        Vendor vendor = req.vendorId() != null ? findVendorById(req.vendorId()) : null;

        JournalEntry entry = journalEntryRepo.save(JournalEntry.builder()
                .org(org)
                .entryDate(req.entryDate())
                .description(req.description())
                .entryType("general")
                .status("draft")
                .totalDebit(req.amount())
                .totalCredit(req.amount())
                .paymentMethod(req.paymentMethod())
                .checkNumber(req.checkNumber())
                .vendor(vendor)
                .categoryAccount(category)
                .fund(fund)
                .build());

        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(category).fund(fund)
                .debitAmount(req.amount()).creditAmount(BigDecimal.ZERO)
                .memo(req.description()).build());
        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(payment).fund(fund)
                .debitAmount(BigDecimal.ZERO).creditAmount(req.amount())
                .memo(req.description()).build());

        return postEntry(entry.getId(), null);
    }

    /**
     * Records a platform payout landing in the real bank account: money moves from a pass-through
     * platform's "Undeposited Funds" clearing account (see DEFAULT_ACCOUNTS) into Checking. No
     * revenue account is touched — the income was already recognized when the original donation was
     * recorded against the clearing account, so this is purely a transfer between two asset accounts.
     */
    @Transactional
    public JournalEntry recordTransfer(RecordTransferRequest req) {
        Organization org = orgService.findById(req.orgId());
        Account from = findAccountById(req.fromAccountId());
        requireAccountType(from, "asset", "Source account");
        Account to = findAccountById(req.toAccountId());
        requireAccountType(to, "asset", "Destination account");

        JournalEntry entry = journalEntryRepo.save(JournalEntry.builder()
                .org(org)
                .entryDate(req.entryDate())
                .description(req.description())
                .entryType("general")
                .status("draft")
                .totalDebit(req.amount())
                .totalCredit(req.amount())
                .build());

        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(to)
                .debitAmount(req.amount()).creditAmount(BigDecimal.ZERO)
                .memo(req.description()).build());
        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(from)
                .debitAmount(BigDecimal.ZERO).creditAmount(req.amount())
                .memo(req.description()).build());

        return postEntry(entry.getId(), null);
    }

    /**
     * Moves already-recognized revenue from one category account to another — e.g. correcting a
     * Zeffy Ticket-category transaction that turned out to actually be a membership payment, after
     * it already posted to the wrong revenue account. Doesn't touch any asset/clearing account, so
     * cash-in-hand figures are unaffected; this is purely a categorization fix.
     */
    @Transactional
    public JournalEntry reclassifyIncome(UUID orgId, LocalDate entryDate, BigDecimal amount, String description,
            UUID fromCategoryAccountId, UUID toCategoryAccountId) {
        Organization org = orgService.findById(orgId);
        Account from = findAccountById(fromCategoryAccountId);
        requireAccountType(from, "revenue", "Source account");
        Account to = findAccountById(toCategoryAccountId);
        requireAccountType(to, "revenue", "Destination account");

        JournalEntry entry = journalEntryRepo.save(JournalEntry.builder()
                .org(org)
                .entryDate(entryDate)
                .description(description)
                .entryType("general")
                .status("draft")
                .totalDebit(amount)
                .totalCredit(amount)
                .build());

        // Revenue is credit-normal — debiting `from` reduces its balance, crediting `to` increases it.
        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(from)
                .debitAmount(amount).creditAmount(BigDecimal.ZERO)
                .memo(description).build());
        journalLineRepo.save(JournalLine.builder()
                .journalEntry(entry).account(to)
                .debitAmount(BigDecimal.ZERO).creditAmount(amount)
                .memo(description).build());

        return postEntry(entry.getId(), null);
    }

    private void requireAccountType(Account account, String expectedType, String label) {
        if (!expectedType.equals(account.getAccountType())) {
            throw new IllegalArgumentException(label + " must be a '" + expectedType
                    + "' account (got '" + account.getAccountType() + "')");
        }
    }

    // ── Reporting helpers ─────────────────────────────────────────────────────

    public BigDecimal accountBalance(UUID accountId) {
        return journalLineRepo.netBalanceForAccount(accountId);
    }

    /** Powers the "project financial status" view: a fund's total income, total expense, and balance. */
    public FundSummary fundSummary(UUID fundId) {
        Fund fund = findFundById(fundId);
        BigDecimal totalIncome = journalLineRepo.netAmountForFundAndAccountType(fundId, "revenue").negate();
        BigDecimal totalExpense = journalLineRepo.netAmountForFundAndAccountType(fundId, "expense");
        BigDecimal balance = fund.getOpeningBalance().add(totalIncome).subtract(totalExpense);
        return new FundSummary(fund.getOpeningBalance(), totalIncome, totalExpense, balance);
    }

    // ── Reports ──────────────────────────────────────────────────────────────

    private StatementOfActivitiesLine toIncomeLine(AccountAmount a) {
        // revenue is credit-normal — its stored debit-minus-credit is negative for a real donation,
        // so negate it to get a positive income dollar figure for display.
        return new StatementOfActivitiesLine(a.getAccountId(), a.getAccountNumber(), a.getAccountName(),
                a.getAmount().negate());
    }

    private StatementOfActivitiesLine toExpenseLine(AccountAmount a) {
        // expense is debit-normal — its stored debit-minus-credit is already a positive dollar figure.
        return new StatementOfActivitiesLine(a.getAccountId(), a.getAccountNumber(), a.getAccountName(),
                a.getAmount());
    }

    /** Income vs. expense by category for a period — see StatementOfActivities. */
    public StatementOfActivities statementOfActivities(UUID orgId, LocalDate from, LocalDate to, UUID fundId) {
        List<StatementOfActivitiesLine> income = journalLineRepo
                .sumByAccountForOrgAndTypeAndDateRange(orgId, "revenue", from, to, fundId)
                .stream().map(this::toIncomeLine).toList();
        List<StatementOfActivitiesLine> expense = journalLineRepo
                .sumByAccountForOrgAndTypeAndDateRange(orgId, "expense", from, to, fundId)
                .stream().map(this::toExpenseLine).toList();

        BigDecimal totalIncome = income.stream().map(StatementOfActivitiesLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = expense.stream().map(StatementOfActivitiesLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StatementOfActivities(from, to, income, totalIncome, expense, totalExpense,
                totalIncome.subtract(totalExpense));
    }

    /** Every account's balance as of a date, grouped Assets/Liabilities/Equity — see
     *  StatementOfFinancialPosition for the computed "Net Income (to date)" equity line. */
    public StatementOfFinancialPosition statementOfFinancialPosition(UUID orgId, LocalDate asOf) {
        List<BalanceSheetLine> assets = journalLineRepo
                .sumByAccountForOrgAndTypeAsOfDate(orgId, "asset", asOf)
                .stream().map(a -> new BalanceSheetLine(a.getAccountId(), a.getAccountNumber(),
                        a.getAccountName(), a.getAmount()))
                .toList();
        List<BalanceSheetLine> liabilities = journalLineRepo
                .sumByAccountForOrgAndTypeAsOfDate(orgId, "liability", asOf)
                .stream().map(a -> new BalanceSheetLine(a.getAccountId(), a.getAccountNumber(),
                        a.getAccountName(), a.getAmount().negate()))
                .toList();
        List<BalanceSheetLine> equity = journalLineRepo
                .sumByAccountForOrgAndTypeAsOfDate(orgId, "equity", asOf)
                .stream().map(a -> new BalanceSheetLine(a.getAccountId(), a.getAccountNumber(),
                        a.getAccountName(), a.getAmount().negate()))
                .toList();

        BigDecimal totalAssets = assets.stream().map(BalanceSheetLine::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiabilities = liabilities.stream().map(BalanceSheetLine::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal equityAccountsTotal = equity.stream().map(BalanceSheetLine::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // This app has no period-closing process that zeroes revenue/expense into equity, so a
        // real accounting equity account alone won't reflect income earned so far — compute it live
        // (never stored) so Assets == Liabilities + Equity still holds by the double-entry identity.
        BigDecimal revenueToDate = journalLineRepo.sumByAccountForOrgAndTypeAsOfDate(orgId, "revenue", asOf)
                .stream().map(a -> a.getAmount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenseToDate = journalLineRepo.sumByAccountForOrgAndTypeAsOfDate(orgId, "expense", asOf)
                .stream().map(AccountAmount::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netIncomeToDate = revenueToDate.subtract(expenseToDate);

        BigDecimal totalEquity = equityAccountsTotal.add(netIncomeToDate);
        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);

        return new StatementOfFinancialPosition(asOf, assets, totalAssets, liabilities, totalLiabilities,
                equity, netIncomeToDate, totalEquity, totalLiabilitiesAndEquity);
    }

    /** Every active fund's opening balance/income/expense/balance in one batch — same math as
     *  fundSummary(), without an N+1 per-fund call. */
    public List<FundOverviewRow> fundsOverview(UUID orgId) {
        List<Fund> funds = fundRepo.findByOrgIdAndIsActiveOrderByFundName(orgId, true);

        Map<UUID, BigDecimal> incomeByFund = journalLineRepo.sumByFundAndAccountType(orgId, "revenue")
                .stream().collect(Collectors.toMap(FundAmount::getFundId, a -> a.getAmount().negate()));
        Map<UUID, BigDecimal> expenseByFund = journalLineRepo.sumByFundAndAccountType(orgId, "expense")
                .stream().collect(Collectors.toMap(FundAmount::getFundId, FundAmount::getAmount));

        return funds.stream().map(fund -> {
            BigDecimal income = incomeByFund.getOrDefault(fund.getId(), BigDecimal.ZERO);
            BigDecimal expense = expenseByFund.getOrDefault(fund.getId(), BigDecimal.ZERO);
            BigDecimal balance = fund.getOpeningBalance().add(income).subtract(expense);
            return new FundOverviewRow(fund.getId(), fund.getFundName(), fund.getFundType(),
                    fund.getOpeningBalance(), income, expense, balance);
        }).toList();
    }

    // ── Internal validators ───────────────────────────────────────────────────

    private void validate(Set<String> allowed, String value, String label) {
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException("Invalid " + label + ": '" + value
                    + "'. Allowed: " + allowed);
        }
    }

    private void validateJournalLineSides(JournalLine line) {
        boolean hasDebit = line.getDebitAmount() != null && line.getDebitAmount().compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = line.getCreditAmount() != null && line.getCreditAmount().compareTo(BigDecimal.ZERO) > 0;
        if (hasDebit == hasCredit) { // both zero or both non-zero
            throw new IllegalArgumentException(
                    "Exactly one of debitAmount or creditAmount must be positive (got debit="
                            + line.getDebitAmount() + ", credit=" + line.getCreditAmount() + ")");
        }
    }
}
