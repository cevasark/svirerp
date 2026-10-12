package com.svivanrilski.svirerp.membership;

import com.svivanrilski.svirerp.person.PersonSpecifications;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reusable JPA specifications for filtering {@link Member} entities.
 * Supports filtering by status, membership type ID, and person email address with wildcard matching.
 * All specified criteria are combined using an AND condition.
 */
public final class MemberSpecifications {

    private MemberSpecifications() {
    }

    /**
     * Builds an AND filter specification for {@link Member}.
     *
     * @param status           optional member status ('active', 'inactive', etc.)
     * @param membershipTypeId optional membership type ID
     * @param email            optional email compared (case-insensitive, trailing wildcard) against the member's person email
     * @return JPA Specification combining all non-empty filter criteria
     */
    public static Specification<Member> filter(String status, UUID membershipTypeId, String email) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }

            if (membershipTypeId != null) {
                predicates.add(cb.equal(root.get("membershipType").get("id"), membershipTypeId));
            }

            if (email != null && !email.isBlank()) {
                String pattern = PersonSpecifications.toWildcardPattern(email);
                predicates.add(cb.like(cb.lower(root.get("person").get("email")), pattern));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
