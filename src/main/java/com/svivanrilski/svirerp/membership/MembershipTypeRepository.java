package com.svivanrilski.svirerp.membership;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipTypeRepository extends JpaRepository<MembershipType, UUID> {

    Page<MembershipType> findByIsActive(boolean isActive, Pageable pageable);

    /** Resolves the human-readable name used in the member import template to an actual type. */
    Optional<MembershipType> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
