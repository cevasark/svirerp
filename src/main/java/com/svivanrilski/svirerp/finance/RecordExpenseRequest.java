package com.svivanrilski.svirerp.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for the simple "Record Expense" flow — insurance, utilities, supplies, priest
 * compensation/housing, event and construction costs, etc. FinanceService#recordExpense turns this
 * into a balanced, posted JournalEntry with two JournalLines.
 */
public record RecordExpenseRequest(
        LocalDate entryDate,
        BigDecimal amount,
        String description,
        UUID categoryAccountId,
        UUID paymentAccountId,
        UUID fundId,
        UUID vendorId,
        String paymentMethod,
        String checkNumber) {
}
