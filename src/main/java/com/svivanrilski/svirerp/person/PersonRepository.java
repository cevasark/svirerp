package com.svivanrilski.svirerp.person;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonRepository extends JpaRepository<Person, UUID>, JpaSpecificationExecutor<Person> {

    Optional<Person> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select p from Person p where lower(trim(p.email)) = lower(:email)")
    List<Person> findAllByNormalizedEmail(@Param("email") String email);
}
