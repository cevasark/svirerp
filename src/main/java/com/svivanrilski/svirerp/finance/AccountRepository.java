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
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // spring.jpa.open-in-view=false closes the Hibernate session before the controller layer
    // serializes the response, so lazy associations must be eagerly fetched here.
    @EntityGraph(attributePaths = {"parentAccount"})
    @Override
    Optional<Account> findById(UUID id);

    @EntityGraph(attributePaths = {"parentAccount"})
    Page<Account> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"parentAccount"})
    Page<Account> findByAccountType(String accountType, Pageable pageable);

    /** Returns root accounts (no parent) for a given org — used to build the account tree. */
    List<Account> findByParentAccountIsNull();

    boolean existsByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = {"parentAccount"})
    Optional<Account> findByAccountNumber(String accountNumber);
}
