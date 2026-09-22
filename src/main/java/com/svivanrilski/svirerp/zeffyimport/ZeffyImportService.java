package com.svivanrilski.svirerp.zeffyimport;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.organization.OrganizationService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the Zeffy CSV import: preview (parse + persist every row with a computed outcome,
 * no writes to Person/Member/MemberPayment/JournalEntry yet), campaign-to-fund mapping, and
 * commit (apply every still-eligible row via {@link ZeffyImportRowApplier}, a different bean, so
 * each row gets its own transaction).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ZeffyImportService {

    private static final int OUTCOME_DETAIL_MAX_LENGTH = 500;

    private static final String COL_TRANSACTION_ID = "Id";
    private static final String COL_AMOUNT = "Amount";
    private static final String COL_CATEGORY = "Category";
    private static final String COL_FIRST_NAME = "First Name";
    private static final String COL_LAST_NAME = "Last Name";
    private static final String COL_EMAIL = "Email";
    private static final String COL_TRANSACTION_DATE = "Creation Date (America/Chicago)";
    private static final String COL_AVAILABLE_DATE = "Available on (America/Chicago)";
    private static final String COL_ELIGIBLE_AMOUNT = "Eligible amount";
    private static final String COL_CAMPAIGN_TITLE = "Campaign";

    /** Columns that must be present for a file to be recognized as a Zeffy Transactions export —
     *  Payments-format uploads (which have "Payment Status" instead of "Id"/"Category") are
     *  rejected with a clear error rather than silently mis-parsed. */
    private static final Set<String> REQUIRED_COLUMNS =
            Set.of(COL_TRANSACTION_ID, COL_CATEGORY, COL_TRANSACTION_DATE, COL_EMAIL);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"));

    /** Columns that are real dates in an .xlsx export — extracted as an actual date value rather
     *  than trusting the cell's display format (see {@link #cellToText}). */
    private static final Set<String> DATE_COLUMNS = Set.of(COL_TRANSACTION_DATE, COL_AVAILABLE_DATE);

    private static final Set<String> VALID_CATEGORIES = Set.of("Donation", "Ticket");

    private final ZeffyImportBatchRepository batchRepo;
    private final ZeffyImportRowRepository rowRepo;
    private final ZeffyCampaignMappingRepository mappingRepo;
    private final ZeffyImportRowApplier rowApplier;
    private final OrganizationService orgService;
    private final PersonService personService;
    private final MembershipService membershipService;
    private final FinanceService financeService;

    public record CampaignMappingRequest(String campaignTitle, UUID fundId, Boolean isMembershipPayment) {
    }

    public record ZeffyImportSummary(
            UUID batchId,
            int totalRows,
            long readyCount,
            long duplicateCount,
            long skippedStatusCount,
            long unmappedCampaignCount,
            long errorCount,
            long committedCount,
            long newPersonCount,
            long newMemberCount,
            BigDecimal totalAmountReady,
            List<String> unmappedCampaignTitles) {
    }

    public record ZeffyImportCommitResult(UUID batchId, int committed, int failed, int stillUnmappedCampaign) {
    }

    public record ReprocessMembershipResult(int rowsProcessed, int membersCreated) {
    }

    // ── Batches ──────────────────────────────────────────────────────────────

    /** Defaults to newest-first only when the caller didn't ask for a specific column sort. */
    public Page<ZeffyImportBatch> findBatchesByOrg(UUID orgId, Pageable pageable) {
        Pageable effective = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "uploadedAt"))
                : pageable;
        return batchRepo.findByOrgId(orgId, effective);
    }

    public ZeffyImportBatch findBatchById(UUID id) {
        return batchRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ZeffyImportBatch", id));
    }

    public Page<ZeffyImportRow> findRows(UUID batchId, Pageable pageable) {
        return rowRepo.findByBatchIdOrderByRowNumber(batchId, pageable);
    }

    // ── Preview ──────────────────────────────────────────────────────────────

    @Transactional
    public ZeffyImportBatch previewImport(UUID orgId, MultipartFile file) {
        Organization org = orgService.findById(orgId);
        ZeffyImportBatch batch = batchRepo.save(ZeffyImportBatch.builder()
                .org(org)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload")
                .status("previewed")
                .rowCount(0)
                .build());

        List<Map<String, String>> rawRows;
        try {
            rawRows = parseFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded Zeffy file", e);
        }

        Set<String> seenDedupeKeysInBatch = new HashSet<>();
        int rowCount = 0;
        for (Map<String, String> raw : rawRows) {
            int rowNumber = rowCount + 2; // header is row 1; first data row is row 2
            ZeffyImportRow row = ZeffyImportRow.builder()
                    .batch(batch)
                    .org(org)
                    .rowNumber(rowNumber)
                    .outcome("pending_preview")
                    .isNewPerson(false)
                    .isNewMember(false)
                    .build();
            try {
                populateRawFields(row, raw);
                computeOutcome(row, orgId, seenDedupeKeysInBatch);
            } catch (Exception ex) {
                row.setOutcome("error");
                row.setOutcomeDetail(truncate(ex.getMessage()));
            }
            rowRepo.save(row);
            rowCount++;
        }

        batch.setRowCount(rowCount);
        return batchRepo.save(batch);
    }

    /** Dispatches on file extension — Zeffy's real export is an .xlsx spreadsheet; .csv is also
     *  accepted since that's a common "save as" alternative. */
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
                    String value = cellToText(cell, header, formatter);
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

    /** Fails fast on a file that isn't a Zeffy Transactions export (e.g. a Payments-format upload —
     *  recognizable by having "Payment Status" instead of "Id"/"Category") rather than silently
     *  mis-parsing it into a batch full of garbage/error rows. */
    private void validateHeaders(List<String> presentHeaders) {
        Set<String> present = new HashSet<>(presentHeaders);
        if (!present.containsAll(REQUIRED_COLUMNS)) {
            throw new IllegalArgumentException(
                    "This doesn't look like a Zeffy Transactions export — expected columns including "
                            + String.join(", ", REQUIRED_COLUMNS) + ". If you exported a Zeffy Payments "
                            + "report instead, that format is no longer supported — please generate a "
                            + "Transactions export instead.");
        }
    }

    /** Transaction Date / Available On are real Excel date cells — extracted as an actual LocalDate
     *  (ISO-formatted) rather than trusting the cell's display format, which varies. Every other
     *  column is rendered as its displayed text via DataFormatter (mirrors what a human sees, and
     *  handles currency-formatted amount cells like "$1,200.00" — parseAmount strips the symbols). */
    private String cellToText(Cell cell, String header, DataFormatter formatter) {
        if (cell == null) return null;
        String value;
        if (DATE_COLUMNS.contains(header) && cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            value = cell.getLocalDateTimeCellValue().toLocalDate().toString();
        } else {
            value = formatter.formatCellValue(cell).trim();
        }
        return value.isBlank() ? null : value;
    }

    private void populateRawFields(ZeffyImportRow row, Map<String, String> raw) {
        row.setTransactionId(raw.get(COL_TRANSACTION_ID));
        row.setTransactionDate(parseDate(raw.get(COL_TRANSACTION_DATE)));
        row.setAmount(parseAmount(raw.get(COL_AMOUNT)));
        row.setCategory(raw.get(COL_CATEGORY));
        // Eligible amount is purely informational (the tax-deductible portion — blank for
        // non-donation rows like Ticket) and never read by any downstream logic.
        row.setEligibleAmount(raw.get(COL_ELIGIBLE_AMOUNT) != null ? parseAmount(raw.get(COL_ELIGIBLE_AMOUNT)) : null);
        // Available On is purely informational — stored for reference but never read by any
        // membership/tier/finance logic, which uses Transaction Date exclusively (see
        // ZeffyImportRowApplier). Parsed leniently so a placeholder value Zeffy uses for a payout
        // that hasn't happened yet doesn't fail the whole row (same leniency the old Payout Date
        // column needed).
        row.setAvailableDate(parseDateLenient(raw.get(COL_AVAILABLE_DATE)));
        row.setFirstName(raw.get(COL_FIRST_NAME));
        row.setLastName(raw.get(COL_LAST_NAME));
        row.setEmail(raw.get(COL_EMAIL));
        row.setCampaignTitle(raw.get(COL_CAMPAIGN_TITLE));
    }

    /**
     * Sets dedupeKey/isNewPerson/isNewMember and the row's outcome. Order matters: a row that's
     * both a duplicate and unmapped is reported as a duplicate (it won't be applied either way,
     * and duplicate is the more informative reason).
     */
    private void computeOutcome(ZeffyImportRow row, UUID orgId, Set<String> seenDedupeKeysInBatch) {
        if (row.getEmail() == null || row.getEmail().isBlank()) {
            row.setOutcome("error");
            row.setOutcomeDetail("Missing email");
            return;
        }
        if (row.getTransactionId() == null || row.getTransactionId().isBlank()) {
            row.setOutcome("error");
            row.setOutcomeDetail("Missing transaction Id");
            return;
        }

        Optional<Person> existingPerson = personService.findByEmailIfExists(row.getEmail());
        row.setIsNewPerson(existingPerson.isEmpty());
        row.setIsNewMember(existingPerson.isEmpty()
                || !membershipService.hasMembership(existingPerson.get().getId(), orgId));

        // Zeffy's transaction Id is always present, unlike the old Payments export's Tax Receipt #
        // (blank for non-tax-deductible rows) — no composite-key fallback needed anymore.
        String dedupeKey = row.getTransactionId();
        row.setDedupeKey(dedupeKey);

        if (seenDedupeKeysInBatch.contains(dedupeKey)
                || rowRepo.existsByOrgIdAndDedupeKeyAndOutcome(orgId, dedupeKey, "committed")) {
            row.setOutcome("duplicate");
            return;
        }
        seenDedupeKeysInBatch.add(dedupeKey);

        if (!VALID_CATEGORIES.contains(row.getCategory())) {
            row.setOutcome("error");
            row.setOutcomeDetail("Unrecognized category: " + row.getCategory());
            return;
        }

        if (row.getAmount() != null && row.getAmount().signum() < 0) {
            row.setOutcome("error");
            row.setOutcomeDetail("Negative amount — needs manual review");
            return;
        }

        if (row.getCampaignTitle() != null && !row.getCampaignTitle().isBlank()) {
            Optional<ZeffyCampaignMapping> mapping =
                    mappingRepo.findByOrgIdAndCampaignTitleIgnoreCase(orgId, row.getCampaignTitle());
            if (mapping.isEmpty()) {
                row.setOutcome("unmapped_campaign");
                row.setOutcomeDetail(row.getCampaignTitle());
                return;
            }
            row.setFund(mapping.get().getFund());
        }

        row.setOutcome("ready");
    }

    public ZeffyImportSummary getSummary(UUID batchId) {
        ZeffyImportBatch batch = findBatchById(batchId);
        List<ZeffyImportRow> rows = rowRepo.findByBatchIdOrderByRowNumber(batchId);

        Map<String, Long> countsByOutcome = rows.stream()
                .collect(Collectors.groupingBy(ZeffyImportRow::getOutcome, Collectors.counting()));

        List<String> unmappedCampaignTitles = rows.stream()
                .filter(r -> "unmapped_campaign".equals(r.getOutcome()))
                .map(ZeffyImportRow::getCampaignTitle)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        BigDecimal totalAmountReady = rows.stream()
                .filter(r -> "ready".equals(r.getOutcome()) && r.getAmount() != null)
                .map(ZeffyImportRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long newPersonCount = rows.stream().filter(ZeffyImportRow::getIsNewPerson).count();
        long newMemberCount = rows.stream().filter(ZeffyImportRow::getIsNewMember).count();

        return new ZeffyImportSummary(
                batch.getId(),
                rows.size(),
                countsByOutcome.getOrDefault("ready", 0L),
                countsByOutcome.getOrDefault("duplicate", 0L),
                countsByOutcome.getOrDefault("skipped_status", 0L),
                countsByOutcome.getOrDefault("unmapped_campaign", 0L),
                countsByOutcome.getOrDefault("error", 0L),
                countsByOutcome.getOrDefault("committed", 0L),
                newPersonCount,
                newMemberCount,
                totalAmountReady,
                unmappedCampaignTitles);
    }

    // ── Campaign mappings ────────────────────────────────────────────────────

    public List<ZeffyCampaignMapping> findMappingsByOrg(UUID orgId) {
        return mappingRepo.findByOrgId(orgId);
    }

    @Transactional
    public List<ZeffyCampaignMapping> upsertCampaignMappings(UUID orgId, List<CampaignMappingRequest> requests) {
        Organization org = orgService.findById(orgId);
        List<ZeffyCampaignMapping> saved = new ArrayList<>();
        for (CampaignMappingRequest req : requests) {
            Fund fund = financeService.findFundById(req.fundId());
            ZeffyCampaignMapping mapping = mappingRepo
                    .findByOrgIdAndCampaignTitleIgnoreCase(orgId, req.campaignTitle())
                    .orElseGet(() -> ZeffyCampaignMapping.builder()
                            .org(org)
                            .campaignTitle(req.campaignTitle())
                            .build());
            mapping.setFund(fund);
            mapping.setIsMembershipPayment(Boolean.TRUE.equals(req.isMembershipPayment()));
            saved.add(mappingRepo.save(mapping));
        }
        return saved;
    }

    @Transactional
    public void deleteMapping(UUID id) {
        if (!mappingRepo.existsById(id)) throw new ResourceNotFoundException("ZeffyCampaignMapping", id);
        mappingRepo.deleteById(id);
    }

    // ── Commit ───────────────────────────────────────────────────────────────

    /**
     * Deliberately NOT a single wrapping transaction (overrides the class-level readOnly default
     * with NOT_SUPPORTED, rather than plain @Transactional): rowApplier.applyRow is a different
     * bean, so it only gets its own fresh transaction per row if this method has no ambient
     * transaction of its own for it to join. Confirmed the hard way — an earlier version of this
     * method was @Transactional, which meant applyRow's default REQUIRED propagation joined it,
     * so one row's exception marked the *whole batch's* transaction rollback-only, throwing
     * UnexpectedRollbackException instead of isolating the failure to that row.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ZeffyImportCommitResult commitImport(UUID orgId, UUID batchId) {
        ZeffyImportBatch batch = findBatchById(batchId);
        if (!batch.getOrg().getId().equals(orgId)) {
            throw new IllegalArgumentException("Batch does not belong to this organization");
        }
        if ("committed".equals(batch.getStatus())) {
            throw new IllegalArgumentException("This import batch has already been committed");
        }

        membershipService.ensureZeffyTierTypesSeeded(orgId);
        financeService.findAccountsByOrg(orgId, PageRequest.of(0, 1)); // triggers lazy chart-of-accounts seed
        // Donation rows earn membership tier credit and post to Donation Income; Ticket rows (event/
        // service ticket purchases, not membership contributions) post to Service Fees Income instead
        // and skip the Member/MemberPayment/tier pipeline entirely — see ZeffyImportRowApplier.
        Account donationAccount = financeService.findAccountByNumber(orgId, "4010");
        Account ticketAccount = financeService.findAccountByNumber(orgId, "4030");
        // Zeffy holds donations and pays out to the real bank in periodic lump sums — post to its
        // clearing account rather than Checking directly, so Checking only grows when the actual
        // payout lands (see FinanceService#recordTransfer / DEFAULT_ACCOUNTS). findOrCreateAccountByNumber
        // retrofits this org's already-established chart of accounts the same way the Stripe
        // integration's fee account (5320) is retrofitted.
        Account depositAccount = financeService.findOrCreateAccountByNumber(orgId, "1020", "Undeposited Funds – Zeffy", "asset");

        List<ZeffyImportRow> rows = rowRepo.findByBatchIdOrderByRowNumber(batchId);
        int committed = 0;
        int failed = 0;
        int stillUnmapped = 0;
        for (ZeffyImportRow row : rows) {
            if (!"ready".equals(row.getOutcome()) && !"unmapped_campaign".equals(row.getOutcome())) continue;
            try {
                rowApplier.applyRow(row.getId(), depositAccount.getId(), donationAccount.getId(), ticketAccount.getId());
                String outcomeAfter = rowRepo.findById(row.getId())
                        .map(ZeffyImportRow::getOutcome)
                        .orElse("error");
                if ("committed".equals(outcomeAfter)) {
                    committed++;
                } else if ("unmapped_campaign".equals(outcomeAfter)) {
                    stillUnmapped++;
                }
            } catch (Exception ex) {
                rowApplier.markRowError(row.getId(), ex.getMessage());
                failed++;
            }
        }

        batch.setStatus("committed");
        batch.setCommittedAt(OffsetDateTime.now());
        batchRepo.save(batch);

        return new ZeffyImportCommitResult(batchId, committed, failed, stillUnmapped);
    }

    /**
     * One-time backfill: finds every already-committed Ticket-category row whose campaign has
     * since been flagged {@code isMembershipPayment=true} (a campaign mapping change doesn't
     * retroactively touch already-committed rows on its own) and applies the missing Member/
     * MemberPayment/tier + income-reclassification via {@link ZeffyImportRowApplier#reprocessAsMembership}.
     * Idempotent — safe to call again after a partial failure, or if run a second time with no new
     * rows to process. Same NOT_SUPPORTED reasoning as {@link #commitImport} — each row gets its
     * own transaction so one bad row can't poison the rest.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReprocessMembershipResult reprocessMembershipRows(UUID orgId) {
        financeService.findAccountsByOrg(orgId, PageRequest.of(0, 1)); // triggers lazy chart-of-accounts seed
        Account donationAccount = financeService.findAccountByNumber(orgId, "4010");
        Account ticketAccount = financeService.findAccountByNumber(orgId, "4030");

        List<ZeffyImportRow> rows = rowRepo.findCommittedTicketRowsNeedingMembershipReprocess(orgId);
        int rowsProcessed = 0;
        int membersCreated = 0;
        for (ZeffyImportRow row : rows) {
            boolean hadMembershipBefore = row.getPerson() != null
                    && membershipService.hasMembership(row.getPerson().getId(), orgId);
            rowApplier.reprocessAsMembership(row.getId(), donationAccount.getId(), ticketAccount.getId());
            rowsProcessed++;
            if (!hadMembershipBefore) membersCreated++;
        }

        return new ReprocessMembershipResult(rowsProcessed, membersCreated);
    }

    // ── Parsing helpers ──────────────────────────────────────────────────────

    /** A blank Amount is treated the same as an explicit 0 — both import as Follower with
     *  no Finance posting (see ZeffyImportRowApplier), rather than failing the row at commit time. */
    private BigDecimal parseAmount(String raw) {
        if (raw == null) return BigDecimal.ZERO;
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Amount must be a number, got: " + raw);
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException("Unrecognized date format: " + raw);
    }

    /** Same as parseDate, but returns null instead of throwing — for columns whose value is
     *  never actually used, so a garbage/placeholder value shouldn't fail the whole row. */
    private LocalDate parseDateLenient(String raw) {
        try {
            return parseDate(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String truncate(String detail) {
        if (detail == null) return null;
        return detail.length() > OUTCOME_DETAIL_MAX_LENGTH ? detail.substring(0, OUTCOME_DETAIL_MAX_LENGTH) : detail;
    }
}
