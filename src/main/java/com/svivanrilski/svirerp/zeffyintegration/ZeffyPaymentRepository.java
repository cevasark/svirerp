package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZeffyPaymentRepository extends JpaRepository<ZeffyPayment, UUID> {

    Optional<ZeffyPayment> findByZeffyPaymentId(String zeffyPaymentId);

    @EntityGraph(attributePaths = {"person", "member"})
    List<ZeffyPayment> findByContactId(String contactId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ZeffyPayment p where p.zeffyPaymentId = :paymentId")
    Optional<ZeffyPayment> findByZeffyPaymentIdForUpdate(@Param("paymentId") String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ZeffyPayment p where p.id = :paymentId")
    Optional<ZeffyPayment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    @Query("""
            select distinct p.id from ZeffyPayment p
            where exists (select r.id from ZeffyRefund r
                          where r.zeffyPayment = p and r.correctionStatus = 'AWAITING_CORRECTION')
               or exists (select d.id from ZeffyDispute d
                          where d.zeffyPayment = p and d.correctionStatus = 'AWAITING_CORRECTION')
               or exists (select c.id from ZeffyPaymentChange c
                          where c.zeffyPayment = p and c.correctionStatus = 'AWAITING_CORRECTION')
            order by p.id
            """)
    java.util.List<UUID> findPaymentIdsAwaitingCorrection();

    @EntityGraph(attributePaths = {"person", "member", "memberPayment"})
    @Query("""
            select p from ZeffyPayment p
            where lower(p.status) = 'succeeded'
              and p.deletedAt is null
              and exists (
                  select c.id from ZeffyCampaign c
                  where c.zeffyCampaignId = p.campaignId
                    and c.mappingConfirmed = true
                    and c.processingAction = 'APPLY'
                    and c.grantsMembershipCredit = true
              )
            order by p.paymentCreatedAt asc, p.zeffyPaymentId asc
            """)
    List<ZeffyPayment> findMembershipCreditPayments();
}
