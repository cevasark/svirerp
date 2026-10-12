package com.svivanrilski.svirerp.membership;

import com.svivanrilski.svirerp.person.Person;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberSpecificationsTest {

    @Mock private Root<Member> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;

    @Mock private Path<String> statusPath;
    @Mock private Path<MembershipType> typePath;
    @Mock private Path<UUID> typeIdPath;
    @Mock private Path<Person> personPath;
    @Mock private Path<String> emailPath;
    @Mock private Expression<String> lowerExpr;
    @Mock private Predicate statusPredicate;
    @Mock private Predicate typePredicate;
    @Mock private Predicate emailPredicate;
    @Mock private Predicate andPredicate;
    @Mock private Predicate conjunctionPredicate;

    @Test
    @DisplayName("filter without criteria returns conjunction")
    void filter_noCriteria_returnsConjunction() {
        when(cb.conjunction()).thenReturn(conjunctionPredicate);

        Predicate result = MemberSpecifications.filter(null, null, "  ").toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunctionPredicate);
    }

    @Test
    @DisplayName("filter with email matches lower(person.email) using wildcard pattern")
    void filter_withEmail_createsPersonEmailLike() {
        when(root.<Person>get("person")).thenReturn(personPath);
        when(personPath.<String>get("email")).thenReturn(emailPath);
        when(cb.lower(emailPath)).thenReturn(lowerExpr);
        when(cb.like(lowerExpr, "%gmail%")).thenReturn(emailPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        Predicate result = MemberSpecifications.filter(null, null, "%Gmail").toPredicate(root, query, cb);

        assertThat(result).isSameAs(andPredicate);
        verify(cb).like(lowerExpr, "%gmail%");
    }

    @Test
    @DisplayName("filter combines status, membership type and email with AND")
    void filter_allCriteria_combinedWithAnd() {
        UUID typeId = UUID.randomUUID();
        when(root.<String>get("status")).thenReturn(statusPath);
        when(cb.equal(statusPath, "active")).thenReturn(statusPredicate);
        when(root.<MembershipType>get("membershipType")).thenReturn(typePath);
        when(typePath.<UUID>get("id")).thenReturn(typeIdPath);
        when(cb.equal(typeIdPath, typeId)).thenReturn(typePredicate);
        when(root.<Person>get("person")).thenReturn(personPath);
        when(personPath.<String>get("email")).thenReturn(emailPath);
        when(cb.lower(emailPath)).thenReturn(lowerExpr);
        when(cb.like(lowerExpr, "john%")).thenReturn(emailPredicate);
        when(cb.and(statusPredicate, typePredicate, emailPredicate)).thenReturn(andPredicate);

        Predicate result = MemberSpecifications.filter("active", typeId, "John").toPredicate(root, query, cb);

        assertThat(result).isSameAs(andPredicate);
    }

    @Test
    @DisplayName("service uses specification query (with totalPaid tie-breaker) when email is supplied")
    @SuppressWarnings("unchecked")
    void service_withEmail_usesSpecificationAndStableSort() {
        MemberRepository memberRepo = mock(MemberRepository.class);
        MembershipService service = new MembershipService(null, memberRepo, null, null);
        PageRequest requested = PageRequest.of(0, 10, Sort.by("totalPaid").descending());
        PageRequest stable = PageRequest.of(0, 10, Sort.by("totalPaid").descending().and(Sort.by("id")));
        when(memberRepo.findAll(any(Specification.class), eq(stable))).thenReturn(Page.empty(stable));

        service.findAllMembers("active", null, "john", requested);

        ArgumentCaptor<Specification<Member>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(memberRepo).findAll(captor.capture(), eq(stable));
        assertThat(captor.getValue()).isNotNull();
        verify(memberRepo, never()).findByStatus(any(), any());
    }
}
