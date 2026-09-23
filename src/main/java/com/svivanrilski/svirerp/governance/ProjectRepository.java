package com.svivanrilski.svirerp.governance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the
    // controller layer serializes the response, so these LAZY associations
    // must be eagerly fetched here or Jackson hits a LazyInitializationException.
    @EntityGraph(attributePaths = {"assignee"})
    @Override
    Optional<Project> findById(UUID id);

    @EntityGraph(attributePaths = {"assignee"})
    Page<Project> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"assignee"})
    Page<Project> findByStatus(String status, Pageable pageable);
}
