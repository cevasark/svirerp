package com.svivanrilski.svirerp.volunteer;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.person.Person;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Volunteer roster entry linking a Person to the Organisation. */
@Entity
@Table(name = "volunteer")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Volunteer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /** Optional alternate contact (e.g. family member or coordinator) to reach about this volunteer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_person_id")
    private Person contactPerson;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "volunteer_area_assignment",
        joinColumns = @JoinColumn(name = "volunteer_id"),
        inverseJoinColumns = @JoinColumn(name = "area_id"))
    @Builder.Default
    private Set<VolunteerArea> areas = new HashSet<>();

    @NotNull
    @Column(name = "onboard_date", nullable = false)
    private LocalDate onboardDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(length = 255)
    private String availability;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
