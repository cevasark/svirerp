package com.svivanrilski.svirerp.zeffyimport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.organization.OrganizationService;
import com.svivanrilski.svirerp.person.PersonService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers previewImport's outcome computation against the Zeffy Transactions export format —
 * the dedupe key is now always the transaction Id (no more tax-receipt-or-composite fallback),
 * and two new guards (unrecognized category, negative amount) reuse the existing "error" outcome
 * rather than adding new enum values. All collaborators are mocked; this doesn't stand up a
 * Spring context or a real DB — only the real CSV-parsing code runs.
 */
@ExtendWith(MockitoExtension.class)
class ZeffyImportServiceTest {

    private static final String HEADER = String.join(",",
            "Id", "Amount", "Type", "Category", "First Name", "Last Name", "Email",
            "Creation Date (America/Chicago)", "Available on (America/Chicago)",
            "Eligible amount", "Campaign", "Fund");

    @Mock private ZeffyImportBatchRepository batchRepo;
    @Mock private ZeffyImportRowRepository rowRepo;
    @Mock private ZeffyCampaignMappingRepository mappingRepo;
    @Mock private ZeffyImportRowApplier rowApplier;
    @Mock private OrganizationService orgService;
    @Mock private PersonService personService;
    @Mock private MembershipService membershipService;
    @Mock private FinanceService financeService;

    @InjectMocks
    private ZeffyImportService service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);
        lenient().when(orgService.findById(orgId)).thenReturn(org);
        lenient().when(batchRepo.save(any(ZeffyImportBatch.class))).thenAnswer(inv -> {
            ZeffyImportBatch batch = inv.getArgument(0);
            if (batch.getId() == null) batch.setId(UUID.randomUUID());
            return batch;
        });
        lenient().when(rowRepo.save(any(ZeffyImportRow.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(personService.findByEmailIfExists(anyString())).thenReturn(Optional.empty());
        lenient().when(mappingRepo.findByOrgIdAndCampaignTitleIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
    }

    private MockMultipartFile csvOf(String... dataRows) {
        String content = HEADER + "\n" + String.join("\n", dataRows) + "\n";
        return new MockMultipartFile("file", "transactions.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private String row(String id, String amount, String category, String email) {
        return String.join(",", id, amount, "", category, "John", "Doe", email,
                "7/1/2026", "7/2/2026", "", "", "");
    }

    @Test
    void ready_row_gets_dedupeKey_equal_to_transactionId() {
        ZeffyImportBatch batch = service.previewImport(orgId, csvOf(row("txn_1", "100", "Donation", "a@example.com")));

        assertThat(batch.getRowCount()).isEqualTo(1);
    }

    @Test
    void duplicate_transactionId_within_same_batch_is_flagged() {
        service.previewImport(orgId, csvOf(
                row("txn_1", "100", "Donation", "a@example.com"),
                row("txn_1", "100", "Donation", "a@example.com")));

        // Both rows saved; the second one must have outcome "duplicate" — captured via rowRepo.save.
        var captor = org.mockito.ArgumentCaptor.forClass(ZeffyImportRow.class);
        org.mockito.Mockito.verify(rowRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        List<ZeffyImportRow> saved = captor.getAllValues();
        assertThat(saved.get(0).getOutcome()).isEqualTo("ready");
        assertThat(saved.get(1).getOutcome()).isEqualTo("duplicate");
        assertThat(saved.get(0).getDedupeKey()).isEqualTo("txn_1");
    }

    @Test
    void previously_committed_transactionId_is_flagged_duplicate() {
        when(rowRepo.existsByOrgIdAndDedupeKeyAndOutcome(orgId, "txn_1", "committed")).thenReturn(true);

        service.previewImport(orgId, csvOf(row("txn_1", "100", "Donation", "a@example.com")));

        var captor = org.mockito.ArgumentCaptor.forClass(ZeffyImportRow.class);
        org.mockito.Mockito.verify(rowRepo).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("duplicate");
    }

    @Test
    void unrecognized_category_is_an_error() {
        service.previewImport(orgId, csvOf(row("txn_1", "100", "Refund", "a@example.com")));

        var captor = org.mockito.ArgumentCaptor.forClass(ZeffyImportRow.class);
        org.mockito.Mockito.verify(rowRepo).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("error");
        assertThat(captor.getValue().getOutcomeDetail()).contains("Refund");
    }

    @Test
    void negative_amount_is_an_error() {
        service.previewImport(orgId, csvOf(row("txn_1", "-10", "Donation", "a@example.com")));

        var captor = org.mockito.ArgumentCaptor.forClass(ZeffyImportRow.class);
        org.mockito.Mockito.verify(rowRepo).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("error");
    }

    @Test
    void missing_transactionId_is_an_error() {
        service.previewImport(orgId, csvOf(row("", "10", "Donation", "a@example.com")));

        var captor = org.mockito.ArgumentCaptor.forClass(ZeffyImportRow.class);
        org.mockito.Mockito.verify(rowRepo).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("error");
        assertThat(captor.getValue().getOutcomeDetail()).contains("transaction Id");
    }

    @Test
    void payments_format_upload_is_rejected_with_a_clear_message() {
        String paymentsHeader = String.join(",", "Payment Date (America/Chicago)", "Payment Time (America/Chicago)",
                "Total Amount", "Payment Status", "Payout Date", "First Name", "Last Name", "Email",
                "Address", "City", "Postal Code", "State", "Country", "Tax Receipt #", "Tax Receipt URL",
                "Campaign Title");
        MockMultipartFile file = new MockMultipartFile("file", "payments.csv", "text/csv",
                (paymentsHeader + "\n").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.previewImport(orgId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transactions export");
    }

    @Test
    void reprocessMembershipRows_delegatesPerRow_andCountsOnlyGenuinelyNewMembers() {
        com.svivanrilski.svirerp.finance.Account donationAccount = new com.svivanrilski.svirerp.finance.Account();
        donationAccount.setId(UUID.randomUUID());
        com.svivanrilski.svirerp.finance.Account ticketAccount = new com.svivanrilski.svirerp.finance.Account();
        ticketAccount.setId(UUID.randomUUID());
        when(financeService.findAccountByNumber(orgId, "4010")).thenReturn(donationAccount);
        when(financeService.findAccountByNumber(orgId, "4030")).thenReturn(ticketAccount);

        com.svivanrilski.svirerp.person.Person newPersonAlready = com.svivanrilski.svirerp.person.Person.builder()
                .id(UUID.randomUUID()).build();
        com.svivanrilski.svirerp.person.Person alreadyMemberPerson = com.svivanrilski.svirerp.person.Person.builder()
                .id(UUID.randomUUID()).build();
        ZeffyImportRow row1 = ZeffyImportRow.builder().id(UUID.randomUUID()).person(newPersonAlready).build();
        ZeffyImportRow row2 = ZeffyImportRow.builder().id(UUID.randomUUID()).person(alreadyMemberPerson).build();
        when(rowRepo.findCommittedTicketRowsNeedingMembershipReprocess(orgId)).thenReturn(List.of(row1, row2));

        when(membershipService.hasMembership(newPersonAlready.getId(), orgId)).thenReturn(false);
        when(membershipService.hasMembership(alreadyMemberPerson.getId(), orgId)).thenReturn(true);

        ZeffyImportService.ReprocessMembershipResult result = service.reprocessMembershipRows(orgId);

        assertThat(result.rowsProcessed()).isEqualTo(2);
        assertThat(result.membersCreated()).isEqualTo(1);
        org.mockito.Mockito.verify(rowApplier)
                .reprocessAsMembership(row1.getId(), donationAccount.getId(), ticketAccount.getId());
        org.mockito.Mockito.verify(rowApplier)
                .reprocessAsMembership(row2.getId(), donationAccount.getId(), ticketAccount.getId());
    }
}
