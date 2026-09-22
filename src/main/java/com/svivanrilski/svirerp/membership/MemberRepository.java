package com.svivanrilski.svirerp.membership;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the
    // controller layer serializes the response, so these LAZY associations
    // must be eagerly fetched here or Jackson hits a LazyInitializationException.
    @EntityGraph(attributePaths = {"person", "org", "membershipType"})
    @Override
    Optional<Member> findById(UUID id);

    @EntityGraph(attributePaths = {"person", "org", "membershipType"})
    Page<Member> findByOrgId(UUID orgId, Pageable pageable);

    @EntityGraph(attributePaths = {"person", "org", "membershipType"})
    Page<Member> findByOrgIdAndStatus(UUID orgId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"person", "org", "membershipType"})
    Page<Member> findByOrgIdAndMembershipTypeId(UUID orgId, UUID membershipTypeId, Pageable pageable);

    @EntityGraph(attributePaths = {"person", "org", "membershipType"})
    Page<Member> findByOrgIdAndStatusAndMembershipTypeId(
            UUID orgId, String status, UUID membershipTypeId, Pageable pageable);

    List<Member> findByExpiryDateBefore(LocalDate date);

    boolean existsByPersonIdAndOrgIdAndStatus(UUID personId, UUID orgId, String status);

    /** A person may hold at most one membership per organisation, regardless of status. */
    boolean existsByPersonIdAndOrgId(UUID personId, UUID orgId);

    @EntityGraph(attributePaths = {"person", "org", "membershipType"})
    Optional<Member> findByPersonIdAndOrgId(UUID personId, UUID orgId);

    long countByOrgIdAndStatusAndMembershipType_NameIgnoreCase(UUID orgId, String status, String membershipTypeName);

    long countByOrgIdAndMembershipType_NameIgnoreCase(UUID orgId, String membershipTypeName);

    long countByOrgId(UUID orgId);
}
