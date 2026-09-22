package com.svivanrilski.svirerp.person;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.svivanrilski.svirerp.membership.MembershipService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the create-vs-skip decision and the unsubscribed->active mapping. All collaborators
 *  mocked; doesn't stand up a Spring context or a real DB. */
@ExtendWith(MockitoExtension.class)
class PersonImportRowApplierTest {

    @Mock private PersonService personService;
    @Mock private MembershipService membershipService;

    @InjectMocks
    private PersonImportRowApplier applier;

    private UUID orgId;

    private PersonImportRow rowOf(String email, boolean unsubscribed) {
        return new PersonImportRow("Jane", "Doe", email, "555-0100", "123 Main St", "Springfield", "62704",
                unsubscribed);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
    }

    @Test
    void newEmail_createsPersonAndActiveFollower() {
        PersonImportRow row = rowOf("jane.doe@example.com", false);
        when(personService.findByEmailIfExists(row.email())).thenReturn(Optional.empty());
        Person created = Person.builder().id(UUID.randomUUID()).build();
        when(personService.create(any(Person.class))).thenReturn(created);

        PersonImportRowApplier.ApplyOutcome outcome = applier.applyRow(orgId, row);

        assertThat(outcome).isEqualTo(PersonImportRowApplier.ApplyOutcome.CREATED);
        ArgumentCaptor<Person> personCaptor = ArgumentCaptor.forClass(Person.class);
        verify(personService).create(personCaptor.capture());
        assertThat(personCaptor.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(personCaptor.getValue().getEmail()).isEqualTo("jane.doe@example.com");
        verify(membershipService).createFollowerMember(created.getId(), orgId, true);
    }

    @Test
    void unsubscribedRow_createsInactiveFollower() {
        PersonImportRow row = rowOf("unsub@example.com", true);
        when(personService.findByEmailIfExists(row.email())).thenReturn(Optional.empty());
        Person created = Person.builder().id(UUID.randomUUID()).build();
        when(personService.create(any(Person.class))).thenReturn(created);

        applier.applyRow(orgId, row);

        verify(membershipService).createFollowerMember(created.getId(), orgId, false);
    }

    @Test
    void existingEmail_isSkipped_personAndMemberUntouched() {
        PersonImportRow row = rowOf("already@example.com", false);
        Person existing = Person.builder().id(UUID.randomUUID()).build();
        when(personService.findByEmailIfExists(row.email())).thenReturn(Optional.of(existing));

        PersonImportRowApplier.ApplyOutcome outcome = applier.applyRow(orgId, row);

        assertThat(outcome).isEqualTo(PersonImportRowApplier.ApplyOutcome.SKIPPED_EXISTING);
        verify(personService, never()).create(any());
        verify(membershipService, never()).createFollowerMember(any(), any(), anyBoolean());
    }
}
