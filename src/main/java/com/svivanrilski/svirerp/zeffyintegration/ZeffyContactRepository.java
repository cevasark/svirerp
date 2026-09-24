package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZeffyContactRepository extends JpaRepository<ZeffyContact, UUID> {

    @EntityGraph(attributePaths = "person")
    Optional<ZeffyContact> findByZeffyContactId(String zeffyContactId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "person")
    @Query("select c from ZeffyContact c where c.zeffyContactId = :contactId")
    Optional<ZeffyContact> findByZeffyContactIdForUpdate(@Param("contactId") String contactId);

    @EntityGraph(attributePaths = "person")
    List<ZeffyContact> findByPerson_IdInOrderByZeffyContactId(Collection<UUID> personIds);

    long countByProcessingStatus(String processingStatus);
}
