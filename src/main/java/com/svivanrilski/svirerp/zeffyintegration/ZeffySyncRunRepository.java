package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZeffySyncRunRepository extends JpaRepository<ZeffySyncRun, UUID> {

    @EntityGraph(attributePaths = "previewRun")
    Optional<ZeffySyncRun> findFirstBySyncTypeOrderByStartedAtDesc(String syncType);

    @EntityGraph(attributePaths = "previewRun")
    Optional<ZeffySyncRun> findFirstBySyncTypeAndExecutionModeOrderByStartedAtDesc(
            String syncType, String executionMode);

    @EntityGraph(attributePaths = "previewRun")
    @Query("select r from ZeffySyncRun r where r.id = :id")
    Optional<ZeffySyncRun> findDetailedById(@Param("id") UUID id);

    Page<ZeffySyncRun> findBySyncType(String syncType, Pageable pageable);
}
