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
public interface MemberPaymentRepository extends JpaRepository<MemberPayment, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the
    // controller layer serializes the response, so the lazy `member` association
    // (and everything nested under it) must be eagerly fetched here or Jackson
    // hits a LazyInitializationException.
    @EntityGraph(attributePaths = {"member", "member.person", "member.membershipType"})
    @Override
    Optional<MemberPayment> findById(UUID id);

    @EntityGraph(attributePaths = {"member", "member.person", "member.membershipType"})
    Page<MemberPayment> findByMemberId(UUID memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"member", "member.person", "member.membershipType"})
    Page<MemberPayment> findByMemberIdAndStatus(UUID memberId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"member", "member.person", "member.membershipType"})
    Page<MemberPayment> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"member", "member.person", "member.membershipType"})
    Page<MemberPayment> findByPaymentDateGreaterThanEqual(LocalDate fromDate, Pageable pageable);

    /** Internal use only (tier computation reads amount/paymentDate directly) — never serialized, no EntityGraph needed. */
    List<MemberPayment> findByMemberIdAndStatus(UUID memberId, String status);
}
