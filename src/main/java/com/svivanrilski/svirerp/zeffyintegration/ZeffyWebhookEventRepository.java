package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZeffyWebhookEventRepository extends JpaRepository<ZeffyWebhookEvent, UUID>,
        JpaSpecificationExecutor<ZeffyWebhookEvent> {

    Optional<ZeffyWebhookEvent> findByZeffyEventId(String zeffyEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ZeffyWebhookEvent e where e.zeffyEventId = :eventId")
    Optional<ZeffyWebhookEvent> findByZeffyEventIdForUpdate(@Param("eventId") String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ZeffyWebhookEvent e where e.id = :id")
    Optional<ZeffyWebhookEvent> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"zeffyPayment", "zeffyPayment.mappedFund",
            "zeffyPayment.mappedAccount", "zeffyPayment.person", "zeffyPayment.member",
            "zeffyPayment.memberPayment", "zeffyPayment.journalEntry", "zeffyContact",
            "zeffyContact.person"})
    @Query("select e from ZeffyWebhookEvent e where e.id = :id")
    Optional<ZeffyWebhookEvent> findDetailedById(@Param("id") UUID id);
}
