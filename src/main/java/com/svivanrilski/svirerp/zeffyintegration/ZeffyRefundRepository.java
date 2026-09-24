package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZeffyRefundRepository extends JpaRepository<ZeffyRefund, UUID> {
    Optional<ZeffyRefund> findByZeffyRefundId(String zeffyRefundId);

    @EntityGraph(attributePaths = "correctionJournalEntry")
    List<ZeffyRefund> findByZeffyPayment_IdOrderByRefundCreatedAtAsc(UUID paymentId);
}
