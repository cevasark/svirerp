package com.svivanrilski.svirerp.person;

import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberRepository;
import com.svivanrilski.svirerp.zeffyintegration.ZeffyContact;
import com.svivanrilski.svirerp.zeffyintegration.ZeffyContactRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PersonOverviewService {

    private final PersonRepository personRepository;
    private final MemberRepository memberRepository;
    private final ZeffyContactRepository contactRepository;

    public PersonOverviewService(
            PersonRepository personRepository,
            MemberRepository memberRepository,
            ZeffyContactRepository contactRepository) {
        this.personRepository = personRepository;
        this.memberRepository = memberRepository;
        this.contactRepository = contactRepository;
    }

    public record ZeffyContactSummary(
            UUID id, String zeffyContactId, String email, String firstName, String lastName,
            String phoneNumber, String addressLine1, String city, String state, String postalCode,
            String country, String donorType, BigDecimal totalContribution, String currency, Integer donationCount,
            String firstDonationAt, String lastDonationAt, String processingStatus,
            String outcomeReason, String zeffyCreatedAt, String zeffyUpdatedAt,
            String lastSyncedAt, String deletedAt) {
    }

    public record PersonOverview(
            UUID id, String firstName, String lastName, String email, String phone,
            String addressLine1, String city, String state, String zip,
            LocalDate dateOfBirth, OffsetDateTime createdAt,
            UUID memberId, String membershipType, String membershipStatus,
            LocalDate joinDate, LocalDate expiryDate,
            List<ZeffyContactSummary> zeffyContacts) {
    }

    public Page<PersonOverview> findAll(Pageable pageable) {
        return findAll(null, null, null, null, pageable);
    }

    public Page<PersonOverview> findAll(
            String name,
            String email,
            UUID membershipTypeId,
            String membershipTypeName,
            Pageable pageable) {
        return findAll(name, email, membershipTypeId, membershipTypeName, false, pageable);
    }

    public Page<PersonOverview> findAll(
            String name,
            String email,
            UUID membershipTypeId,
            String membershipTypeName,
            boolean noMembership,
            Pageable pageable) {
        Page<Person> people = personRepository.findAll(
                PersonSpecifications.filter(name, email, membershipTypeId, membershipTypeName, noMembership),
                pageable);
        List<UUID> ids = people.stream().map(Person::getId).toList();
        if (ids.isEmpty()) return people.map(person -> toOverview(person, null, List.of()));

        Map<UUID, Member> members = noMembership
                ? Map.of()
                : memberRepository.findByPerson_IdIn(ids).stream()
                        .collect(Collectors.toMap(member -> member.getPerson().getId(), Function.identity(), (m1, m2) -> m1));
        Map<UUID, List<ZeffyContact>> contacts = contactRepository
                .findByPerson_IdInOrderByZeffyContactId(ids).stream()
                .collect(Collectors.groupingBy(contact -> contact.getPerson().getId()));
        return people.map(person -> toOverview(
                person, members.get(person.getId()), contacts.getOrDefault(person.getId(), List.of())));
    }

    private PersonOverview toOverview(Person person, Member member, List<ZeffyContact> contacts) {
        return new PersonOverview(
                person.getId(), person.getFirstName(), person.getLastName(), person.getEmail(),
                person.getPhone(), person.getAddressLine1(), person.getCity(), person.getState(),
                person.getZip(), person.getDateOfBirth(), person.getCreatedAt(),
                member == null ? null : member.getId(),
                member == null ? null : member.getMembershipType().getName(),
                member == null ? null : member.getStatus(),
                member == null ? null : member.getJoinDate(),
                member == null ? null : member.getExpiryDate(),
                contacts.stream().map(this::toSummary).toList());
    }

    private ZeffyContactSummary toSummary(ZeffyContact contact) {
        return new ZeffyContactSummary(
                contact.getId(), contact.getZeffyContactId(), contact.getEmail(),
                contact.getFirstName(), contact.getLastName(), contact.getPhoneNumber(),
                contact.getAddressLine1(), contact.getCity(), contact.getState(),
                contact.getPostalCode(), contact.getCountry(), contact.getDonorType(),
                contact.getTotalContribution(), contact.getCurrency(), contact.getDonationCount(),
                text(contact.getFirstDonationAt()), text(contact.getLastDonationAt()),
                contact.getProcessingStatus(), contact.getOutcomeReason(),
                text(contact.getZeffyCreatedAt()), text(contact.getZeffyUpdatedAt()),
                text(contact.getLastSyncedAt()), text(contact.getDeletedAt()));
    }

    private String text(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
