package com.svivanrilski.svirerp.membership;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.organization.Organization;
import com.svivanrilski.svirerp.organization.OrganizationService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MembershipService {

    // Valid values for each status/method column — mirrors the DB CHECK constraints.
    private static final Set<String> MEMBER_STATUSES =
            Set.of("active", "inactive", "suspended", "expired", "pending");
    private static final Set<String> PAYMENT_METHODS =
            Set.of("cash", "check", "credit_card", "ach", "online", "other", "zeffy", "stripe");
    private static final Set<String> PAYMENT_STATUSES =
            Set.of("pending", "completed", "failed", "refunded");

    private final MembershipTypeRepository typeRepo;
    private final MemberRepository memberRepo;
    private final MemberPaymentRepository paymentRepo;
    private final OrganizationService orgService;
    private final PersonService personService;

    // ── MembershipType ──────────────────────────────────────────────────────

    public Page<MembershipType> findAllTypes(UUID orgId, Pageable pageable) {
        return typeRepo.findByOrgId(orgId, pageable);
    }

    public MembershipType findTypeById(UUID id) {
        return typeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipType", id));
    }

    @Transactional
    public MembershipType createType(MembershipType type) {
        // Ensure the org exists before saving
        Organization org = orgService.findById(type.getOrg().getId());
        type.setOrg(org);
        return typeRepo.save(type);
    }

    @Transactional
    public MembershipType updateType(UUID id, MembershipType patch) {
        MembershipType existing = findTypeById(id);
        existing.setName(patch.getName());
        existing.setDescription(patch.getDescription());
        existing.setAnnualFee(patch.getAnnualFee());
        existing.setDurationMonths(patch.getDurationMonths());
        existing.setIsActive(patch.getIsActive());
        existing.setCanVote(patch.getCanVote());
        existing.setBenefits(patch.getBenefits());
        existing.setMaxMembers(patch.getMaxMembers());
        return typeRepo.save(existing);
    }

    @Transactional
    public void deleteType(UUID id) {
        if (!typeRepo.existsById(id)) throw new ResourceNotFoundException("MembershipType", id);
        typeRepo.deleteById(id);
    }

    // ── Member ───────────────────────────────────────────────────────────────

    public Page<Member> findAllMembers(UUID orgId, String status, UUID membershipTypeId, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            validateMemberStatus(status);
        }
        boolean hasStatus = status != null && !status.isBlank();
        if (hasStatus && membershipTypeId != null) {
            return memberRepo.findByOrgIdAndStatusAndMembershipTypeId(orgId, status, membershipTypeId, pageable);
        }
        if (hasStatus) {
            return memberRepo.findByOrgIdAndStatus(orgId, status, pageable);
        }
        if (membershipTypeId != null) {
            return memberRepo.findByOrgIdAndMembershipTypeId(orgId, membershipTypeId, pageable);
        }
        return memberRepo.findByOrgId(orgId, pageable);
    }

    public Member findMemberById(UUID id) {
        return memberRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", id));
    }

    public record MemberSummary(
            long activeMembers, long inactiveMembers,
            long activeBenefactors, long inactiveBenefactors,
            long followers, long totalMembers) {
    }

    /** Counts by tier/status for the Members list header — tier is TierCalculator.MEMBER/
     *  BENEFACTOR/FOLLOWER stored as the member's membershipType name (see TierCalculator's class
     *  doc for why "Member" here means the $150+ tier, not the generic "has a Member row" sense).
     *  Followers have no active/inactive distinction (always active — see TierCalculator), so just
     *  one count. totalMembers is every Member row for the org, regardless of tier/status. */
    public MemberSummary getMemberSummary(UUID orgId) {
        return new MemberSummary(
                memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "active", TierCalculator.MEMBER),
                memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "inactive", TierCalculator.MEMBER),
                memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "active", TierCalculator.BENEFACTOR),
                memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "inactive", TierCalculator.BENEFACTOR),
                memberRepo.countByOrgIdAndMembershipType_NameIgnoreCase(orgId, TierCalculator.FOLLOWER),
                memberRepo.countByOrgId(orgId));
    }

    /** Returns all members whose expiry date is before today (candidates for renewal notices). */
    public List<Member> findExpiredMembers() {
        return memberRepo.findByExpiryDateBefore(LocalDate.now());
    }

    @Transactional
    public Member createMember(Member member) {
        validateMemberStatus(member.getStatus());
        Person person = personService.findById(member.getPerson().getId());
        Organization org = orgService.findById(member.getOrg().getId());
        MembershipType type = findTypeById(member.getMembershipType().getId());
        // A person may hold at most one membership per organisation — to switch
        // membership type, update the existing Member record instead of creating another.
        if (memberRepo.existsByPersonIdAndOrgId(person.getId(), org.getId())) {
            throw new IllegalArgumentException(
                    person.getFirstName() + " " + person.getLastName() + " already has a membership in this organization");
        }
        member.setPerson(person);
        member.setOrg(org);
        member.setMembershipType(type);
        return memberRepo.save(member);
    }

    @Transactional
    public Member updateMember(UUID id, Member patch) {
        validateMemberStatus(patch.getStatus());
        Member existing = findMemberById(id);
        existing.setMembershipType(findTypeById(patch.getMembershipType().getId()));
        existing.setMemberNumber(patch.getMemberNumber());
        existing.setJoinDate(patch.getJoinDate());
        existing.setExpiryDate(patch.getExpiryDate());
        existing.setStatus(patch.getStatus());
        existing.setEmailOptIn(patch.getEmailOptIn());
        return memberRepo.save(existing);
    }

    @Transactional
    public void deleteMember(UUID id) {
        if (!memberRepo.existsById(id)) throw new ResourceNotFoundException("Member", id);
        memberRepo.deleteById(id);
    }

    public enum ImportOutcome { CREATED, UPDATED }

    /**
     * Called once per CSV row by MemberImportService — deliberately a separate
     * bean-to-bean call (not a loop within this same class) so each row gets its
     * own transaction. Without that, a DB-level failure partway through a big
     * multi-row transaction can mark the whole thing rollback-only, silently
     * discarding otherwise-successful rows despite catching the exception.
     */
    @Transactional
    public ImportOutcome importOrUpdateMemberFromRow(UUID orgId, MemberImportRow row) {
        validateMemberStatus(row.status());
        Organization org = orgService.findById(orgId);

        MembershipType type = typeRepo.findByOrgIdAndNameIgnoreCase(orgId, row.membershipTypeName())
                .orElseThrow(() -> new IllegalArgumentException("Unknown membership type: " + row.membershipTypeName()));

        Person person = personService.findByEmailIfExists(row.email())
                .orElseGet(() -> personService.create(Person.builder()
                        .firstName(row.firstName())
                        .lastName(row.lastName())
                        .email(row.email())
                        .phone(row.phone())
                        .addressLine1(row.addressLine1())
                        .city(row.city())
                        .state(row.state())
                        .zip(row.zip())
                        .dateOfBirth(row.dateOfBirth())
                        .build()));

        Optional<Member> existing = memberRepo.findByPersonIdAndOrgId(person.getId(), orgId);
        if (existing.isPresent()) {
            Member member = existing.get();
            member.setMembershipType(type);
            member.setMemberNumber(row.memberNumber());
            member.setJoinDate(row.joinDate());
            member.setExpiryDate(row.expiryDate());
            member.setStatus(row.status() != null ? row.status() : member.getStatus());
            member.setEmailOptIn(row.emailOptIn() != null ? row.emailOptIn() : member.getEmailOptIn());
            memberRepo.save(member);
            return ImportOutcome.UPDATED;
        }

        Member member = Member.builder()
                .person(person)
                .org(org)
                .membershipType(type)
                .memberNumber(row.memberNumber())
                .joinDate(row.joinDate())
                .expiryDate(row.expiryDate())
                .status(row.status() != null ? row.status() : "active")
                .emailOptIn(row.emailOptIn() != null ? row.emailOptIn() : true)
                .build();
        memberRepo.save(member);
        return ImportOutcome.CREATED;
    }

    // ── MemberPayment ────────────────────────────────────────────────────────

    public Page<MemberPayment> findPaymentsByMember(UUID memberId, Pageable pageable) {
        return paymentRepo.findByMemberId(memberId, pageable);
    }

    public Page<MemberPayment> findPaymentsByOrg(UUID orgId, LocalDate fromDate, Pageable pageable) {
        if (fromDate == null) {
            return paymentRepo.findByMemberOrgId(orgId, pageable);
        }
        return paymentRepo.findByMemberOrgIdAndPaymentDateGreaterThanEqual(orgId, fromDate, pageable);
    }

    public MemberPayment findPaymentById(UUID id) {
        return paymentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MemberPayment", id));
    }

    @Transactional
    public MemberPayment createPayment(MemberPayment payment) {
        validatePaymentMethod(payment.getPaymentMethod());
        validatePaymentStatus(payment.getStatus());
        Member member = findMemberById(payment.getMember().getId());
        payment.setMember(member);
        return paymentRepo.save(payment);
    }

    @Transactional
    public MemberPayment updatePayment(UUID id, MemberPayment patch) {
        validatePaymentMethod(patch.getPaymentMethod());
        validatePaymentStatus(patch.getStatus());
        MemberPayment existing = findPaymentById(id);
        existing.setAmount(patch.getAmount());
        existing.setPaymentDate(patch.getPaymentDate());
        existing.setPaymentMethod(patch.getPaymentMethod());
        existing.setTransactionRef(patch.getTransactionRef());
        existing.setPeriodStart(patch.getPeriodStart());
        existing.setPeriodEnd(patch.getPeriodEnd());
        existing.setStatus(patch.getStatus());
        existing.setNotes(patch.getNotes());
        return paymentRepo.save(existing);
    }

    @Transactional
    public void deletePayment(UUID id) {
        if (!paymentRepo.existsById(id)) throw new ResourceNotFoundException("MemberPayment", id);
        paymentRepo.deleteById(id);
    }

    // ── Zeffy tier computation ──────────────────────────────────────────────
    // See TierCalculator for the algorithm. MembershipType names below are the ones
    // ZeffyImportService/recomputeTier resolve a computed tier name against.

    private static final String[] ZEFFY_TIER_TYPES = {
        // name, canVote
        TierCalculator.FOLLOWER, TierCalculator.MEMBER, TierCalculator.BENEFACTOR,
    };

    /**
     * Lazily seeds the three Zeffy tier MembershipTypes for an org. Deliberately not gated on
     * "type list is empty" (unlike the Finance chart-of-accounts seed) — an org may already have
     * unrelated custom types (e.g. from the pre-existing member CSV import), so that guard would
     * silently skip seeding these. Idempotent per-row instead.
     */
    @Transactional
    public void ensureZeffyTierTypesSeeded(UUID orgId) {
        Organization org = orgService.findById(orgId);
        for (String name : ZEFFY_TIER_TYPES) {
            if (!typeRepo.existsByOrgIdAndNameIgnoreCase(orgId, name)) {
                typeRepo.save(MembershipType.builder()
                        .org(org)
                        .name(name)
                        .canVote(!TierCalculator.FOLLOWER.equals(name))
                        .annualFee(BigDecimal.ZERO)
                        .durationMonths(12)
                        .isActive(true)
                        .build());
            }
        }
    }

    /**
     * Recomputes a member's tier (MembershipType), expiryDate, and status from their full
     * completed-payment history — see TierCalculator's class doc for the chaining rule. "inactive"
     * now covers two distinct cases: (a) zero completed-payment history at all (unreachable via
     * Zeffy import, but possible for a manually-created Member with no payments) — tier is left
     * untouched in that case; (b) the member's last $150+ membership period has expired — tier is
     * still updated to whatever that last period was (not reverted to Follower), only status flips.
     */
    @Transactional
    public Member recomputeTier(UUID memberId) {
        Member member = findMemberById(memberId);
        ensureZeffyTierTypesSeeded(member.getOrg().getId());

        List<TierCalculator.PaymentSnapshot> snapshots = paymentRepo
                .findByMemberIdAndStatus(memberId, "completed").stream()
                .map(p -> new TierCalculator.PaymentSnapshot(p.getAmount(), p.getPaymentDate()))
                .toList();
        TierCalculator.TierResult result = TierCalculator.compute(snapshots);

        if (result == null) {
            member.setStatus("inactive");
            return memberRepo.save(member);
        }

        MembershipType tierType = typeRepo.findByOrgIdAndNameIgnoreCase(member.getOrg().getId(), result.tierName())
                .orElseThrow(() -> new IllegalStateException("Zeffy tier type not seeded: " + result.tierName()));
        member.setMembershipType(tierType);
        member.setStatus(result.status());
        member.setExpiryDate(result.expiryDate());
        return memberRepo.save(member);
    }

    public boolean hasMembership(UUID personId, UUID orgId) {
        return memberRepo.existsByPersonIdAndOrgId(personId, orgId);
    }

    /**
     * Find-or-create a Member starting at the Follower tier — used by ZeffyImportRowApplier for a
     * brand-new payer. Kept here (rather than reaching into MemberRepository from another package)
     * so all Member/MembershipType access stays inside this domain's service, per this repo's
     * one-shared-service-per-domain-area convention.
     *
     * <p>{@code transactionDate} also self-heals an existing member's {@code joinDate} backward
     * when an earlier transaction shows up — a bulk historical import processes files/rows in
     * whatever order they're uploaded, not necessarily chronological, so the transaction that
     * happens to be committed *first* for a person isn't necessarily their chronologically
     * earliest. Without this, joinDate would permanently lock onto whichever transaction was
     * committed first, which can be wrong by however far out of order the import was.
     */
    @Transactional
    public Member findOrCreateFollowerMember(UUID personId, UUID orgId, LocalDate transactionDate) {
        return memberRepo.findByPersonIdAndOrgId(personId, orgId)
                .map(existing -> {
                    if (transactionDate != null && existing.getJoinDate() != null
                            && transactionDate.isBefore(existing.getJoinDate())) {
                        existing.setJoinDate(transactionDate);
                        return memberRepo.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    ensureZeffyTierTypesSeeded(orgId);
                    MembershipType follower = typeRepo.findByOrgIdAndNameIgnoreCase(orgId, TierCalculator.FOLLOWER)
                            .orElseThrow(() -> new IllegalStateException("Zeffy tier type not seeded: " + TierCalculator.FOLLOWER));
                    return memberRepo.save(Member.builder()
                            .person(personService.findById(personId))
                            .org(orgService.findById(orgId))
                            .membershipType(follower)
                            .joinDate(transactionDate)
                            .status("active")
                            .emailOptIn(true)
                            .build());
                });
    }

    /**
     * Unconditionally creates a new Follower Member for a person who is guaranteed not to have one
     * yet in this org (used by the People import, which only ever calls this for a Person it just
     * created — unlike {@link #findOrCreateFollowerMember}, which also handles the "may already
     * exist" case for the Zeffy-transactions path). joinDate is the import date, since a Zeffy
     * contacts export carries no date of its own. {@code active} drives both status and
     * emailOptIn together — a person Zeffy reports as unsubscribed is neither.
     */
    @Transactional
    public Member createFollowerMember(UUID personId, UUID orgId, boolean active) {
        ensureZeffyTierTypesSeeded(orgId);
        MembershipType follower = typeRepo.findByOrgIdAndNameIgnoreCase(orgId, TierCalculator.FOLLOWER)
                .orElseThrow(() -> new IllegalStateException("Zeffy tier type not seeded: " + TierCalculator.FOLLOWER));
        return memberRepo.save(Member.builder()
                .person(personService.findById(personId))
                .org(orgService.findById(orgId))
                .membershipType(follower)
                .joinDate(LocalDate.now())
                .status(active ? "active" : "inactive")
                .emailOptIn(active)
                .build());
    }

    /** Backs the manual "Recompute Tiers" action — tier can go stale purely from time passing. */
    @Transactional
    public int recomputeAllTiersForOrg(UUID orgId) {
        List<Member> members = memberRepo.findByOrgId(orgId, Pageable.unpaged()).getContent();
        for (Member member : members) {
            recomputeTier(member.getId());
        }
        return members.size();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void validateMemberStatus(String status) {
        if (status != null && !MEMBER_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid member status: " + status
                    + ". Allowed: " + MEMBER_STATUSES);
        }
    }

    private void validatePaymentMethod(String method) {
        if (method != null && !PAYMENT_METHODS.contains(method)) {
            throw new IllegalArgumentException("Invalid payment method: " + method
                    + ". Allowed: " + PAYMENT_METHODS);
        }
    }

    private void validatePaymentStatus(String status) {
        if (status != null && !PAYMENT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid payment status: " + status
                    + ". Allowed: " + PAYMENT_STATUSES);
        }
    }
}
