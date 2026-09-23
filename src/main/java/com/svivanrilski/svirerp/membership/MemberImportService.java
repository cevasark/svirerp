package com.svivanrilski.svirerp.membership;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns the CSV template and the best-effort import loop. Deliberately calls
 * MembershipService.importOrUpdateMemberFromRow (a different bean) once per
 * row rather than looping inside a single big transaction — see the comment
 * on that method for why a shared transaction would risk silently discarding
 * successful rows when a later row fails.
 */
@Service
@RequiredArgsConstructor
public class MemberImportService {

    private static final String[] HEADERS = {
            "firstName", "lastName", "email", "phone", "addressLine1", "city", "state", "zip",
            "dateOfBirth", "membershipType", "memberNumber", "joinDate", "expiryDate", "status", "emailOptIn",
    };

    private final MembershipService membershipService;

    public record MemberImportResult(int created, int updated, List<MemberImportRowError> failed) {
    }

    public record MemberImportRowError(int rowNumber, String email, String message) {
    }

    public byte[] buildImportTemplateCsv() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            printer.printRecord("Jane", "Doe", "jane.doe@example.com", "555-0100", "123 Main St",
                    "Springfield", "IL", "62704", "1985-06-15", "General", "M-1001",
                    "2026-01-01", "2026-12-31", "active", "true");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public MemberImportResult importMembers(MultipartFile file) {
        int created = 0;
        int updated = 0;
        List<MemberImportRowError> failed = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : parser) {
                // getRecordNumber() is 1-based over data rows only (the header is
                // consumed during parsing, not counted) — +1 maps it to the line
                // number a spreadsheet user would actually see (header = line 1).
                int rowNumber = (int) record.getRecordNumber() + 1;
                String email = optional(record, "email");
                try {
                    MemberImportRow row = parseRow(record);
                    MembershipService.ImportOutcome outcome =
                            membershipService.importOrUpdateMemberFromRow(row);
                    if (outcome == MembershipService.ImportOutcome.CREATED) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception ex) {
                    failed.add(new MemberImportRowError(rowNumber, email, ex.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        return new MemberImportResult(created, updated, failed);
    }

    private MemberImportRow parseRow(CSVRecord record) {
        return new MemberImportRow(
                required(record, "firstName"),
                required(record, "lastName"),
                required(record, "email"),
                optional(record, "phone"),
                optional(record, "addressLine1"),
                optional(record, "city"),
                optional(record, "state"),
                optional(record, "zip"),
                optionalDate(record, "dateOfBirth"),
                required(record, "membershipType"),
                optional(record, "memberNumber"),
                requiredDate(record, "joinDate"),
                optionalDate(record, "expiryDate"),
                optional(record, "status"),
                optionalBoolean(record, "emailOptIn"));
    }

    private String required(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    /** Returns null for blank values or columns missing from this file's header entirely. */
    private String optional(CSVRecord record, String column) {
        String value;
        try {
            value = record.get(column);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return (value == null || value.isBlank()) ? null : value;
    }

    private LocalDate requiredDate(CSVRecord record, String column) {
        LocalDate value = optionalDate(record, column);
        if (value == null) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    private LocalDate optionalDate(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(column + " must be a date in YYYY-MM-DD format, got: " + value);
        }
    }

    private Boolean optionalBoolean(CSVRecord record, String column) {
        String value = optional(record, column);
        return value == null ? null : Boolean.parseBoolean(value);
    }
}
