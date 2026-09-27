package com.svivanrilski.svirerp.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svivanrilski.svirerp.person.PersonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTotalPaidTest {
    @Mock private MembershipTypeRepository typeRepo;
    @Mock private MemberRepository memberRepo;
    @Mock private MemberPaymentRepository paymentRepo;
    @Mock private PersonService personService;
    @InjectMocks private MembershipService service;

    @Test
    void amountSortAddsStableTieBreakerBeforeRepositoryPaginationForAllFilters() {
        UUID type = UUID.randomUUID();
        PageRequest requested = PageRequest.of(2, 10, Sort.by("totalPaid").descending());
        PageRequest stable = PageRequest.of(2, 10,
                Sort.by("totalPaid").descending().and(Sort.by("id")));
        when(memberRepo.findAll(stable)).thenReturn(Page.empty(stable));
        when(memberRepo.findByStatus("active", stable)).thenReturn(Page.empty(stable));
        when(memberRepo.findByMembershipTypeId(type, stable)).thenReturn(Page.empty(stable));
        when(memberRepo.findByStatusAndMembershipTypeId("active", type, stable)).thenReturn(Page.empty(stable));

        assertThat(service.findAllMembers(null, null, requested).getPageable()).isEqualTo(stable);
        assertThat(service.findAllMembers("active", null, requested).getPageable()).isEqualTo(stable);
        assertThat(service.findAllMembers(null, type, requested).getPageable()).isEqualTo(stable);
        assertThat(service.findAllMembers("active", type, requested).getPageable()).isEqualTo(stable);
    }

    @Test
    void amountSortPreservesExplicitTieBreakerAndOtherSorts() {
        Pageable explicit = PageRequest.of(0, 25,
                Sort.by("totalPaid").and(Sort.by("id").descending()));
        Pageable date = PageRequest.of(0, 25, Sort.by("expiryDate"));
        service.findAllMembers(null, null, explicit);
        service.findAllMembers(null, null, date);
        verify(memberRepo).findAll(explicit);
        verify(memberRepo).findAll(date);
    }

    @Test
    void totalPaidIsReturnedAsANumberButCannotBeSetByAnApiRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Member member = Member.builder().totalPaid(new BigDecimal("1250.25")).build();
        assertThat(mapper.valueToTree(member).get("totalPaid").decimalValue())
                .isEqualByComparingTo("1250.25");
        Member request = mapper.readValue("{\"totalPaid\":999999}", Member.class);
        assertThat(request.getTotalPaid()).isNull();
    }
}
