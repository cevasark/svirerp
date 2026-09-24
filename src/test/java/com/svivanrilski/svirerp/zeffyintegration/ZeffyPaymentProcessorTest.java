package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.finance.RecordIncomeRequest;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ZeffyPaymentProcessorTest {

    private ZeffyWebhookEventRepository events;
    private ZeffyPaymentRepository payments;
    private ZeffyCampaignRepository campaigns;
    private PersonService people;
    private MembershipService memberships;
    private FinanceService finance;
    private ZeffyPaymentProcessor processor;

    @BeforeEach
    void setUp() {
        events = mock(ZeffyWebhookEventRepository.class);
        payments = mock(ZeffyPaymentRepository.class);
        campaigns = mock(ZeffyCampaignRepository.class);
        people = mock(PersonService.class);
        memberships = mock(MembershipService.class);
        finance = mock(FinanceService.class);
        processor = new ZeffyPaymentProcessor(events, payments, campaigns, people,
                memberships, finance, new ZeffyPaymentPayload(new ObjectMapper()));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void unmappedPaymentCreatesNoDomainRecords() {
        ZeffyWebhookEvent event = event(paymentPayload(5000));
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.empty());
        when(campaigns.findWithMappingByZeffyCampaignId("campaign-1")).thenReturn(Optional.empty());

        processor.applyEvent(event.getId());

        assertThat(event.getStatus()).isEqualTo("NEEDS_MAPPING");
        assertThat(event.getZeffyPayment().getProcessingStatus()).isEqualTo("NEEDS_MAPPING");
        verifyNoInteractions(people, memberships, finance);
    }

    @Test
    void appliesMappedPaymentOnceUsingOriginalChicagoPaymentDate() {
        ZeffyWebhookEvent event = event(paymentPayload(15000));
        Fund fund = Fund.builder().id(UUID.randomUUID()).fundName("General").build();
        Account revenue = Account.builder().id(UUID.randomUUID()).accountType("revenue").build();
        ZeffyCampaign campaign = ZeffyCampaign.builder()
                .zeffyCampaignId("campaign-1")
                .title("Annual membership")
                .mappingConfirmed(true)
                .processingAction("APPLY")
                .fund(fund)
                .categoryAccount(revenue)
                .grantsMembershipCredit(false)
                .build();
        Person person = Person.builder().id(UUID.randomUUID()).firstName("Jane")
                .lastName("Doe").email("jane@example.com").build();
        Account deposit = Account.builder().id(UUID.randomUUID()).accountType("asset").build();
        JournalEntry journal = JournalEntry.builder().id(UUID.randomUUID()).build();

        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.empty());
        when(campaigns.findWithMappingByZeffyCampaignId("campaign-1")).thenReturn(Optional.of(campaign));
        when(people.findByNormalizedEmail("jane@example.com")).thenReturn(List.of(person));
        when(people.fillBlankFields(eq(person.getId()), any())).thenReturn(person);
        when(finance.findOrCreateAccountByNumber("1020", "Undeposited Funds – Zeffy", "asset"))
                .thenReturn(deposit);
        when(finance.recordIncome(any())).thenReturn(journal);

        processor.applyEvent(event.getId());

        ArgumentCaptor<RecordIncomeRequest> income = ArgumentCaptor.forClass(RecordIncomeRequest.class);
        verify(finance).recordIncome(income.capture());
        assertThat(income.getValue().entryDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(income.getValue().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(income.getValue().fundId()).isEqualTo(fund.getId());
        assertThat(income.getValue().categoryAccountId()).isEqualTo(revenue.getId());
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        assertThat(event.getZeffyPayment().getJournalEntry()).isSameAs(journal);
        assertThat(event.getZeffyPayment().getPerson()).isSameAs(person);
    }

    @Test
    void alreadyAppliedPaymentIsAnIdempotentNoOp() {
        ZeffyWebhookEvent event = event(paymentPayload(5000));
        ZeffyPayment payment = ZeffyPayment.builder()
                .zeffyPaymentId("pay-1")
                .processingStatus("PROCESSED")
                .latestPayload("{}")
                .appliedAt(OffsetDateTime.now())
                .build();
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.of(payment));

        processor.applyEvent(event.getId());

        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        verifyNoInteractions(campaigns, people, memberships, finance);
    }

    @Test
    void zeroDollarMembershipPaymentCreatesCreditWithoutJournalEntry() {
        ZeffyWebhookEvent event = event(paymentPayload(0));
        Fund fund = Fund.builder().id(UUID.randomUUID()).fundName("General").build();
        Account revenue = Account.builder().id(UUID.randomUUID()).accountType("revenue").build();
        ZeffyCampaign campaign = ZeffyCampaign.builder()
                .zeffyCampaignId("campaign-1").title("Free membership")
                .mappingConfirmed(true).processingAction("APPLY")
                .fund(fund).categoryAccount(revenue).grantsMembershipCredit(true).build();
        Person person = Person.builder().id(UUID.randomUUID()).firstName("Jane")
                .lastName("Doe").email("jane@example.com").build();
        Member member = Member.builder().id(UUID.randomUUID()).person(person).build();

        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.empty());
        when(campaigns.findWithMappingByZeffyCampaignId("campaign-1")).thenReturn(Optional.of(campaign));
        when(people.findByNormalizedEmail("jane@example.com")).thenReturn(List.of(person));
        when(people.fillBlankFields(eq(person.getId()), any())).thenReturn(person);
        when(memberships.findOrCreateFollowerMember(eq(person.getId()), any())).thenReturn(member);
        MemberPayment contribution = MemberPayment.builder().id(UUID.randomUUID()).member(member)
                .amount(BigDecimal.ZERO).paymentDate(LocalDate.of(2025, 12, 31)).build();
        when(memberships.upsertZeffyPayment(eq(member.getId()), any(BigDecimal.class), any(), any(),
                eq("pay-1"), eq("Free membership")))
                .thenReturn(new MembershipService.ExternalPaymentUpsert(contribution, true));
        when(memberships.recomputeTier(member.getId())).thenReturn(member);

        processor.applyEvent(event.getId());

        assertThat(event.getZeffyPayment().getMemberPayment()).isSameAs(contribution);
        verify(finance, never()).recordIncome(any());
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    void ambiguousEmailNeedsReviewWithoutBusinessWrites() {
        ZeffyWebhookEvent event = event(paymentPayload(5000));
        ZeffyCampaign campaign = ZeffyCampaign.builder()
                .zeffyCampaignId("campaign-1").title("Donation")
                .mappingConfirmed(true).processingAction("APPLY")
                .fund(Fund.builder().id(UUID.randomUUID()).build())
                .categoryAccount(Account.builder().id(UUID.randomUUID()).build())
                .build();
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.empty());
        when(campaigns.findWithMappingByZeffyCampaignId("campaign-1")).thenReturn(Optional.of(campaign));
        when(people.findByNormalizedEmail("jane@example.com")).thenReturn(List.of(
                Person.builder().id(UUID.randomUUID()).build(),
                Person.builder().id(UUID.randomUUID()).build()));

        processor.applyEvent(event.getId());

        assertThat(event.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(event.getErrorSummary()).contains("more than one");
        verify(people, never()).create(any());
        verifyNoInteractions(memberships, finance);
    }

    @Test
    void previewEvaluatesEligibilityWithoutCreatingBusinessRecords() throws Exception {
        var data = new ObjectMapper().readTree(paymentPayload(5000)).get("data");
        ZeffyCampaign campaign = ZeffyCampaign.builder()
                .zeffyCampaignId("campaign-1").title("Donation")
                .mappingConfirmed(true).processingAction("APPLY")
                .fund(Fund.builder().id(UUID.randomUUID()).build())
                .categoryAccount(Account.builder().id(UUID.randomUUID()).build())
                .build();
        when(payments.findByZeffyPaymentIdForUpdate("pay-1")).thenReturn(Optional.empty());
        when(campaigns.findWithMappingByZeffyCampaignId("campaign-1")).thenReturn(Optional.of(campaign));
        when(people.findByNormalizedEmail("jane@example.com")).thenReturn(List.of());

        ZeffyPaymentProcessor.ProcessingResult result = processor.previewApiPayment(
                data, OffsetDateTime.parse("2026-01-02T00:00:00Z"));

        assertThat(result.outcome()).isEqualTo("ELIGIBLE");
        assertThat(result.payloadSha256()).hasSize(64);
        verify(people, never()).create(any());
        verify(people, never()).fillBlankFields(any(), any());
        verifyNoInteractions(memberships, finance);
    }

    @Test
    void paymentFingerprintDoesNotDependOnJsonObjectFieldOrder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(processor.fingerprint(mapper.readTree("{\"id\":\"pay-1\",\"amount\":500}")))
                .isEqualTo(processor.fingerprint(mapper.readTree("{\"amount\":500,\"id\":\"pay-1\"}")));
    }

    private ZeffyWebhookEvent event(String payload) {
        return ZeffyWebhookEvent.builder()
                .id(UUID.randomUUID())
                .eventType("payment.completed")
                .schemaVersion(1)
                .status("RECEIVED")
                .processingAttemptCount(0)
                .dispatchedAt(OffsetDateTime.parse("2026-01-01T03:01:00Z"))
                .rawPayload(payload)
                .build();
    }

    private String paymentPayload(long amount) {
        return """
                {"id":"2f4754db-53ac-4abe-bb23-b7ac11f2ac36","type":"payment.completed","version":1,
                 "dispatchedAt":"2026-01-01T03:01:00Z","data":{"id":"pay-1","object":"payment",
                 "created":1767236400,"amount":%d,"eligible_amount":%d,"currency":"usd",
                 "status":"succeeded","type":"online","refund_status":"none","refunds":[],
                 "dispute":null,"description":"Annual membership","contact":"contact-1",
                 "buyer":{"email":"Jane@Example.com","first_name":"Jane","last_name":"Doe",
                 "is_corporate":false,"company_name":null,"address":{"line1":"1 Main St",
                 "city":"Chicago","state":"IL","postal_code":"60601","country":"US"}},
                 "campaign_type":"donation_form","campaign_id":"campaign-1","campaign_category":"membership",
                 "buyer_questions":[],"items":[],"occurrence_id":null,"receipt_url":null,
                 "recurring":null,"fund":null,"metadata":{}}}
                """.formatted(amount, amount);
    }
}
