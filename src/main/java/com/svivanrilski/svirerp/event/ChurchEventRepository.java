package com.svivanrilski.svirerp.event;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChurchEventRepository extends JpaRepository<ChurchEvent, UUID> {

    @EntityGraph(attributePaths = {"calendarEvent", "calendarEvent.createdBy"})
    @Override
    Optional<ChurchEvent> findById(UUID id);

    @EntityGraph(attributePaths = {"calendarEvent", "calendarEvent.createdBy"})
    Optional<ChurchEvent> findByCalendarEventId(UUID calendarEventId);

    boolean existsByCalendarEventId(UUID calendarEventId);
}
