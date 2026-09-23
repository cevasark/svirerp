package com.svivanrilski.svirerp.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the controller layer
    // serializes the response, so lazy associations must be eagerly fetched here.
    @EntityGraph(attributePaths = {"glAccount", "glAccount.parentAccount"})
    @Override
    Optional<BankAccount> findById(UUID id);

    @EntityGraph(attributePaths = {"glAccount", "glAccount.parentAccount"})
    Page<BankAccount> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"glAccount", "glAccount.parentAccount"})
    Page<BankAccount> findByIsActive(boolean isActive, Pageable pageable);
}
