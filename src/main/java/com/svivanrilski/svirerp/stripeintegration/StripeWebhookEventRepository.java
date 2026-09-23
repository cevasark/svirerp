package com.svivanrilski.svirerp.stripeintegration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, UUID> {

    boolean existsByStripeEventId(String stripeEventId);

    Optional<StripeWebhookEvent> findByStripeEventId(String stripeEventId);

    @EntityGraph(attributePaths = {
        "person",
        "member", "member.person", "member.membershipType",
        "memberPayment",
        "serviceRequest", "serviceRequest.requestorPerson",
        "journalEntry", "journalEntry.payer", "journalEntry.categoryAccount", "journalEntry.fund",
    })
    @Override
    Optional<StripeWebhookEvent> findById(UUID id);

    @EntityGraph(attributePaths = {
        "person",
        "member", "member.person", "member.membershipType",
        "memberPayment",
        "serviceRequest", "serviceRequest.requestorPerson",
        "journalEntry", "journalEntry.payer", "journalEntry.categoryAccount", "journalEntry.fund",
    })
    Page<StripeWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
