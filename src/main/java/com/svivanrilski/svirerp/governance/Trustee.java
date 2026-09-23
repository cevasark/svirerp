package com.svivanrilski.svirerp.governance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.person.Person;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Board trustee with term tracking and officer designation. */
@Entity
@Table(name = "trustee")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trustee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(length = 100)
    private String title;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String role;

    @NotNull
    @Column(name = "term_start", nullable = false)
    private LocalDate termStart;

    @Column(name = "term_end")
    private LocalDate termEnd;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_officer", nullable = false)
    private Boolean isOfficer = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
