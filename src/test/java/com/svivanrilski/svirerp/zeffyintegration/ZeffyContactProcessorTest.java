package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.membership.MembershipType;
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

class ZeffyContactProcessorTest {

    private ZeffyContactRepository contacts;
    private ZeffyWebhookEventRepository events;
    private ZeffyPaymentRepository payments;
    private PersonService people;
    private MembershipService memberships;
    private ZeffyContactProcessor processor;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        contacts = mock(ZeffyContactRepository.class);
        events = mock(ZeffyWebhookEventRepository.class);
        payments = mock(ZeffyPaymentRepository.class);
        people = mock(PersonService.class);
        memberships = mock(MembershipService.class);
        processor = new ZeffyContactProcessor(contacts, events, payments,
                new ZeffyContactPayload(mapper), people, memberships);
        when(contacts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.findByContactId(any())).thenReturn(List.of());
    }

    @Test
    void contactWithoutContributionsCreatesActiveFollowerBaseline() throws Exception {
        JsonNode remote = contactPayload("contact-1", 0, 0);
        Person created = Person.builder().id(UUID.randomUUID()).firstName("Jane")
                .lastName("Doe").email("jane@example.com").build();
        Member follower = Member.builder().id(UUID.randomUUID()).person(created)
                .membershipType(MembershipType.builder().name("Follower").build())
                .status("active").build();
        when(contacts.findByZeffyContactIdForUpdate("contact-1")).thenReturn(Optional.empty());
        when(people.findByNormalizedEmail("jane@example.com")).thenReturn(List.of());
        when(people.create(any())).thenReturn(created);
        when(memberships.findOrCreateFollowerMember(created.getId(), LocalDate.of(2024, 1, 15)))
                .thenReturn(follower);

        ZeffyContactProcessor.ApplyResult result = processor.applyFromSync(remote);

        ArgumentCaptor<Person> person = ArgumentCaptor.forClass(Person.class);
        verify(people).create(person.capture());
        assertThat(person.getValue().getPhone()).isEqualTo("+13125550100");
        verify(memberships).findOrCreateFollowerMember(created.getId(), LocalDate.of(2024, 1, 15));
        ArgumentCaptor<ZeffyContact> stored = ArgumentCaptor.forClass(ZeffyContact.class);
        verify(contacts).save(stored.capture());
        assertThat(stored.getValue().getProcessingStatus()).isEqualTo("PROCESSED");
        assertThat(stored.getValue().getTotalContribution()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.inserted()).isTrue();
        assertThat(result.needsReview()).isFalse();
    }

    @Test
    void existingBenefactorLinkIsPreservedWhenContactChanges() throws Exception {
        Person person = Person.builder().id(UUID.randomUUID()).firstName("Jane")
                .lastName("Doe").email("local@example.com").build();
        ZeffyContact existing = ZeffyContact.builder().id(UUID.randomUUID())
                .zeffyContactId("contact-1").person(person).processingStatus("PROCESSED")
                .latestPayloadSha256("old").firstSeenSource("API_SYNC").build();
        Member benefactor = Member.builder().id(UUID.randomUUID()).person(person)
                .membershipType(MembershipType.builder().name("Benefactor").build())
                .status("active").build();
        when(contacts.findByZeffyContactIdForUpdate("contact-1")).thenReturn(Optional.of(existing));
        when(people.findByNormalizedEmail("jane@example.com")).thenReturn(List.of());
        when(people.fillBlankContactFields(eq(person.getId()), any())).thenReturn(person);
        when(memberships.findOrCreateFollowerMember(eq(person.getId()), any())).thenReturn(benefactor);

        processor.applyFromSync(contactPayload("contact-1", 25000, 2));

        verify(memberships).findOrCreateFollowerMember(person.getId(), LocalDate.of(2024, 1, 15));
        verify(memberships, never()).recomputeTier(any());
        assertThat(existing.getPerson()).isSameAs(person);
        assertThat(existing.getProcessingStatus()).isEqualTo("PROCESSED");
        assertThat(existing.getOutcomeReason()).isNull();
    }

    @Test
    void ambiguousEmailIsHeldForReviewWithoutCreatingFollower() throws Exception {
        when(contacts.findByZeffyContactIdForUpdate("contact-1")).thenReturn(Optional.empty());
        when(people.findByNormalizedEmail("jane@example.com")).thenReturn(List.of(
                Person.builder().id(UUID.randomUUID()).build(),
                Person.builder().id(UUID.randomUUID()).build()));

        ZeffyContactProcessor.ApplyResult result =
                processor.applyFromSync(contactPayload("contact-1", 0, 0));

        assertThat(result.needsReview()).isTrue();
        verify(people, never()).create(any());
        verifyNoInteractions(memberships);
        verify(contacts).save(argThat(contact -> "NEEDS_REVIEW".equals(contact.getProcessingStatus())
                && contact.getOutcomeReason().contains("more than one")));
    }

    @Test
    void deletionTombstonesLinkAndPreservesPersonAndMember() {
        UUID eventId = UUID.randomUUID();
        Person person = Person.builder().id(UUID.randomUUID()).build();
        ZeffyContact contact = ZeffyContact.builder().id(UUID.randomUUID())
                .zeffyContactId("contact-1").person(person).processingStatus("PROCESSED").build();
        ZeffyWebhookEvent event = ZeffyWebhookEvent.builder().id(eventId)
                .eventType("contact.deleted").zeffyResourceId("contact-1")
                .dispatchedAt(OffsetDateTime.parse("2026-09-24T12:00:00Z"))
                .processingAttemptCount(0).build();
        when(events.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(contacts.findByZeffyContactIdForUpdate("contact-1")).thenReturn(Optional.of(contact));

        processor.recordDeleted(eventId, false);

        assertThat(contact.getProcessingStatus()).isEqualTo("DELETED");
        assertThat(contact.getDeletedAt()).isEqualTo(event.getDispatchedAt());
        assertThat(contact.getPerson()).isSameAs(person);
        verifyNoInteractions(people, memberships);
    }

    private JsonNode contactPayload(String id, long totalContribution, int donationCount) throws Exception {
        return mapper.readTree("""
                {"id":"%s","object":"contact","created":1705341600,"updated":1705428000,
                 "email":"Jane@Example.com","first_name":"Jane","last_name":"Doe",
                 "phone_number":"+13125550100","address":{"line1":"1 Main St","city":"Chicago",
                 "state":"IL","postal_code":"60601","country":"US"},"donor_type":"individual",
                 "total_contribution":%d,"currency":"usd","donation_count":%d,
                 "first_donation_date":null,"last_donation_date":null,"metadata":{}}
                """.formatted(id, totalContribution, donationCount));
    }
}
