package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZeffyPaymentChangeRepository extends JpaRepository<ZeffyPaymentChange, UUID> {
    Optional<ZeffyPaymentChange> findByWebhookEvent_Id(UUID eventId);
    List<ZeffyPaymentChange> findByZeffyPayment_IdOrderByObservedAtDesc(UUID paymentId);
}
