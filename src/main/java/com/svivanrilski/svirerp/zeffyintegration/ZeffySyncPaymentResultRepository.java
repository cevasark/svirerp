package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZeffySyncPaymentResultRepository extends JpaRepository<ZeffySyncPaymentResult, UUID> {

    @EntityGraph(attributePaths = {"zeffyPayment"})
    Page<ZeffySyncPaymentResult> findBySyncRunId(UUID syncRunId, Pageable pageable);

    Optional<ZeffySyncPaymentResult> findBySyncRunIdAndZeffyPaymentId(
            UUID syncRunId, String zeffyPaymentId);

    @EntityGraph(attributePaths = {"zeffyPayment"})
    List<ZeffySyncPaymentResult> findBySyncRunId(UUID syncRunId);

    long countBySyncRunId(UUID syncRunId);
}
