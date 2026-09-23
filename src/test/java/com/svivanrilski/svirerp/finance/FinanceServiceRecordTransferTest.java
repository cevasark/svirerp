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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers recordTransfer — the "Record Platform Payout" flow that moves money from a pass-through
 * platform's clearing account into Checking without touching any revenue account. All collaborators
 * are mocked; this doesn't stand up a Spring context or a real DB.
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceRecordTransferTest {

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

    private UUID clearingAccountId;
    private UUID checkingAccountId;

    private final AtomicReference<JournalEntry> savedEntry = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        clearingAccountId = UUID.randomUUID();
        checkingAccountId = UUID.randomUUID();

        // Only the happy-path test reaches these — the rejection test throws before posting.
        lenient().when(journalEntryRepo.save(any(JournalEntry.class))).thenAnswer(inv -> {
            JournalEntry entry = inv.getArgument(0);
            if (entry.getId() == null) entry.setId(UUID.randomUUID());
            savedEntry.set(entry);
            return entry;
        });
        lenient().when(journalEntryRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedEntry.get()));
        lenient().when(journalLineRepo.save(any(JournalLine.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Account accountOf(UUID id, String type) {
        Account account = new Account();
        account.setId(id);
        account.setAccountType(type);
        return account;
    }

    @Test
    void recordTransfer_postsBalancedTwoLineEntryBetweenAssetAccounts() {
        when(accountRepo.findById(clearingAccountId)).thenReturn(Optional.of(accountOf(clearingAccountId, "asset")));
        when(accountRepo.findById(checkingAccountId)).thenReturn(Optional.of(accountOf(checkingAccountId, "asset")));

        RecordTransferRequest req = new RecordTransferRequest(LocalDate.now(), new BigDecimal("250.00"),
                "Zeffy payout", clearingAccountId, checkingAccountId);
        JournalEntry entry = financeService.recordTransfer(req);

        assertThat(entry.getStatus()).isEqualTo("posted");
        assertThat(entry.getTotalDebit()).isEqualByComparingTo("250.00");
        assertThat(entry.getTotalCredit()).isEqualByComparingTo("250.00");
        assertThat(entry.getCategoryAccount()).isNull();

        ArgumentCaptor<JournalLine> lineCaptor = ArgumentCaptor.forClass(JournalLine.class);
        verify(journalLineRepo, times(2)).save(lineCaptor.capture());
        List<JournalLine> lines = lineCaptor.getAllValues();

        JournalLine toLine = lines.get(0);
        assertThat(toLine.getAccount().getId()).isEqualTo(checkingAccountId);
        assertThat(toLine.getDebitAmount()).isEqualByComparingTo("250.00");
        assertThat(toLine.getCreditAmount()).isEqualByComparingTo("0.00");

        JournalLine fromLine = lines.get(1);
        assertThat(fromLine.getAccount().getId()).isEqualTo(clearingAccountId);
        assertThat(fromLine.getCreditAmount()).isEqualByComparingTo("250.00");
        assertThat(fromLine.getDebitAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void recordTransfer_rejectsNonAssetDestination() {
        when(accountRepo.findById(clearingAccountId)).thenReturn(Optional.of(accountOf(clearingAccountId, "asset")));
        when(accountRepo.findById(checkingAccountId)).thenReturn(Optional.of(accountOf(checkingAccountId, "revenue")));

        RecordTransferRequest req = new RecordTransferRequest(LocalDate.now(), new BigDecimal("100.00"),
                "Bad transfer", clearingAccountId, checkingAccountId);

        assertThatThrownBy(() -> financeService.recordTransfer(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination account");
    }
}
