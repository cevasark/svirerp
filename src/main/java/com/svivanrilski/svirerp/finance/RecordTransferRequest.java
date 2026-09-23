package com.svivanrilski.svirerp.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for the "Record Platform Payout" flow — money moving from a pass-through platform's
 * "Undeposited Funds" clearing account into the real Checking account once the platform's lump-sum
 * payout actually lands. FinanceService#recordTransfer turns this into a balanced, posted
 * JournalEntry with two JournalLines and no revenue/expense account involved.
 */
public record RecordTransferRequest(
        LocalDate entryDate,
        BigDecimal amount,
        String description,
        UUID fromAccountId,
        UUID toAccountId) {
}
