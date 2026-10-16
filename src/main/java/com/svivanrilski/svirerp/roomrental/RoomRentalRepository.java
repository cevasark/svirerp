package com.svivanrilski.svirerp.roomrental;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRentalRepository extends JpaRepository<RoomRental, UUID> {

    @EntityGraph(attributePaths = {"renterPerson", "contactTrustee", "contactTrustee.person"})
    @Override
    Optional<RoomRental> findById(UUID id);

    @EntityGraph(attributePaths = {"renterPerson", "contactTrustee", "contactTrustee.person"})
    Page<RoomRental> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"renterPerson", "contactTrustee", "contactTrustee.person"})
    Page<RoomRental> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"renterPerson", "contactTrustee", "contactTrustee.person"})
    @Query("SELECT r FROM RoomRental r WHERE (:roomName IS NULL OR r.roomName = :roomName) " +
           "AND r.startTime < :end AND r.endTime > :start ORDER BY r.startTime ASC")
    List<RoomRental> findByRoomAndDateRange(@Param("roomName") String roomName,
                                           @Param("start") OffsetDateTime start,
                                           @Param("end") OffsetDateTime end);

    @EntityGraph(attributePaths = {"renterPerson", "contactTrustee", "contactTrustee.person"})
    @Query("SELECT r FROM RoomRental r WHERE r.roomName = :roomName AND r.status NOT IN ('cancelled') " +
           "AND (:excludeId IS NULL OR r.id != :excludeId) " +
           "AND (r.startTime < :endTime AND r.endTime > :startTime)")
    List<RoomRental> findOverlappingRentals(@Param("roomName") String roomName,
                                           @Param("startTime") OffsetDateTime startTime,
                                           @Param("endTime") OffsetDateTime endTime,
                                           @Param("excludeId") UUID excludeId);
}
