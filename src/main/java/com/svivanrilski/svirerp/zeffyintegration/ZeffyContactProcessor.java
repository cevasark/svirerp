package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Applies one contact at a time so bulk synchronization can retain all successful contacts. */
@Service
@RequiredArgsConstructor
public class ZeffyContactProcessor {

    private static final ZoneId CHURCH_ZONE = ZoneId.of("America/Chicago");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ZeffyContactRepository contactRepository;
    private final ZeffyWebhookEventRepository eventRepository;
    private final ZeffyPaymentRepository paymentRepository;
    private final ZeffyContactPayload payload;
    private final PersonService personService;
    private final MembershipService membershipService;

    public record ApplyResult(boolean inserted, boolean updated, boolean ignored,
                              boolean needsReview, UUID contactRecordId) {
    }

    @Transactional
    public ApplyResult applyFromSync(JsonNode remote) {
        return apply(payload.parse(remote), "API_SYNC", null, null);
    }

    @Transactional
    public ApplyResult applyFromWebhook(UUID eventId, JsonNode remote) {
        ZeffyWebhookEvent event = lockContactEvent(eventId);
        return apply(payload.parse(remote), "WEBHOOK", event, event.getDispatchedAt());
    }

    @Transactional
    public ApplyResult applyCreatedEvent(UUID eventId) {
        ZeffyWebhookEvent event = lockContactEvent(eventId);
        return apply(payload.parseEvent(event.getRawPayload()), "WEBHOOK", event, event.getDispatchedAt());
    }

    private ApplyResult apply(ZeffyContactPayload.ContactData data, String source,
                              ZeffyWebhookEvent event, OffsetDateTime observedAt) {
        ZeffyContact contact = contactRepository.findByZeffyContactIdForUpdate(data.id()).orElse(null);
        boolean inserted = contact == null;
        if (inserted) {
            contact = ZeffyContact.builder()
                    .zeffyContactId(data.id()).firstSeenSource(source)
                    .firstSeenAt(OffsetDateTime.now(ZoneOffset.UTC)).processingStatus("NEEDS_REVIEW")
                    .build();
        } else if (observedAt != null && contact.getLastEventAt() != null
                && contact.getLastEventAt().isAfter(observedAt)) {
            finishEvent(event, contact, "PROCESSED",
                    "Older contact event ignored because newer contact data is stored", null);
            return new ApplyResult(false, false, true, false, contact.getId());
        }

        boolean samePayload = Objects.equals(contact.getLatestPayloadSha256(), data.payloadSha256());
        boolean retryReview = "NEEDS_REVIEW".equals(contact.getProcessingStatus())
                || "ERROR".equals(contact.getProcessingStatus());
        copySnapshot(contact, data);
        contact.setDeletedAt(null);
        if (event != null) {
            contact.setLatestWebhookEvent(event);
            contact.setLastEventAt(observedAt);
        } else {
            contact.setLastSyncedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        if (samePayload && !retryReview && contact.getPerson() != null) {
            contact.setProcessingStatus("PROCESSED");
            contact.setOutcomeReason(null);
            contactRepository.save(contact);
            finishEvent(event, contact, "PROCESSED", "Contact was already synchronized", null);
            return new ApplyResult(inserted, false, true, false, contact.getId());
        }

        String validation = validate(data);
        if (validation != null) return needsReview(contact, event, inserted, validation);

        PersonResolution resolution = resolvePerson(contact, data);
        if (resolution.reason() != null) {
            return needsReview(contact, event, inserted, resolution.reason());
        }

        Person person = fillPerson(resolution.person(), data);
        LocalDate joinDate = data.createdAt().atZoneSameInstant(CHURCH_ZONE).toLocalDate();
        Member member = membershipService.findOrCreateFollowerMember(person.getId(), joinDate);
        String paymentConflict = linkPayments(data.id(), person);

        contact.setPerson(person);
        contact.setProcessingStatus(paymentConflict == null ? "PROCESSED" : "NEEDS_REVIEW");
        contact.setOutcomeReason(paymentConflict);
        ZeffyContact saved = contactRepository.save(contact);
        String summary = paymentConflict == null
                ? "Contact linked to " + person.getFirstName() + " " + person.getLastName()
                + " as " + member.getMembershipType().getName()
                : paymentConflict;
        finishEvent(event, saved, paymentConflict == null ? "PROCESSED" : "NEEDS_REVIEW",
                summary, paymentConflict);
        return new ApplyResult(inserted, !inserted, false, paymentConflict != null, saved.getId());
    }

    @Transactional
    public void recordDeleted(UUID eventId, boolean confirmedByFetch) {
        ZeffyWebhookEvent event = lockContactEvent(eventId);
        ZeffyContact contact = contactRepository.findByZeffyContactIdForUpdate(event.getZeffyResourceId())
                .orElseGet(() -> ZeffyContact.builder()
                        .zeffyContactId(event.getZeffyResourceId())
                        .firstSeenSource("WEBHOOK").firstSeenAt(event.getDispatchedAt())
                        .processingStatus("DELETED").build());
        if (contact.getLastEventAt() != null && contact.getLastEventAt().isAfter(event.getDispatchedAt())) {
            finishEvent(event, contact, "PROCESSED",
                    "Older contact deletion ignored because newer contact data is stored", null);
            return;
        }
        contact.setLatestWebhookEvent(event);
        contact.setLastEventAt(event.getDispatchedAt());
        contact.setDeletedAt(event.getDispatchedAt());
        contact.setProcessingStatus("DELETED");
        contact.setOutcomeReason(null);
        ZeffyContact saved = contactRepository.save(contact);
        finishEvent(event, saved, "PROCESSED",
                confirmedByFetch ? "Contact no longer exists in Zeffy; local records were preserved"
                        : "Zeffy contact deleted; local records were preserved", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(UUID eventId, String reason) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        event.setStatus("ERROR");
        event.setProcessingAttemptCount(event.getProcessingAttemptCount() + 1);
        event.setLastAttemptedAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setErrorSummary(truncate(reason));
        eventRepository.save(event);
    }

    private PersonResolution resolvePerson(ZeffyContact contact, ZeffyContactPayload.ContactData data) {
        if (contact.getPerson() != null) {
            if (data.email() != null) {
                List<Person> emailMatches = personService.findByNormalizedEmail(data.email());
                if (emailMatches.stream().anyMatch(p -> !p.getId().equals(contact.getPerson().getId()))) {
                    return new PersonResolution(null,
                            "Zeffy email belongs to another local Person; the stable contact link was preserved");
                }
            }
            return new PersonResolution(contact.getPerson(), null);
        }

        Set<Person> paymentPeople = new LinkedHashSet<>();
        for (ZeffyPayment payment : paymentRepository.findByContactId(data.id())) {
            if (payment.getPerson() != null) paymentPeople.add(payment.getPerson());
        }
        if (paymentPeople.size() > 1) {
            return new PersonResolution(null, "Zeffy contact is linked to more than one payment Person");
        }
        if (paymentPeople.size() == 1) return new PersonResolution(paymentPeople.iterator().next(), null);

        if (data.email() != null) {
            List<Person> matches = personService.findByNormalizedEmail(data.email());
            if (matches.size() > 1) {
                return new PersonResolution(null, "Zeffy contact email matches more than one local Person");
            }
            if (matches.size() == 1) return new PersonResolution(matches.getFirst(), null);
        }
        if (data.firstName() == null || data.lastName() == null) {
            return new PersonResolution(null,
                    "Zeffy contact needs first and last name before a local Person can be created");
        }
        return new PersonResolution(null, null);
    }

    private Person fillPerson(Person existing, ZeffyContactPayload.ContactData data) {
        Person incoming = Person.builder()
                .firstName(data.firstName()).lastName(data.lastName()).email(data.email())
                .phone(data.phoneNumber()).addressLine1(data.addressLine1())
                .city(data.city()).state(data.state()).zip(data.postalCode()).build();
        return existing == null ? personService.create(incoming)
                : personService.fillBlankContactFields(existing.getId(), incoming);
    }

    private String linkPayments(String contactId, Person person) {
        String conflict = null;
        for (ZeffyPayment payment : paymentRepository.findByContactId(contactId)) {
            if (payment.getPerson() == null) {
                payment.setPerson(person);
                paymentRepository.save(payment);
            } else if (!payment.getPerson().getId().equals(person.getId())) {
                conflict = "A Zeffy payment for this contact is linked to a different local Person";
            }
        }
        return conflict;
    }

    private String validate(ZeffyContactPayload.ContactData data) {
        if (data.email() != null && !EMAIL.matcher(data.email()).matches()) {
            return "Zeffy contact email is invalid";
        }
        if (data.totalContribution() == null || data.totalContribution().compareTo(BigDecimal.ZERO) < 0) {
            return "Zeffy contact total contribution is invalid";
        }
        if (data.donationCount() == null || data.donationCount() < 0) {
            return "Zeffy contact donation count is invalid";
        }
        return null;
    }

    private ApplyResult needsReview(ZeffyContact contact, ZeffyWebhookEvent event,
                                    boolean inserted, String reason) {
        contact.setProcessingStatus("NEEDS_REVIEW");
        contact.setOutcomeReason(truncate(reason));
        ZeffyContact saved = contactRepository.save(contact);
        finishEvent(event, saved, "NEEDS_REVIEW", "Contact requires review", reason);
        return new ApplyResult(inserted, !inserted, false, true, saved.getId());
    }

    private void copySnapshot(ZeffyContact target, ZeffyContactPayload.ContactData source) {
        target.setEmail(source.email());
        target.setFirstName(source.firstName());
        target.setLastName(source.lastName());
        target.setPhoneNumber(source.phoneNumber());
        target.setAddressLine1(source.addressLine1());
        target.setCity(source.city());
        target.setState(source.state());
        target.setPostalCode(source.postalCode());
        target.setCountry(source.country());
        target.setDonorType(source.donorType());
        target.setTotalContribution(source.totalContribution());
        target.setCurrency(source.currency());
        target.setDonationCount(source.donationCount());
        target.setFirstDonationAt(source.firstDonationAt());
        target.setLastDonationAt(source.lastDonationAt());
        target.setZeffyCreatedAt(source.createdAt());
        target.setZeffyUpdatedAt(source.updatedAt());
        target.setLatestPayloadSha256(source.payloadSha256());
    }

    private ZeffyWebhookEvent lockContactEvent(UUID eventId) {
        ZeffyWebhookEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        if (!event.getEventType().startsWith("contact.")) {
            throw new IllegalArgumentException("Webhook event is not a Zeffy contact event");
        }
        event.setStatus("PROCESSING");
        event.setProcessingAttemptCount(event.getProcessingAttemptCount() + 1);
        event.setLastAttemptedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return eventRepository.save(event);
    }

    private void finishEvent(ZeffyWebhookEvent event, ZeffyContact contact, String status,
                             String summary, String error) {
        if (event == null) return;
        event.setZeffyContact(contact);
        event.setStatus(status);
        event.setProcessingSummary(truncate(summary));
        event.setErrorSummary(truncate(error));
        event.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        eventRepository.save(event);
    }

    private String truncate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record PersonResolution(Person person, String reason) {
    }
}
