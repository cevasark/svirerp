package com.svivanrilski.svirerp.governance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, UUID> {

    Page<Committee> findByIsActive(boolean isActive, Pageable pageable);
}
