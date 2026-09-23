package com.svivanrilski.svirerp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    @EntityGraph(attributePaths = {"requestorPerson", "churchEvent", "churchEvent.calendarEvent"})
    @Override
    Optional<ServiceRequest> findById(UUID id);

    @EntityGraph(attributePaths = {"requestorPerson", "churchEvent", "churchEvent.calendarEvent"})
    Page<ServiceRequest> findAll(Pageable pageable);
}
