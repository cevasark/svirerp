package com.svivanrilski.svirerp.finance;

import com.svivanrilski.svirerp.person.Person;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceTransactionCsvService {

    public static final String[] HEADERS = {
            "Transaction ID", "Entry Number", "Entry Date", "Description", "Reference",
            "Entry Type", "Status", "Payment Method", "Check Number", "Total Debit",
            "Total Credit", "Category Account Number", "Category Account Name",
            "Category Account Type", "Transaction Fund Code", "Transaction Fund Name",
            "Transaction Fund Type", "Payer ID", "Payer Name", "Payer Email", "Vendor ID",
            "Vendor Name", "Service Request ID", "Service Type", "Corrects Transaction ID",
            "Corrects Entry Number", "Created By ID", "Created By Name", "Created At",
            "Approved By ID", "Approved By Name", "Approved At", "Line ID", "Line Number",
            "Account ID", "Account Number", "Account Name", "Account Type", "Account Subtype",
            "Normal Balance", "Line Fund Code", "Line Fund Name", "Line Description",
            "Debit Amount", "Credit Amount", "Line Memo"
    };

    private static final Map<String, String> SORT_PROPERTIES = Map.ofEntries(
            Map.entry("id", "journalEntry.id"),
            Map.entry("entryNumber", "journalEntry.entryNumber"),
            Map.entry("entryDate", "journalEntry.entryDate"),
            Map.entry("description", "journalEntry.description"),
            Map.entry("entryType", "journalEntry.entryType"),
            Map.entry("status", "journalEntry.status"),
            Map.entry("paymentMethod", "journalEntry.paymentMethod"),
            Map.entry("totalDebit", "journalEntry.totalDebit"),
            Map.entry("totalCredit", "journalEntry.totalCredit"),
            Map.entry("createdAt", "journalEntry.createdAt"));

    private final JournalLineRepository journalLineRepository;

    @Transactional(readOnly = true)
    public byte[] export(UUID fundId, String paymentMethod, LocalDate from, LocalDate to, Sort requestedSort) {
        List<JournalLine> lines = journalLineRepository.findForTransactionExport(
                fundId, paymentMethod, from, to, exportSort(requestedSort));
        Map<UUID, Integer> lineNumbers = new HashMap<>();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);
            try (CSVPrinter csv = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                    CSVFormat.RFC4180.builder().setHeader(HEADERS).build())) {
                for (JournalLine line : lines) {
                    JournalEntry entry = line.getJournalEntry();
                    int lineNumber = lineNumbers.merge(entry.getId(), 1, Integer::sum);
                    Account category = entry.getCategoryAccount();
                    Fund entryFund = entry.getFund();
                    Person payer = entry.getPayer();
                    Vendor vendor = entry.getVendor();
                    ServiceRequest request = entry.getServiceRequest();
                    JournalEntry correction = entry.getCorrectsJournalEntry();
                    Account account = line.getAccount();
                    Fund lineFund = line.getFund();

                    csv.printRecord(
                            value(entry.getId()), safe(entry.getEntryNumber()), value(entry.getEntryDate()),
                            safe(entry.getDescription()), safe(entry.getReference()), safe(entry.getEntryType()),
                            safe(entry.getStatus()), safe(entry.getPaymentMethod()), safe(entry.getCheckNumber()),
                            value(entry.getTotalDebit()), value(entry.getTotalCredit()),
                            category == null ? "" : safe(category.getAccountNumber()),
                            category == null ? "" : safe(category.getAccountName()),
                            category == null ? "" : safe(category.getAccountType()),
                            entryFund == null ? "" : safe(entryFund.getFundCode()),
                            entryFund == null ? "" : safe(entryFund.getFundName()),
                            entryFund == null ? "" : safe(entryFund.getFundType()),
                            payer == null ? "" : value(payer.getId()), personName(payer),
                            payer == null ? "" : safe(payer.getEmail()),
                            vendor == null ? "" : value(vendor.getId()),
                            vendor == null ? "" : safe(vendor.getName()),
                            request == null ? "" : value(request.getId()),
                            request == null ? "" : safe(request.getServiceType()),
                            correction == null ? "" : value(correction.getId()),
                            correction == null ? "" : safe(correction.getEntryNumber()),
                            entry.getCreatedBy() == null ? "" : value(entry.getCreatedBy().getId()),
                            personName(entry.getCreatedBy()), value(entry.getCreatedAt()),
                            entry.getApprovedBy() == null ? "" : value(entry.getApprovedBy().getId()),
                            personName(entry.getApprovedBy()), value(entry.getApprovedAt()),
                            value(line.getId()), lineNumber, value(account.getId()),
                            safe(account.getAccountNumber()), safe(account.getAccountName()),
                            safe(account.getAccountType()), safe(account.getAccountSubtype()),
                            safe(account.getNormalBalance()), lineFund == null ? "" : safe(lineFund.getFundCode()),
                            lineFund == null ? "" : safe(lineFund.getFundName()), safe(line.getDescription()),
                            value(line.getDebitAmount()), value(line.getCreditAmount()), safe(line.getMemo()));
                }
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not generate transaction CSV", ex);
        }
    }

    private Sort exportSort(Sort requested) {
        Sort mapped = Sort.unsorted();
        if (requested != null) {
            for (Sort.Order order : requested) {
                String property = SORT_PROPERTIES.get(order.getProperty());
                if (property != null) mapped = mapped.and(Sort.by(order.withProperty(property)));
            }
        }
        if (mapped.isUnsorted()) mapped = Sort.by(Sort.Order.desc("journalEntry.entryDate"));
        return mapped.and(Sort.by("journalEntry.id", "id"));
    }

    private String personName(Person person) {
        return person == null ? "" : safe(person.getFirstName() + " " + person.getLastName());
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Prevent spreadsheet programs from evaluating user-entered text as a formula. */
    private String safe(String value) {
        if (value == null || value.isEmpty()) return "";
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
    }
}
