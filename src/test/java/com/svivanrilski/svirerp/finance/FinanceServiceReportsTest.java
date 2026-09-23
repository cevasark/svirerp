package com.svivanrilski.svirerp.finance;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.svivanrilski.svirerp.event.EventService;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the three new report methods. All collaborators are mocked; this doesn't stand up a
 * Spring context or a real DB — {@link AccountAmount}/{@link FundAmount} projections are stubbed
 * with plain records implementing those interfaces.
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceReportsTest {

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


    private record AccountAmountRow(UUID accountId, String accountNumber, String accountName, BigDecimal amount)
            implements AccountAmount {
        @Override public UUID getAccountId() { return accountId; }
        @Override public String getAccountNumber() { return accountNumber; }
        @Override public String getAccountName() { return accountName; }
        @Override public BigDecimal getAmount() { return amount; }
    }

    private record FundAmountRow(UUID fundId, BigDecimal amount) implements FundAmount {
        @Override public UUID getFundId() { return fundId; }
        @Override public BigDecimal getAmount() { return amount; }
    }

    @Test
    void statementOfActivities_convertsRevenueAndExpenseToPositiveDollarsAndComputesNetChange() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        UUID donationAccountId = UUID.randomUUID();
        UUID suppliesAccountId = UUID.randomUUID();

        // Revenue is credit-normal: a $500 donation stores as debit-minus-credit = -500.
        when(journalLineRepo.sumByAccountForTypeAndDateRange("revenue", from, to, null))
                .thenReturn(List.of(new AccountAmountRow(donationAccountId, "4010", "Donation Income",
                        new BigDecimal("-500.00"))));
        // Expense is debit-normal: a $120 supplies purchase stores as debit-minus-credit = +120.
        when(journalLineRepo.sumByAccountForTypeAndDateRange("expense", from, to, null))
                .thenReturn(List.of(new AccountAmountRow(suppliesAccountId, "5020", "Office Supplies Expense",
                        new BigDecimal("120.00"))));

        StatementOfActivities result = financeService.statementOfActivities(from, to, null);

        assertThat(result.income()).hasSize(1);
        assertThat(result.income().get(0).amount()).isEqualByComparingTo("500.00");
        assertThat(result.totalIncome()).isEqualByComparingTo("500.00");
        assertThat(result.expense()).hasSize(1);
        assertThat(result.expense().get(0).amount()).isEqualByComparingTo("120.00");
        assertThat(result.totalExpense()).isEqualByComparingTo("120.00");
        assertThat(result.netChange()).isEqualByComparingTo("380.00");
    }

    @Test
    void statementOfFinancialPosition_balancesAssetsAgainstLiabilitiesPlusEquity() {
        LocalDate asOf = LocalDate.of(2026, 8, 31);
        UUID checkingId = UUID.randomUUID();
        UUID netAssetsId = UUID.randomUUID();

        // $1000 sat in Checking (asset, debit-normal -> stored positive) funded entirely by a $1000
        // donation (revenue, credit-normal -> stored as -1000) with no expenses and no liabilities,
        // and the static Net Assets equity account still at its seeded $0 — nothing ever closes into it.
        when(journalLineRepo.sumByAccountForTypeAsOfDate("asset", asOf))
                .thenReturn(List.of(new AccountAmountRow(checkingId, "1010", "Checking Account",
                        new BigDecimal("1000.00"))));
        when(journalLineRepo.sumByAccountForTypeAsOfDate("liability", asOf))
                .thenReturn(List.of());
        when(journalLineRepo.sumByAccountForTypeAsOfDate("equity", asOf))
                .thenReturn(List.of(new AccountAmountRow(netAssetsId, "3000", "Net Assets", BigDecimal.ZERO)));
        when(journalLineRepo.sumByAccountForTypeAsOfDate("revenue", asOf))
                .thenReturn(List.of(new AccountAmountRow(UUID.randomUUID(), "4010", "Donation Income",
                        new BigDecimal("-1000.00"))));
        when(journalLineRepo.sumByAccountForTypeAsOfDate("expense", asOf))
                .thenReturn(List.of());

        StatementOfFinancialPosition result = financeService.statementOfFinancialPosition(asOf);

        assertThat(result.totalAssets()).isEqualByComparingTo("1000.00");
        assertThat(result.totalLiabilities()).isEqualByComparingTo("0.00");
        assertThat(result.netIncomeToDate()).isEqualByComparingTo("1000.00");
        assertThat(result.totalEquity()).isEqualByComparingTo("1000.00");
        // The double-entry identity this whole design rests on: Assets == Liabilities + Equity,
        // even though the static "Net Assets" equity account itself never moved.
        assertThat(result.totalLiabilitiesAndEquity()).isEqualByComparingTo(result.totalAssets());
    }

    @Test
    void fundsOverview_computesPerFundBalanceFromOpeningPlusIncomeMinusExpense() {
        UUID fundId = UUID.randomUUID();
        Fund fund = Fund.builder().id(fundId).fundName("Building Campaign")
                .fundType("temporarily_restricted").openingBalance(new BigDecimal("100.00")).build();

        when(fundRepo.findByIsActiveOrderByFundName(true)).thenReturn(List.of(fund));
        when(journalLineRepo.sumByFundAndAccountType("revenue"))
                .thenReturn(List.of(new FundAmountRow(fundId, new BigDecimal("-300.00"))));
        when(journalLineRepo.sumByFundAndAccountType("expense"))
                .thenReturn(List.of(new FundAmountRow(fundId, new BigDecimal("50.00"))));

        List<FundOverviewRow> result = financeService.fundsOverview();

        assertThat(result).hasSize(1);
        FundOverviewRow row = result.get(0);
        assertThat(row.fundName()).isEqualTo("Building Campaign");
        assertThat(row.openingBalance()).isEqualByComparingTo("100.00");
        assertThat(row.totalIncome()).isEqualByComparingTo("300.00");
        assertThat(row.totalExpense()).isEqualByComparingTo("50.00");
        // 100 opening + 300 income - 50 expense
        assertThat(row.balance()).isEqualByComparingTo("350.00");
    }
}
