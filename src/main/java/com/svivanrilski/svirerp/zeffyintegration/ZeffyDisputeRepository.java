package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZeffyDisputeRepository extends JpaRepository<ZeffyDispute, UUID> {
    Optional<ZeffyDispute> findByZeffyDisputeId(String zeffyDisputeId);

    @EntityGraph(attributePaths = "correctionJournalEntry")
    List<ZeffyDispute> findByZeffyPayment_IdOrderByDisputeCreatedAtAsc(UUID paymentId);
}
