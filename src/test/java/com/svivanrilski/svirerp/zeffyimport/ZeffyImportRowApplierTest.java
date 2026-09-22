package com.svivanrilski.svirerp.zeffyimport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.finance.RecordIncomeRequest;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.membership.MemberPaymentRepository;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers applyRow's Donation-vs-Ticket branch — a Ticket-category row isn't a membership
 * contribution, so it must skip Member/MemberPayment/tier-recompute entirely and post to the
 * ticket account instead of the donation account. All collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class ZeffyImportRowApplierTest {

    @Mock private ZeffyImportRowRepository rowRepo;
    @Mock private ZeffyCampaignMappingRepository mappingRepo;
    @Mock private PersonService personService;
    @Mock private MembershipService membershipService;
    @Mock private MemberPaymentRepository memberPaymentRepo;
    @Mock private FinanceService financeService;

    @InjectMocks
    private ZeffyImportRowApplier applier;

    private UUID orgId;
    private UUID depositAccountId;
    private UUID donationAccountId;
    private UUID ticketAccountId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        depositAccountId = UUID.randomUUID();
        donationAccountId = UUID.randomUUID();
        ticketAccountId = UUID.randomUUID();

        // Not every test below exercises applyRow()/reprocessAsMembership() the same way — some
        // (e.g. the early-return no-op cases) never reach these calls at all.
        lenient().when(rowRepo.save(any(ZeffyImportRow.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry entry = new JournalEntry();
        entry.setId(UUID.randomUUID());
        lenient().when(financeService.recordIncome(any(RecordIncomeRequest.class))).thenReturn(entry);
    }

    private ZeffyImportRow rowOf(String category) {
        Organization org = new Organization();
        org.setId(orgId);
        return ZeffyImportRow.builder()
                .id(UUID.randomUUID())
                .org(org)
                .rowNumber(2)
                .transactionId("txn_1")
                .amount(new BigDecimal("75.00"))
                .category(category)
                .email("donor@example.com")
                .firstName("Jane")
                .lastName("Donor")
                .transactionDate(LocalDate.of(2026, 7, 1))
                .outcome("ready")
                .build();
    }

    @Test
    void donation_row_creates_member_and_posts_to_donation_account() {
        ZeffyImportRow row = rowOf("Donation");
        when(rowRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(personService.findByEmailIfExists("donor@example.com")).thenReturn(Optional.empty());
        Person person = Person.builder().id(UUID.randomUUID()).build();
        when(personService.create(any(Person.class))).thenReturn(person);
        when(membershipService.hasMembership(person.getId(), orgId)).thenReturn(false);
        Member member = new Member();
        member.setId(UUID.randomUUID());
        when(membershipService.findOrCreateFollowerMember(person.getId(), orgId, row.getTransactionDate()))
                .thenReturn(member);
        when(memberPaymentRepo.save(any(MemberPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipService.recomputeTier(member.getId())).thenReturn(member);

        applier.applyRow(row.getId(), depositAccountId, donationAccountId, ticketAccountId);

        assertThat(row.getOutcome()).isEqualTo("committed");
        assertThat(row.getMember()).isNotNull();
        assertThat(row.getMemberPayment()).isNotNull();
        verify(membershipService).recomputeTier(member.getId());

        ArgumentCaptor<RecordIncomeRequest> captor = ArgumentCaptor.forClass(RecordIncomeRequest.class);
        verify(financeService).recordIncome(captor.capture());
        assertThat(captor.getValue().categoryAccountId()).isEqualTo(donationAccountId);
    }

    @Test
    void ticket_row_skips_membership_pipeline_and_posts_to_ticket_account() {
        ZeffyImportRow row = rowOf("Ticket");
        when(rowRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(personService.findByEmailIfExists("donor@example.com")).thenReturn(Optional.empty());
        Person person = Person.builder().id(UUID.randomUUID()).build();
        when(personService.create(any(Person.class))).thenReturn(person);

        applier.applyRow(row.getId(), depositAccountId, donationAccountId, ticketAccountId);

        assertThat(row.getOutcome()).isEqualTo("committed");
        assertThat(row.getMember()).isNull();
        assertThat(row.getMemberPayment()).isNull();
        verify(membershipService, never()).findOrCreateFollowerMember(any(), any(), any());
        verify(membershipService, never()).recomputeTier(any());
        verify(memberPaymentRepo, never()).save(any());

        ArgumentCaptor<RecordIncomeRequest> captor = ArgumentCaptor.forClass(RecordIncomeRequest.class);
        verify(financeService).recordIncome(captor.capture());
        assertThat(captor.getValue().categoryAccountId()).isEqualTo(ticketAccountId);
    }

    @Test
    void ticket_row_with_membership_flagged_campaign_still_goes_through_membership_pipeline() {
        // Zeffy implements fixed-price membership registration as a Ticket-category product — a
        // campaign explicitly flagged isMembershipPayment=true overrides the raw category.
        ZeffyImportRow row = rowOf("Ticket");
        row.setCampaignTitle("Become a member of \"Sv. Ivan Rilski\" Chicago");
        when(rowRepo.findById(row.getId())).thenReturn(Optional.of(row));

        Fund fund = Fund.builder().id(UUID.randomUUID()).build();
        ZeffyCampaignMapping mapping = ZeffyCampaignMapping.builder()
                .id(UUID.randomUUID()).fund(fund).isMembershipPayment(true).build();
        when(mappingRepo.findByOrgIdAndCampaignTitleIgnoreCase(orgId, row.getCampaignTitle()))
                .thenReturn(Optional.of(mapping));

        when(personService.findByEmailIfExists("donor@example.com")).thenReturn(Optional.empty());
        Person person = Person.builder().id(UUID.randomUUID()).build();
        when(personService.create(any(Person.class))).thenReturn(person);
        when(membershipService.hasMembership(person.getId(), orgId)).thenReturn(false);
        Member member = new Member();
        member.setId(UUID.randomUUID());
        when(membershipService.findOrCreateFollowerMember(person.getId(), orgId, row.getTransactionDate()))
                .thenReturn(member);
        when(memberPaymentRepo.save(any(MemberPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipService.recomputeTier(member.getId())).thenReturn(member);

        applier.applyRow(row.getId(), depositAccountId, donationAccountId, ticketAccountId);

        assertThat(row.getMember()).isNotNull();
        assertThat(row.getMemberPayment()).isNotNull();
        verify(membershipService).recomputeTier(member.getId());

        ArgumentCaptor<RecordIncomeRequest> captor = ArgumentCaptor.forClass(RecordIncomeRequest.class);
        verify(financeService).recordIncome(captor.capture());
        assertThat(captor.getValue().categoryAccountId()).isEqualTo(donationAccountId);
    }

    @Test
    void reprocessAsMembership_backfillsMemberAndReclassifiesIncome() {
        ZeffyImportRow row = rowOf("Ticket");
        row.setOutcome("committed");
        row.setCampaignTitle("Become a member of \"Sv. Ivan Rilski\" Chicago");
        Person person = Person.builder().id(UUID.randomUUID()).build();
        row.setPerson(person);
        when(rowRepo.findById(row.getId())).thenReturn(Optional.of(row));

        Member member = new Member();
        member.setId(UUID.randomUUID());
        when(membershipService.findOrCreateFollowerMember(person.getId(), orgId, row.getTransactionDate()))
                .thenReturn(member);
        when(memberPaymentRepo.save(any(MemberPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipService.recomputeTier(member.getId())).thenReturn(member);

        applier.reprocessAsMembership(row.getId(), donationAccountId, ticketAccountId);

        assertThat(row.getMember()).isEqualTo(member);
        assertThat(row.getMemberPayment()).isNotNull();
        assertThat(row.getMemberPayment().getTransactionRef()).isEqualTo("txn_1");
        verify(membershipService).recomputeTier(member.getId());
        verify(financeService).reclassifyIncome(eq(orgId), eq(row.getTransactionDate()), eq(row.getAmount()),
                anyString(), eq(ticketAccountId), eq(donationAccountId));
    }

    @Test
    void reprocessAsMembership_isNoOp_whenRowAlreadyHasMember() {
        ZeffyImportRow row = rowOf("Ticket");
        row.setOutcome("committed");
        row.setMember(new Member()); // already backfilled
        when(rowRepo.findById(row.getId())).thenReturn(Optional.of(row));

        applier.reprocessAsMembership(row.getId(), donationAccountId, ticketAccountId);

        verify(membershipService, never()).findOrCreateFollowerMember(any(), any(), any());
        verify(financeService, never()).reclassifyIncome(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void reprocessAsMembership_isNoOp_whenRowNotCommitted() {
        ZeffyImportRow row = rowOf("Ticket");
        row.setOutcome("ready");
        when(rowRepo.findById(row.getId())).thenReturn(Optional.of(row));

        applier.reprocessAsMembership(row.getId(), donationAccountId, ticketAccountId);

        verify(membershipService, never()).findOrCreateFollowerMember(any(), any(), any());
    }
}
