package com.svivanrilski.svirerp.person;

import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberRepository;
import com.svivanrilski.svirerp.membership.MembershipType;
import com.svivanrilski.svirerp.zeffyintegration.ZeffyContact;
import com.svivanrilski.svirerp.zeffyintegration.ZeffyContactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonOverviewServiceTest {

    @Mock private PersonRepository personRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ZeffyContactRepository contactRepository;

    @InjectMocks
    private PersonOverviewService overviewService;

    @Test
    @DisplayName("findAll with filters queries repository with specification and maps overview")
    @SuppressWarnings("unchecked")
    void findAll_withFilters_queriesSpecificationAndMapsOverview() {
        UUID personId = UUID.randomUUID();
        Person person = Person.builder()
                .id(personId)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phone("555-1234")
                .build();

        MembershipType type = MembershipType.builder()
                .id(UUID.randomUUID())
                .name("Benefactor")
                .build();

        Member member = Member.builder()
                .id(UUID.randomUUID())
                .person(person)
                .membershipType(type)
                .status("active")
                .joinDate(LocalDate.of(2025, 1, 1))
                .expiryDate(LocalDate.of(2026, 1, 1))
                .build();

        ZeffyContact contact = ZeffyContact.builder()
                .id(UUID.randomUUID())
                .zeffyContactId("zc_123")
                .person(person)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .processingStatus("PROCESSED")
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Person> personPage = new PageImpl<>(List.of(person), pageable, 1);

        when(personRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(personPage);
        when(memberRepository.findByPerson_IdIn(List.of(personId))).thenReturn(List.of(member));
        when(contactRepository.findByPerson_IdInOrderByZeffyContactId(List.of(personId))).thenReturn(List.of(contact));

        Page<PersonOverviewService.PersonOverview> result = overviewService.findAll(
                "John", "john%", type.getId(), null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        PersonOverviewService.PersonOverview overview = result.getContent().get(0);
        assertThat(overview.id()).isEqualTo(personId);
        assertThat(overview.firstName()).isEqualTo("John");
        assertThat(overview.lastName()).isEqualTo("Doe");
        assertThat(overview.email()).isEqualTo("john.doe@example.com");
        assertThat(overview.membershipType()).isEqualTo("Benefactor");
        assertThat(overview.membershipStatus()).isEqualTo("active");
        assertThat(overview.zeffyContacts()).hasSize(1);
        assertThat(overview.zeffyContacts().get(0).zeffyContactId()).isEqualTo("zc_123");

        ArgumentCaptor<Specification<Person>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(personRepository).findAll(specCaptor.capture(), eq(pageable));
        assertThat(specCaptor.getValue()).isNotNull();
    }
}
