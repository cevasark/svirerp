package com.svivanrilski.svirerp.zeffyimport;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ZeffyImportBatchRepository extends JpaRepository<ZeffyImportBatch, UUID> {

    // Deliberately no hardcoded OrderBy in the method name — a derived query's own baked-in order
    // would apply in addition to (not be overridden by) any client-supplied Pageable Sort, silently
    // neutralizing column-header sorting. ZeffyImportService#findBatches supplies the default
    // uploadedAt-desc sort itself, only when the caller didn't ask for a specific one.
    Page<ZeffyImportBatch> findAll(Pageable pageable);
}
