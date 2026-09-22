package com.svivanrilski.svirerp.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.svivanrilski.svirerp.organization.OrganizationService;
import com.svivanrilski.svirerp.person.PersonService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Covers getMemberSummary's tier/status count mapping — see TierCalculator's class doc for why
 *  "Member"/"Benefactor"/"Follower" are stored as the membershipType name. All collaborators
 *  mocked; doesn't stand up a Spring context or a real DB. */
@ExtendWith(MockitoExtension.class)
class MembershipServiceSummaryTest {

    @Mock private MembershipTypeRepository typeRepo;
    @Mock private MemberRepository memberRepo;
    @Mock private MemberPaymentRepository paymentRepo;
    @Mock private OrganizationService orgService;
    @Mock private PersonService personService;

    @InjectMocks
    private MembershipService service;

    @Test
    void getMemberSummary_mapsEachCountToTheRightTierAndStatus() {
        UUID orgId = UUID.randomUUID();
        when(memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "active", TierCalculator.MEMBER))
                .thenReturn(10L);
        when(memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "inactive", TierCalculator.MEMBER))
                .thenReturn(3L);
        when(memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "active", TierCalculator.BENEFACTOR))
                .thenReturn(2L);
        when(memberRepo.countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(orgId, "inactive", TierCalculator.BENEFACTOR))
                .thenReturn(1L);
        when(memberRepo.countByOrgIdAndMembershipType_NameIgnoreCase(orgId, TierCalculator.FOLLOWER))
                .thenReturn(5L);
        when(memberRepo.countByOrgId(orgId)).thenReturn(21L);

        MembershipService.MemberSummary summary = service.getMemberSummary(orgId);

        assertThat(summary.activeMembers()).isEqualTo(10L);
        assertThat(summary.inactiveMembers()).isEqualTo(3L);
        assertThat(summary.activeBenefactors()).isEqualTo(2L);
        assertThat(summary.inactiveBenefactors()).isEqualTo(1L);
        assertThat(summary.followers()).isEqualTo(5L);
        assertThat(summary.totalMembers()).isEqualTo(21L);
    }
}
