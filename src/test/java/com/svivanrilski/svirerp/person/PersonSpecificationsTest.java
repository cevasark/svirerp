package com.svivanrilski.svirerp.person;

import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MembershipType;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonSpecificationsTest {

    @Mock private Root<Person> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;

    @Mock private Path<String> firstNamePath;
    @Mock private Path<String> lastNamePath;
    @Mock private Path<String> emailPath;
    @Mock private Expression<String> lowerExpr;
    @Mock private Expression<String> concatExpr;
    @Mock private Predicate predicate1;
    @Mock private Predicate predicate2;
    @Mock private Predicate predicate3;
    @Mock private Predicate orPredicate;
    @Mock private Predicate andPredicate;
    @Mock private Predicate conjunctionPredicate;

    @Mock private Subquery<Integer> subquery;
    @Mock private Root<Member> memberRoot;
    @Mock private Path<Person> memberPersonPath;
    @Mock private Path<MembershipType> memberTypePath;
    @Mock private Path<UUID> memberTypeIdPath;

    @Test
    @DisplayName("toWildcardPattern appends % to text without trailing % and preserves existing wildcards")
    void toWildcardPattern_formatting() {
        assertThat(PersonSpecifications.toWildcardPattern("John")).isEqualTo("john%");
        assertThat(PersonSpecifications.toWildcardPattern("John%")).isEqualTo("john%");
        assertThat(PersonSpecifications.toWildcardPattern("%John")).isEqualTo("%john%");
        assertThat(PersonSpecifications.toWildcardPattern("%John%")).isEqualTo("%john%");
        assertThat(PersonSpecifications.toWildcardPattern("J%n")).isEqualTo("j%n%");
        assertThat(PersonSpecifications.toWildcardPattern("  Smith  ")).isEqualTo("smith%");
        assertThat(PersonSpecifications.toWildcardPattern(null)).isNull();
        assertThat(PersonSpecifications.toWildcardPattern("")).isNull();
        assertThat(PersonSpecifications.toWildcardPattern("   ")).isNull();
    }

    @Test
    @DisplayName("filter without criteria returns conjunction")
    void filter_noCriteria_returnsConjunction() {
        when(cb.conjunction()).thenReturn(conjunctionPredicate);

        Specification<Person> spec = PersonSpecifications.filter(null, null, null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunctionPredicate);
    }

    @Test
    @DisplayName("filter with name creates or-predicates for firstName, lastName, and fullName")
    void filter_withName_createsNamePredicates() {
        when(root.<String>get("firstName")).thenReturn(firstNamePath);
        when(root.<String>get("lastName")).thenReturn(lastNamePath);
        when(cb.lower(firstNamePath)).thenReturn(lowerExpr);
        when(cb.lower(lastNamePath)).thenReturn(lowerExpr);
        when(cb.concat(firstNamePath, " ")).thenReturn(concatExpr);
        when(cb.concat(concatExpr, lastNamePath)).thenReturn(concatExpr);
        when(cb.lower(concatExpr)).thenReturn(lowerExpr);

        when(cb.like(eq(lowerExpr), eq("john%"))).thenReturn(predicate1, predicate2, predicate3);
        when(cb.or(predicate1, predicate2, predicate3)).thenReturn(orPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        Specification<Person> spec = PersonSpecifications.filter("John", null, null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(andPredicate);
        verify(cb, times(3)).like(lowerExpr, "john%");
    }

    @Test
    @DisplayName("filter with email creates lower LIKE predicate")
    void filter_withEmail_createsEmailPredicate() {
        when(root.<String>get("email")).thenReturn(emailPath);
        when(cb.lower(emailPath)).thenReturn(lowerExpr);
        when(cb.like(eq(lowerExpr), eq("%test.org%"))).thenReturn(predicate1);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        Specification<Person> spec = PersonSpecifications.filter(null, "%test.org", null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(andPredicate);
        verify(cb).like(lowerExpr, "%test.org%");
    }

    @Test
    @DisplayName("filter with membershipTypeId creates EXISTS subquery on Member")
    @SuppressWarnings("unchecked")
    void filter_withMembershipTypeId_createsSubquery() {
        UUID typeId = UUID.randomUUID();

        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(Member.class)).thenReturn(memberRoot);
        when(cb.literal(1)).thenReturn(mock(Expression.class));
        when(memberRoot.<Person>get("person")).thenReturn(memberPersonPath);
        when(cb.equal(memberPersonPath, root)).thenReturn(predicate1);
        when(memberRoot.<MembershipType>get("membershipType")).thenReturn(memberTypePath);
        when(memberTypePath.<UUID>get("id")).thenReturn(memberTypeIdPath);
        when(cb.equal(memberTypeIdPath, typeId)).thenReturn(predicate2);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate3, andPredicate);
        when(cb.exists(subquery)).thenReturn(orPredicate);

        Specification<Person> spec = PersonSpecifications.filter(null, null, typeId, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(andPredicate);
        verify(cb).exists(subquery);
    }
}
