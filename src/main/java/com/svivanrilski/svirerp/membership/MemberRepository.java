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
    @EntityGraph(attributePaths = {"person", "membershipType"})
    @Override
    Optional<Member> findById(UUID id);

    @EntityGraph(attributePaths = {"person", "membershipType"})
    Page<Member> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"person", "membershipType"})
    Page<Member> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"person", "membershipType"})
    Page<Member> findByMembershipTypeId(UUID membershipTypeId, Pageable pageable);

    @EntityGraph(attributePaths = {"person", "membershipType"})
    Page<Member> findByStatusAndMembershipTypeId(
            String status, UUID membershipTypeId, Pageable pageable);

    List<Member> findByExpiryDateBefore(LocalDate date);

    boolean existsByPersonIdAndStatus(UUID personId, String status);

    /** A person may hold at most one membership per organisation, regardless of status. */
    boolean existsByPersonId(UUID personId);

    @EntityGraph(attributePaths = {"person", "membershipType"})
    Optional<Member> findByPersonId(UUID personId);

    long countByStatusAndMembershipType_NameIgnoreCase(String status, String membershipTypeName);

    long countByMembershipType_NameIgnoreCase(String membershipTypeName);
}
