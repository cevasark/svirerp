package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZeffyCampaignRepository extends JpaRepository<ZeffyCampaign, UUID> {

    Optional<ZeffyCampaign> findByZeffyCampaignId(String zeffyCampaignId);

    @Override
    @EntityGraph(attributePaths = {"fund", "categoryAccount"})
    Page<ZeffyCampaign> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"fund", "categoryAccount"})
    @Query("SELECT c FROM ZeffyCampaign c WHERE c.zeffyCampaignId = :campaignId")
    Optional<ZeffyCampaign> findWithMappingByZeffyCampaignId(@Param("campaignId") String campaignId);

    long countByMappingConfirmedTrue();

    long countByMappingConfirmedFalseAndStatusIgnoreCaseAndIsArchivedFalseAndZeffyDeletedAtIsNull(String status);
}
