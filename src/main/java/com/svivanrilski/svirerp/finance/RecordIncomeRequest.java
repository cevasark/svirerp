package com.svivanrilski.svirerp.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for the simple "Record Income" flow — donations, membership dues, fundraising and
 * service payments received via Zeffy, cash, or check. FinanceService#recordIncome turns this into
 * a balanced, posted JournalEntry with two JournalLines — or three when {@code feeAmount} is set
 * (e.g. a Stripe processing fee deducted before deposit): deposit gets debited for the net amount,
 * {@code feeAccountId} gets debited for the fee, and the category account is still credited for the
 * full gross {@code amount}. feeAmount/feeAccountId are null for every non-fee-deducting source
 * (Zeffy, cash, check) — recordIncome falls back to the plain 2-line entry in that case.
 */
public record RecordIncomeRequest(
        LocalDate entryDate,
        BigDecimal amount,
        String description,
        UUID categoryAccountId,
        UUID depositAccountId,
        UUID fundId,
        UUID payerId,
        UUID serviceRequestId,
        String paymentMethod,
        String checkNumber,
        BigDecimal feeAmount,
        UUID feeAccountId) {
}
