package com.svivanrilski.svirerp.governance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutes, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the
    // controller layer serializes the response, so the lazy `org` association
    // must be eagerly fetched here or Jackson hits a LazyInitializationException.
    // ActionItem has no inverse @OneToMany back to MeetingMinutes, so the
    // "has an open action item" filter needs an EXISTS subquery rather than a
    // derived-query collection traversal. openActionItemsOnly=false makes that
    // clause a no-op instead of a separate query method for every combination
    // of the two filters.
    @Query("""
        SELECT DISTINCT mm FROM MeetingMinutes mm
        WHERE (:fromDate IS NULL OR mm.meetingDate >= :fromDate)
        AND (:openActionItemsOnly = false OR EXISTS (
            SELECT 1 FROM ActionItem ai WHERE ai.meetingMinutes = mm AND ai.status <> 'done'
        ))
        """)
    Page<MeetingMinutes> search(@Param("fromDate") LocalDate fromDate,
                                 @Param("openActionItemsOnly") boolean openActionItemsOnly,
                                 Pageable pageable);
}
