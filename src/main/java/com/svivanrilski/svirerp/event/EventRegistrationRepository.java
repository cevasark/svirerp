package com.svivanrilski.svirerp.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

    @EntityGraph(attributePaths = {"event", "event.createdBy", "person"})
    @Override
    Optional<EventRegistration> findById(UUID id);

    @EntityGraph(attributePaths = {"event", "event.createdBy", "person"})
    Page<EventRegistration> findByEventId(UUID eventId, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "event.createdBy", "person"})
    Page<EventRegistration> findByPersonId(UUID personId, Pageable pageable);

    boolean existsByEventIdAndPersonId(UUID eventId, UUID personId);
}
