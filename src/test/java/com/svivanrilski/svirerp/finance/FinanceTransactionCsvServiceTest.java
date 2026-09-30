package com.svivanrilski.svirerp.finance;

import com.svivanrilski.svirerp.person.Person;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceTransactionCsvServiceTest {

    @Mock JournalLineRepository repository;

    @Test
    void exportsLedgerLinesWithPayerEmailAndSpreadsheetSafeText() throws Exception {
        UUID entryId = UUID.randomUUID();
        Person payer = Person.builder().id(UUID.randomUUID()).firstName("Ada").lastName("Lovelace")
                .email("ada@example.org").build();
        Account account = Account.builder().id(UUID.randomUUID()).accountNumber("1010")
                .accountName("Checking").accountType("asset").accountSubtype("cash")
                .normalBalance("debit").build();
        JournalEntry entry = JournalEntry.builder().id(entryId).entryNumber("JE-1")
                .entryDate(LocalDate.of(2026, 9, 29)).description("=unsafe")
                .entryType("general").status("posted").paymentMethod("check")
                .totalDebit(new BigDecimal("25.00")).totalCredit(new BigDecimal("25.00"))
                .payer(payer).build();
        JournalLine line = JournalLine.builder().id(UUID.randomUUID()).journalEntry(entry)
                .account(account).debitAmount(new BigDecimal("25.00"))
                .creditAmount(BigDecimal.ZERO).memo("Gift, unrestricted").build();
        when(repository.findForTransactionExport(isNull(), isNull(), isNull(), isNull(), any(Sort.class)))
                .thenReturn(List.of(line));

        byte[] bytes = new FinanceTransactionCsvService(repository)
                .export(null, null, null, null, Sort.by(Sort.Order.desc("entryDate")));

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFTransaction ID");
        try (CSVParser parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .build().parse(new StringReader(csv.substring(1)))) {
            CSVRecord record = parser.getRecords().getFirst();
            assertThat(record.get("Payer Name")).isEqualTo("Ada Lovelace");
            assertThat(record.get("Payer Email")).isEqualTo("ada@example.org");
            assertThat(record.get("Description")).isEqualTo("'=unsafe");
            assertThat(record.get("Line Memo")).isEqualTo("Gift, unrestricted");
            assertThat(record.get("Debit Amount")).isEqualTo("25.00");
        }

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findForTransactionExport(isNull(), isNull(), isNull(), isNull(), sortCaptor.capture());
        assertThat(sortCaptor.getValue().getOrderFor("journalEntry.entryDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(sortCaptor.getValue().getOrderFor("journalEntry.id")).isNotNull();
        assertThat(sortCaptor.getValue().getOrderFor("id")).isNotNull();
    }

    @Test
    void exportsHeadersForAnEmptyResult() {
        when(repository.findForTransactionExport(isNull(), isNull(), isNull(), isNull(), any(Sort.class)))
                .thenReturn(List.of());

        byte[] bytes = new FinanceTransactionCsvService(repository)
                .export(null, null, null, null, Sort.unsorted());

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("Payer Name,Payer Email,Vendor ID");
        assertThat(csv.lines()).hasSize(1);
    }
}
