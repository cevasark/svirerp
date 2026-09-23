package com.svivanrilski.svirerp.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventResourceRepository extends JpaRepository<EventResource, UUID> {

    @EntityGraph(attributePaths = {"event", "event.createdBy"})
    @Override
    Optional<EventResource> findById(UUID id);

    @EntityGraph(attributePaths = {"event", "event.createdBy"})
    Page<EventResource> findByEventId(UUID eventId, Pageable pageable);
}
