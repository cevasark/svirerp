package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZeffySyncRunRepository extends JpaRepository<ZeffySyncRun, UUID> {

    Optional<ZeffySyncRun> findFirstBySyncTypeOrderByStartedAtDesc(String syncType);

    Page<ZeffySyncRun> findBySyncType(String syncType, Pageable pageable);
}
