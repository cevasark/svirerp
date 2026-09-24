package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Rebuilds membership records from the locally synchronized Zeffy payment history. */
@Service
@RequiredArgsConstructor
public class ZeffyMembershipReconciliationService {

    private static final ZoneId CHURCH_ZONE = ZoneId.of("America/Chicago");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ZeffyPaymentRepository paymentRepository;
    private final PersonService personService;
    private final MembershipService membershipService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public record RebuildResult(
            int paymentsScanned,
            int peopleCreated,
            int membersCreated,
            int membershipPaymentsCreated,
            int membershipPaymentsUpdated,
            int membersRecomputed,
            int needsReview) {
    }

    @Transactional
    public RebuildResult rebuild() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("A Zeffy membership rebuild is already running");
        }

        try {
            List<ZeffyPayment> payments = paymentRepository.findMembershipCreditPayments();
            Set<UUID> affectedMemberIds = new LinkedHashSet<>();
            int peopleCreated = 0;
            int membersCreated = 0;
            int membershipPaymentsCreated = 0;
            int membershipPaymentsUpdated = 0;
            int needsReview = 0;

            for (ZeffyPayment payment : payments) {
                String validationIssue = validate(payment);
                if (validationIssue != null) {
                    markForReview(payment, validationIssue);
                    needsReview++;
                    continue;
                }

                PersonResolution personResolution = resolvePerson(payment);
                if (personResolution.issue() != null) {
                    markForReview(payment, personResolution.issue());
                    needsReview++;
                    continue;
                }
                if (personResolution.created()) peopleCreated++;
                Person person = personResolution.person();

                LocalDate paymentDate = payment.getPaymentCreatedAt()
                        .atZoneSameInstant(CHURCH_ZONE).toLocalDate();
                boolean memberExisted = membershipService
                        .findMemberByPersonIdIfExists(person.getId()).isPresent();
                Member member = membershipService.findOrCreateFollowerMember(
                        person.getId(), paymentDate);
                if (!memberExisted) membersCreated++;

                try {
                    MembershipService.ExternalPaymentUpsert upsert =
                            membershipService.upsertZeffyPayment(
                                    member.getId(), payment.getAmount(), paymentDate,
                                    payment.getPaymentCreatedAt(), payment.getZeffyPaymentId(),
                                    campaignName(payment));
                    if (upsert.created()) membershipPaymentsCreated++;
                    else membershipPaymentsUpdated++;
                    payment.setPerson(person);
                    payment.setMember(member);
                    payment.setMemberPayment(upsert.payment());
                    payment.setMembershipCredit(true);
                    paymentRepository.save(payment);
                    affectedMemberIds.add(member.getId());
                } catch (IllegalStateException duplicate) {
                    markForReview(payment, duplicate.getMessage());
                    needsReview++;
                }
            }

            for (UUID memberId : affectedMemberIds) {
                membershipService.recomputeTier(memberId);
            }

            return new RebuildResult(
                    payments.size(), peopleCreated, membersCreated,
                    membershipPaymentsCreated, membershipPaymentsUpdated,
                    affectedMemberIds.size(), needsReview);
        } finally {
            running.set(false);
        }
    }

    private String validate(ZeffyPayment payment) {
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            return "Membership rebuild: payment amount is missing or invalid";
        }
        if (payment.getPaymentCreatedAt() == null) {
            return "Membership rebuild: original payment date is missing";
        }
        if (payment.getRefundStatus() != null
                && !"none".equalsIgnoreCase(payment.getRefundStatus())) {
            return "Membership rebuild: refunded payment requires lifecycle reconciliation";
        }
        return null;
    }

    private PersonResolution resolvePerson(ZeffyPayment payment) {
        if (payment.getPerson() != null) {
            return new PersonResolution(payment.getPerson(), false, null);
        }

        String email = normalizedEmail(payment.getBuyerEmail());
        if (email == null || !EMAIL.matcher(email).matches()) {
            return new PersonResolution(null, false,
                    "Membership rebuild: buyer email is missing or invalid");
        }
        List<Person> matches = personService.findByNormalizedEmail(email);
        if (matches.size() > 1) {
            return new PersonResolution(null, false,
                    "Membership rebuild: buyer email matches more than one local person");
        }
        if (!matches.isEmpty()) {
            return new PersonResolution(matches.getFirst(), false, null);
        }

        String firstName = trim(payment.getBuyerFirstName());
        String lastName = trim(payment.getBuyerLastName());
        if (firstName == null || lastName == null) {
            return new PersonResolution(null, false,
                    "Membership rebuild: buyer name is required to create a local person");
        }
        Person created = personService.create(Person.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .build());
        return new PersonResolution(created, true, null);
    }

    private void markForReview(ZeffyPayment payment, String issue) {
        payment.setOutcomeReason(issue);
        if (!"PROCESSED".equals(payment.getProcessingStatus())) {
            payment.setProcessingStatus("NEEDS_REVIEW");
        }
        paymentRepository.save(payment);
    }

    private String campaignName(ZeffyPayment payment) {
        String title = trim(payment.getCampaignTitle());
        return title == null ? payment.getCampaignId() : title;
    }

    private String normalizedEmail(String email) {
        String trimmed = trim(email);
        return trimmed == null ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PersonResolution(Person person, boolean created, String issue) {
    }
}
