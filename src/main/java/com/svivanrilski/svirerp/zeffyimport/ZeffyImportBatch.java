package com.svivanrilski.svirerp.zeffyimport;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per uploaded Zeffy CSV file. Created (status=previewed) as soon as the file is parsed;
 * its rows (see ZeffyImportRow) are the actual preview/commit staging area. Flipped to
 * status=committed once ZeffyImportService#commitImport has applied all ready rows.
 */
@Entity
@Table(name = "zeffy_import_batch")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeffyImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    /** Allowed values (DB CHECK): previewed, committed. */
    @Column(nullable = false, length = 20)
    private String status = "previewed";

    @Column(name = "row_count", nullable = false)
    private Integer rowCount = 0;

    @Column(name = "committed_at")
    private OffsetDateTime committedAt;

    @PrePersist
    private void prePersist() {
        if (uploadedAt == null) uploadedAt = OffsetDateTime.now();
    }
}
