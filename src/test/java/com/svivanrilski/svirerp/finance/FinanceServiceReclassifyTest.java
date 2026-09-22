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
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.organization.OrganizationService;
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
 * Covers reclassifyIncome — moves already-recognized revenue between two category accounts (e.g.
 * a Zeffy transaction that posted as a Ticket purchase but turned out to be a membership payment)
 * without touching any asset/clearing account. All collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceReclassifyTest {

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
    @Mock private OrganizationService orgService;
    @Mock private PersonService personService;
    @Mock private EventService eventService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private FinanceService financeService;

    private UUID orgId;
    private UUID ticketAccountId;
    private UUID donationAccountId;

    private final AtomicReference<JournalEntry> savedEntry = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        ticketAccountId = UUID.randomUUID();
        donationAccountId = UUID.randomUUID();

        Organization org = new Organization();
        org.setId(orgId);
        when(orgService.findById(orgId)).thenReturn(org);

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
    void reclassifyIncome_movesRevenueBetweenTwoRevenueAccounts() {
        when(accountRepo.findById(ticketAccountId)).thenReturn(Optional.of(accountOf(ticketAccountId, "revenue")));
        when(accountRepo.findById(donationAccountId)).thenReturn(Optional.of(accountOf(donationAccountId, "revenue")));

        JournalEntry entry = financeService.reclassifyIncome(orgId, LocalDate.now(), new BigDecimal("150.00"),
                "Reclassify Zeffy Ticket → membership", ticketAccountId, donationAccountId);

        assertThat(entry.getStatus()).isEqualTo("posted");
        assertThat(entry.getTotalDebit()).isEqualByComparingTo("150.00");
        assertThat(entry.getTotalCredit()).isEqualByComparingTo("150.00");

        ArgumentCaptor<JournalLine> lineCaptor = ArgumentCaptor.forClass(JournalLine.class);
        verify(journalLineRepo, times(2)).save(lineCaptor.capture());
        List<JournalLine> lines = lineCaptor.getAllValues();

        JournalLine fromLine = lines.get(0);
        assertThat(fromLine.getAccount().getId()).isEqualTo(ticketAccountId);
        assertThat(fromLine.getDebitAmount()).isEqualByComparingTo("150.00");

        JournalLine toLine = lines.get(1);
        assertThat(toLine.getAccount().getId()).isEqualTo(donationAccountId);
        assertThat(toLine.getCreditAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void reclassifyIncome_rejectsNonRevenueAccounts() {
        when(accountRepo.findById(ticketAccountId)).thenReturn(Optional.of(accountOf(ticketAccountId, "asset")));

        assertThatThrownBy(() -> financeService.reclassifyIncome(orgId, LocalDate.now(), new BigDecimal("150.00"),
                "bad", ticketAccountId, donationAccountId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source account");
    }
}
