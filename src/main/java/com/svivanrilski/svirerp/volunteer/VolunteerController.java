package com.svivanrilski.svirerp.volunteer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VolunteerController {

    private final VolunteerService service;

    // ── Volunteer ─────────────────────────────────────────────────────────────

    @GetMapping("/api/volunteers")
    public Page<Volunteer> listVolunteers(
            @RequestParam(required = false) UUID areaId, Pageable pageable) {
        return service.findAll(areaId, pageable);
    }

    @GetMapping("/api/volunteers/{id}")
    public Volunteer getVolunteer(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/api/volunteers")
    public ResponseEntity<Volunteer> createVolunteer(@Valid @RequestBody Volunteer volunteer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(volunteer));
    }

    @PutMapping("/api/volunteers/{id}")
    public Volunteer updateVolunteer(@PathVariable UUID id, @Valid @RequestBody Volunteer volunteer) {
        return service.update(id, volunteer);
    }

    @DeleteMapping("/api/volunteers/{id}")
    public ResponseEntity<Void> deleteVolunteer(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── VolunteerArea ─────────────────────────────────────────────────────────

    @GetMapping("/api/volunteer-areas")
    public Page<VolunteerArea> listAreas(Pageable pageable) {
        return service.findAreas(pageable);
    }

    @GetMapping("/api/volunteer-areas/{id}")
    public VolunteerArea getArea(@PathVariable UUID id) {
        return service.findAreaById(id);
    }

    @PostMapping("/api/volunteer-areas")
    public ResponseEntity<VolunteerArea> createArea(@Valid @RequestBody VolunteerArea area) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createArea(area));
    }

    @PutMapping("/api/volunteer-areas/{id}")
    public VolunteerArea updateArea(@PathVariable UUID id, @Valid @RequestBody VolunteerArea area) {
        return service.updateArea(id, area);
    }

    @DeleteMapping("/api/volunteer-areas/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id) {
        service.deleteArea(id);
        return ResponseEntity.noContent().build();
    }

    // ── VolunteerHour ─────────────────────────────────────────────────────────

    @GetMapping("/api/volunteers/{volunteerId}/hours")
    public Page<VolunteerHour> listHours(@PathVariable UUID volunteerId, Pageable pageable) {
        return service.findHoursByVolunteer(volunteerId, pageable);
    }

    @GetMapping("/api/volunteers/{volunteerId}/hours/total-approved")
    public BigDecimal totalApprovedHours(@PathVariable UUID volunteerId) {
        return service.totalApprovedHours(volunteerId);
    }

    @GetMapping("/api/volunteer-hours/{id}")
    public VolunteerHour getHour(@PathVariable UUID id) {
        return service.findHourById(id);
    }

    @PostMapping("/api/volunteer-hours")
    public ResponseEntity<VolunteerHour> logHours(@Valid @RequestBody VolunteerHour hour) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.logHours(hour));
    }

    @PutMapping("/api/volunteer-hours/{id}")
    public VolunteerHour updateHour(@PathVariable UUID id, @Valid @RequestBody VolunteerHour hour) {
        return service.updateHour(id, hour);
    }

    @DeleteMapping("/api/volunteer-hours/{id}")
    public ResponseEntity<Void> deleteHour(@PathVariable UUID id) {
        service.deleteHour(id);
        return ResponseEntity.noContent().build();
    }
}
