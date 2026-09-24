package com.svivanrilski.svirerp.finance;

import com.svivanrilski.svirerp.event.EventService;
import com.svivanrilski.svirerp.person.PersonService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceJournalCorrectionTest {

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

    @InjectMocks private FinanceService service;

    private final Map<UUID, JournalEntry> entries = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(journalEntryRepo.save(any())).thenAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            if (entry.getId() == null) entry.setId(UUID.randomUUID());
            entries.put(entry.getId(), entry);
            return entry;
        });
        lenient().when(journalEntryRepo.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(entries.get(invocation.getArgument(0))));
        lenient().when(journalLineRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void refundCorrectionSwapsOriginalIncomeLinesAndLinksOriginalEntry() {
        Account clearing = Account.builder().id(UUID.randomUUID()).accountType("asset").build();
        Account revenue = Account.builder().id(UUID.randomUUID()).accountType("revenue").build();
        Fund fund = Fund.builder().id(UUID.randomUUID()).build();
        JournalEntry original = JournalEntry.builder().id(UUID.randomUUID())
                .entryDate(LocalDate.of(2026, 1, 1)).description("Zeffy payment")
                .entryType("general").status("posted").paymentMethod("zeffy")
                .totalDebit(new BigDecimal("200.00")).totalCredit(new BigDecimal("200.00"))
                .categoryAccount(revenue).fund(fund).build();
        entries.put(original.getId(), original);
        when(journalLineRepo.findByJournalEntryId(original.getId())).thenReturn(List.of(
                JournalLine.builder().journalEntry(original).account(clearing).fund(fund)
                        .debitAmount(new BigDecimal("200.00")).creditAmount(BigDecimal.ZERO).build(),
                JournalLine.builder().journalEntry(original).account(revenue).fund(fund)
                        .debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("200.00")).build()));

        JournalEntry correction = service.recordJournalCorrection(
                new FinanceService.JournalCorrectionRequest(original.getId(),
                        LocalDate.of(2026, 1, 1), new BigDecimal("-50.00"),
                        "Refund", "refund-1"));

        assertThat(correction.getStatus()).isEqualTo("posted");
        assertThat(correction.getEntryType()).isEqualTo("reversing");
        assertThat(correction.getCorrectsJournalEntry()).isSameAs(original);
        assertThat(correction.getEntryDate()).isEqualTo(original.getEntryDate());
        ArgumentCaptor<JournalLine> lines = ArgumentCaptor.forClass(JournalLine.class);
        verify(journalLineRepo, times(2)).save(lines.capture());
        assertThat(lines.getAllValues().get(0).getAccount()).isSameAs(revenue);
        assertThat(lines.getAllValues().get(0).getDebitAmount()).isEqualByComparingTo("50.00");
        assertThat(lines.getAllValues().get(1).getAccount()).isSameAs(clearing);
        assertThat(lines.getAllValues().get(1).getCreditAmount()).isEqualByComparingTo("50.00");
    }
}
