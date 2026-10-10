package com.svivanrilski.svirerp.person;

import com.svivanrilski.svirerp.membership.Member;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reusable JPA specifications for filtering {@link Person} entities.
 * Supports filtering by name (first, last, or combined full name), email, and membership type.
 * All specified criteria are combined using an AND condition.
 */
public final class PersonSpecifications {

    private PersonSpecifications() {
    }

    /**
     * Builds an AND filter specification for {@link Person}.
     *
     * @param name               partial name compared against first name, last name, and full name
     * @param email              partial email compared against email address
     * @param membershipTypeId   optional membership type ID to match via linked {@link Member}
     * @param membershipTypeName optional membership type name to match via linked {@link Member}
     * @return JPA Specification combining all non-empty filter criteria
     */
    public static Specification<Person> filter(
            String name,
            String email,
            UUID membershipTypeId,
            String membershipTypeName) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                String pattern = toWildcardPattern(name);
                Predicate firstNameMatch = cb.like(cb.lower(root.get("firstName")), pattern);
                Predicate lastNameMatch = cb.like(cb.lower(root.get("lastName")), pattern);
                Expression<String> fullName = cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"));
                Predicate fullNameMatch = cb.like(cb.lower(fullName), pattern);
                predicates.add(cb.or(firstNameMatch, lastNameMatch, fullNameMatch));
            }

            if (email != null && !email.isBlank()) {
                String pattern = toWildcardPattern(email);
                predicates.add(cb.like(cb.lower(root.get("email")), pattern));
            }

            if (membershipTypeId != null || (membershipTypeName != null && !membershipTypeName.isBlank())) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<Member> memberRoot = subquery.from(Member.class);
                subquery.select(cb.literal(1));

                List<Predicate> memberPredicates = new ArrayList<>();
                memberPredicates.add(cb.equal(memberRoot.get("person"), root));

                if (membershipTypeId != null) {
                    memberPredicates.add(cb.equal(memberRoot.get("membershipType").get("id"), membershipTypeId));
                }
                if (membershipTypeName != null && !membershipTypeName.isBlank()) {
                    memberPredicates.add(cb.equal(
                            cb.lower(memberRoot.get("membershipType").get("name")),
                            membershipTypeName.trim().toLowerCase()));
                }

                subquery.where(cb.and(memberPredicates.toArray(new Predicate[0])));
                predicates.add(cb.exists(subquery));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Converts a user search input into a wildcard SQL LIKE pattern.
     * Appends '%' to the end of the user input if not already ending with '%'.
     * Existing '%' entered by the user are preserved for wildcard matching.
     *
     * @param input user-entered search text
     * @return lowercased wildcard pattern, or {@code null} if input is null/blank
     */
    public static String toWildcardPattern(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        if (!trimmed.endsWith("%")) {
            trimmed = trimmed + "%";
        }
        return trimmed.toLowerCase();
    }
}
