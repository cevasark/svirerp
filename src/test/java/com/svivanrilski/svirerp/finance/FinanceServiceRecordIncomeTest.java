package com.svivanrilski.svirerp.finance;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.svivanrilski.svirerp.event.EventService;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the processing-fee split added to recordIncome: when a fee is present, the deposit line
 * should carry the net amount and a separate expense line should carry the fee, while the category
 * account is still credited for the full gross amount — and the whole entry still balances. All
 * collaborators are mocked; this doesn't stand up a Spring context or a real DB.
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceRecordIncomeTest {

    @Mock private FundRepository fundRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private JournalEntryRepository journalEntryRepo;
    @Mock private JournalLineRepository journalLineRepo;
    @Mock private BudgetRepository budgetRepo;
    @Mock private BankAccountRepository bankAccountRepo;
    @Mock private BankTransactionRepository bankTxRepo;
    @Mock private BankReconciliationRepository reconciliationRepo;
    @Mock private ReconciliationItemRepository reconItemRepo;
    @Mock private VendorRepository vendorRepo;
    @Mock private ServiceRequestRepository serviceRequestRepo;
    @Mock private PersonService personService;
    @Mock private EventService eventService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private FinanceService financeService;

    private UUID categoryAccountId;
    private UUID depositAccountId;
    private UUID feeAccountId;

    /** journalEntryRepo.save() and postEntry()'s subsequent findById() must agree on the same
     *  instance — recordIncome saves a draft entry, then postEntry() re-fetches it by id to check
     *  its balance before flipping it to 'posted'. Mockito mocks don't share state across stubs by
     *  themselves, so this reference is what ties the two calls together. */
    private final AtomicReference<JournalEntry> savedEntry = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        categoryAccountId = UUID.randomUUID();
        depositAccountId = UUID.randomUUID();
        feeAccountId = UUID.randomUUID();

        when(accountRepo.findById(categoryAccountId)).thenReturn(Optional.of(accountOf(categoryAccountId, "revenue")));
        when(accountRepo.findById(depositAccountId)).thenReturn(Optional.of(accountOf(depositAccountId, "asset")));
        // Only the with-fee test actually looks this up — the other two never take that branch.
        lenient().when(accountRepo.findById(feeAccountId)).thenReturn(Optional.of(accountOf(feeAccountId, "expense")));

        when(journalEntryRepo.save(any(JournalEntry.class))).thenAnswer(inv -> {
            JournalEntry entry = inv.getArgument(0);
            if (entry.getId() == null) entry.setId(UUID.randomUUID());
            savedEntry.set(entry);
            return entry;
        });
        when(journalEntryRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedEntry.get()));
        when(journalLineRepo.save(any(JournalLine.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Account accountOf(UUID id, String type) {
        Account account = new Account();
        account.setId(id);
        account.setAccountType(type);
        return account;
    }

    private RecordIncomeRequest requestWithFee(BigDecimal amount, BigDecimal feeAmount) {
        return new RecordIncomeRequest(LocalDate.now(), amount, "Stripe payment",
                categoryAccountId, depositAccountId, null, null, null, "stripe", null,
                feeAmount, feeAccountId);
    }

    @Test
    void recordIncome_withFee_splitsDepositAndBooksFeeExpense() {
        // $1.00 gross, $0.36 fee -> $0.64 net, matching the example worked through with the user.
        JournalEntry entry = financeService.recordIncome(requestWithFee(new BigDecimal("1.00"), new BigDecimal("0.36")));

        assertThat(entry.getStatus()).isEqualTo("posted");
        assertThat(entry.getTotalDebit()).isEqualByComparingTo("1.00");
        assertThat(entry.getTotalCredit()).isEqualByComparingTo("1.00");

        ArgumentCaptor<JournalLine> lineCaptor = ArgumentCaptor.forClass(JournalLine.class);
        verify(journalLineRepo, times(3)).save(lineCaptor.capture());
        List<JournalLine> lines = lineCaptor.getAllValues();

        JournalLine depositLine = lines.get(0);
        assertThat(depositLine.getAccount().getId()).isEqualTo(depositAccountId);
        assertThat(depositLine.getDebitAmount()).isEqualByComparingTo("0.64");
        assertThat(depositLine.getCreditAmount()).isEqualByComparingTo("0.00");

        JournalLine feeLine = lines.get(1);
        assertThat(feeLine.getAccount().getId()).isEqualTo(feeAccountId);
        assertThat(feeLine.getDebitAmount()).isEqualByComparingTo("0.36");

        JournalLine categoryLine = lines.get(2);
        assertThat(categoryLine.getAccount().getId()).isEqualTo(categoryAccountId);
        assertThat(categoryLine.getCreditAmount()).isEqualByComparingTo("1.00");

        // Balances: total debits (0.64 + 0.36) == total credit (1.00).
        BigDecimal totalDebits = depositLine.getDebitAmount().add(feeLine.getDebitAmount());
        assertThat(totalDebits).isEqualByComparingTo(categoryLine.getCreditAmount());
    }

    @Test
    void recordIncome_withoutFee_postsPlainTwoLineEntry() {
        financeService.recordIncome(requestWithFee(new BigDecimal("150.00"), null));

        ArgumentCaptor<JournalLine> lineCaptor = ArgumentCaptor.forClass(JournalLine.class);
        verify(journalLineRepo, times(2)).save(lineCaptor.capture());
        List<JournalLine> lines = lineCaptor.getAllValues();
        assertThat(lines.get(0).getDebitAmount()).isEqualByComparingTo("150.00");
        assertThat(lines.get(1).getCreditAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void recordIncome_feeNotLessThanAmount_fallsBackToTwoLineEntry() {
        // Defensive guard: a fee >= gross would drive the deposit line to zero/negative, violating
        // the one-sided-JournalLine DB constraint — recordIncome should ignore a bad fee rather
        // than post a broken entry.
        financeService.recordIncome(requestWithFee(new BigDecimal("1.00"), new BigDecimal("1.00")));

        verify(journalLineRepo, times(2)).save(any(JournalLine.class));
    }
}
