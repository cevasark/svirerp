package com.svivanrilski.svirerp.membership;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers recomputeTier's use of TierCalculator's new status field (see TierCalculatorTest for the
 * chaining algorithm itself), and findOrCreateFollowerMember's join-date self-heal — a bulk
 * historical import processes rows/files in upload order, not necessarily chronological, so the
 * transaction that happens to be committed first for a person isn't necessarily their true
 * earliest. All collaborators are mocked; this doesn't stand up a Spring context or a real DB.
 */
@ExtendWith(MockitoExtension.class)
class MembershipServiceTierTest {

    @Mock private MembershipTypeRepository typeRepo;
    @Mock private MemberRepository memberRepo;
    @Mock private MemberPaymentRepository paymentRepo;
    @Mock private PersonService personService;

    @InjectMocks
    private MembershipService service;

    private UUID memberId;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
    }

    private MembershipType typeOf(String name) {
        return MembershipType.builder().id(UUID.randomUUID()).name(name).build();
    }

    @Test
    void recomputeTier_lapsedMember_setsInactiveButKeepsTier() {
        Member member = Member.builder().id(memberId).status("active").build();
        when(memberRepo.findById(memberId)).thenReturn(Optional.of(member));
        when(typeRepo.existsByNameIgnoreCase(anyString())).thenReturn(true);

        LocalDate longAgo = LocalDate.now(java.time.ZoneId.of("America/Chicago")).minusMonths(20);
        MemberPayment payment = MemberPayment.builder()
                .amount(new BigDecimal("1000.00")).paymentDate(longAgo).status("completed").build();
        when(paymentRepo.findByMemberIdAndStatus(memberId, "completed")).thenReturn(List.of(payment));

        MembershipType benefactorType = typeOf(TierCalculator.BENEFACTOR);
        when(typeRepo.findByNameIgnoreCase(TierCalculator.BENEFACTOR))
                .thenReturn(Optional.of(benefactorType));
        when(memberRepo.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member result = service.recomputeTier(memberId);

        assertThat(result.getMembershipType()).isEqualTo(benefactorType);
        assertThat(result.getStatus()).isEqualTo("inactive");
        assertThat(result.getExpiryDate()).isEqualTo(longAgo.plusYears(1));
    }

    @Test
    void recomputeTier_noPaymentHistory_preservesActiveFollowerStatus() {
        MembershipType existingType = typeOf(TierCalculator.FOLLOWER);
        Member member = Member.builder().id(memberId).status("active")
                .membershipType(existingType).build();
        when(memberRepo.findById(memberId)).thenReturn(Optional.of(member));
        when(typeRepo.existsByNameIgnoreCase(anyString())).thenReturn(true);
        when(paymentRepo.findByMemberIdAndStatus(memberId, "completed")).thenReturn(List.of());
        when(memberRepo.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member result = service.recomputeTier(memberId);

        assertThat(result.getStatus()).isEqualTo("active");
        assertThat(result.getMembershipType()).isEqualTo(existingType); // untouched
    }

    @Test
    void findOrCreateFollowerMember_backdatesJoinDate_whenEarlierTransactionSeen() {
        UUID personId = UUID.randomUUID();
        LocalDate recordedJoin = LocalDate.of(2026, 7, 1);
        LocalDate trueEarlierTransaction = LocalDate.of(2024, 10, 9);
        Member existing = Member.builder().id(memberId).joinDate(recordedJoin).status("active").build();
        when(memberRepo.findByPersonId(personId)).thenReturn(Optional.of(existing));
        when(memberRepo.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member result = service.findOrCreateFollowerMember(personId, trueEarlierTransaction);

        assertThat(result.getJoinDate()).isEqualTo(trueEarlierTransaction);
        verify(memberRepo).save(existing);
    }

    @Test
    void findOrCreateFollowerMember_doesNotDriftForward_whenTransactionIsLater() {
        UUID personId = UUID.randomUUID();
        LocalDate recordedJoin = LocalDate.of(2024, 10, 9);
        LocalDate laterTransaction = LocalDate.of(2026, 7, 1);
        Member existing = Member.builder().id(memberId).joinDate(recordedJoin).status("active").build();
        when(memberRepo.findByPersonId(personId)).thenReturn(Optional.of(existing));

        Member result = service.findOrCreateFollowerMember(personId, laterTransaction);

        assertThat(result.getJoinDate()).isEqualTo(recordedJoin); // unchanged
        verify(memberRepo, never()).save(any());
    }
}
