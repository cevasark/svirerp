package com.svivanrilski.svirerp.person;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Imports a Zeffy "contacts" export — unlike the Zeffy Transactions import, this file lists every
 * contact Zeffy knows about regardless of whether they ever paid anything, which is the only way
 * to catch people who registered for free ($0) membership ("Followers"): a $0 registration never
 * produces a financial transaction, so it's invisible to the Transactions importer entirely. Rows
 * matching an existing Person (by email) are skipped outright — this import only ever enrolls
 * people who are completely new to the system; see {@link PersonImportRowApplier}.
 *
 * <p>Dispatches on file extension like {@code ZeffyImportService} — Zeffy's real export is an
 * .xlsx spreadsheet; .csv is also accepted since that's a common "save as" alternative.
 */
@Service
@RequiredArgsConstructor
public class PersonImportService {

    private static final String COL_FIRST_NAME = "First name";
    private static final String COL_LAST_NAME = "Last name";
    private static final String COL_EMAIL = "Email";
    private static final String COL_SECONDARY_EMAIL = "Secondary email";
    private static final String COL_LANGUAGE = "Language";
    private static final String COL_ADDRESS = "Address";
    private static final String COL_CITY = "City";
    private static final String COL_POSTAL_CODE = "Postal code";
    private static final String COL_COUNTRY = "Country";
    private static final String COL_REGION = "Region";
    private static final String COL_PHONE = "Phone";
    private static final String COL_UNSUBSCRIBED = "Unsubscribed";

    private static final String[] TEMPLATE_HEADERS = {
            COL_FIRST_NAME, COL_LAST_NAME, COL_EMAIL, COL_SECONDARY_EMAIL, COL_LANGUAGE, COL_ADDRESS,
            COL_CITY, COL_POSTAL_CODE, COL_COUNTRY, COL_REGION, COL_PHONE, COL_UNSUBSCRIBED,
    };

    private static final Set<String> REQUIRED_COLUMNS = Set.of(COL_FIRST_NAME, COL_LAST_NAME, COL_EMAIL);

    private final PersonImportRowApplier rowApplier;

    public record PersonImportResult(int created, int skippedExisting, List<PersonImportRowError> failed) {
    }

    public record PersonImportRowError(int rowNumber, String email, String message) {
    }

    public byte[] buildImportTemplate() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader(TEMPLATE_HEADERS).build())) {
            printer.printRecord("Jane", "Doe", "jane.doe@example.com", "", "English", "123 Main St",
                    "Springfield", "62704", "USA", "IL", "555-0100", "No");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public PersonImportResult importPeople(UUID orgId, MultipartFile file) {
        int created = 0;
        int skippedExisting = 0;
        List<PersonImportRowError> failed = new ArrayList<>();

        List<Map<String, String>> rawRows;
        try {
            rawRows = parseFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        int rowNumber = 1; // header is row 1; first data row is row 2
        for (Map<String, String> raw : rawRows) {
            rowNumber++;
            String email = raw.get(COL_EMAIL);
            try {
                PersonImportRow row = parseRow(raw);
                PersonImportRowApplier.ApplyOutcome outcome = rowApplier.applyRow(orgId, row);
                if (outcome == PersonImportRowApplier.ApplyOutcome.CREATED) {
                    created++;
                } else {
                    skippedExisting++;
                }
            } catch (Exception ex) {
                failed.add(new PersonImportRowError(rowNumber, email, ex.getMessage()));
            }
        }

        return new PersonImportResult(created, skippedExisting, failed);
    }

    private PersonImportRow parseRow(Map<String, String> raw) {
        return new PersonImportRow(
                required(raw, COL_FIRST_NAME),
                required(raw, COL_LAST_NAME),
                required(raw, COL_EMAIL),
                raw.get(COL_PHONE),
                raw.get(COL_ADDRESS),
                raw.get(COL_CITY),
                raw.get(COL_POSTAL_CODE),
                isUnsubscribed(raw.get(COL_UNSUBSCRIBED)));
    }

    private String required(Map<String, String> raw, String column) {
        String value = raw.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    private boolean isUnsubscribed(String value) {
        return value != null && (value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true"));
    }

    private List<Map<String, String>> parseFile(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        String lower = name != null ? name.toLowerCase() : "";
        try (InputStream in = file.getInputStream()) {
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                return parseExcel(in);
            }
            return parseCsv(in);
        }
    }

    private List<Map<String, String>> parseCsv(InputStream inputStream) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build()
                .parse(reader);
        validateHeaders(parser.getHeaderNames());
        for (CSVRecord record : parser) {
            Map<String, String> raw = new LinkedHashMap<>();
            for (String header : parser.getHeaderNames()) {
                String value = record.isSet(header) ? record.get(header) : null;
                raw.put(header, (value == null || value.isBlank()) ? null : value);
            }
            rows.add(raw);
        }
        return rows;
    }

    private List<Map<String, String>> parseExcel(InputStream inputStream) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Iterator<Row> rowIterator = sheet.rowIterator();
            if (!rowIterator.hasNext()) {
                return rows;
            }

            Row headerRow = rowIterator.next();
            int lastCol = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < lastCol; i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                headers.add(cell != null ? formatter.formatCellValue(cell).trim() : "");
            }
            validateHeaders(headers);

            while (rowIterator.hasNext()) {
                Row excelRow = rowIterator.next();
                Map<String, String> raw = new LinkedHashMap<>();
                boolean anyValue = false;
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    if (header.isBlank()) continue;
                    Cell cell = excelRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell != null ? formatter.formatCellValue(cell).trim() : null;
                    if (value != null && value.isBlank()) value = null;
                    if (value != null) anyValue = true;
                    raw.put(header, value);
                }
                if (anyValue) {
                    rows.add(raw);
                }
            }
        }
        return rows;
    }

    private void validateHeaders(List<String> presentHeaders) {
        Set<String> present = new HashSet<>(presentHeaders);
        if (!present.containsAll(REQUIRED_COLUMNS)) {
            throw new IllegalArgumentException(
                    "This doesn't look like a Zeffy contacts export — expected columns including "
                            + String.join(", ", REQUIRED_COLUMNS) + ".");
        }
    }
}
