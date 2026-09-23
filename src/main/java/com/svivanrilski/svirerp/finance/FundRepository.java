package com.svivanrilski.svirerp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FundRepository extends JpaRepository<Fund, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the controller layer
    // serializes the response, so lazy associations must be eagerly fetched here.
    Page<Fund> findAll(Pageable pageable);

    Page<Fund> findByIsActive(boolean isActive, Pageable pageable);

    /** Unpaginated — Funds Overview needs every active fund at once, not a page of them. */
    List<Fund> findByIsActiveOrderByFundName(boolean isActive);
}
