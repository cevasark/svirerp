package com.svivanrilski.svirerp.zeffyimport;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZeffyCampaignMappingRepository extends JpaRepository<ZeffyCampaignMapping, UUID> {

    @EntityGraph(attributePaths = {"fund"})
    List<ZeffyCampaignMapping> findAll();

    @EntityGraph(attributePaths = {"fund"})
    Optional<ZeffyCampaignMapping> findByCampaignTitleIgnoreCase(String campaignTitle);
}
