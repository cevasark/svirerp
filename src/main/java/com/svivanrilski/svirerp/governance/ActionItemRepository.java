package com.svivanrilski.svirerp.governance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActionItemRepository extends JpaRepository<ActionItem, UUID> {

    // Deliberately unpaginated — a meeting's action items are meant to be seen
    // as a whole live list while trustees are discussing, not paged through.
    @EntityGraph(attributePaths = {"assigneeTrustee", "assigneeTrustee.person", "meetingMinutes"})
    List<ActionItem> findByMeetingMinutesIdOrderByCreatedAt(UUID meetingMinutesId);

    @EntityGraph(attributePaths = {"assigneeTrustee", "assigneeTrustee.person", "meetingMinutes"})
    @Override
    Optional<ActionItem> findById(UUID id);
}
