package com.svivanrilski.svirerp.governance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A comment on a {@link Project}. {@code authorName} is stamped server-side from the logged-in
 *  session (Google display name, or the local-admin username) — never client-supplied — since this
 *  app has no separate {@code User} table to reference (see AuthController#me). */
@Entity
@Table(name = "project_comment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @NotBlank
    @Column(name = "author_name", nullable = false, length = 255)
    private String authorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
