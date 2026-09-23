package com.svivanrilski.svirerp.event;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService service;

    // ── CalendarEvent ─────────────────────────────────────────────────────────

    @GetMapping("/api/events")
    public Page<CalendarEvent> listEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        if (from != null && to != null) {
            return service.findEventsByDateRange(from, to, pageable);
        }
        return service.findEvents(pageable);
    }

    @GetMapping("/api/events/{id}")
    public CalendarEvent getEvent(@PathVariable UUID id) {
        return service.findEventById(id);
    }

    @PostMapping("/api/events")
    public ResponseEntity<CalendarEvent> createEvent(@Valid @RequestBody CalendarEvent event) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEvent(event));
    }

    @PutMapping("/api/events/{id}")
    public CalendarEvent updateEvent(@PathVariable UUID id, @Valid @RequestBody CalendarEvent event) {
        return service.updateEvent(id, event);
    }

    @DeleteMapping("/api/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        service.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    // ── ChurchEvent ───────────────────────────────────────────────────────────

    @GetMapping("/api/events/{calendarEventId}/church-details")
    public ChurchEvent getChurchDetails(@PathVariable UUID calendarEventId) {
        return service.findChurchEventByCalendarId(calendarEventId);
    }

    @GetMapping("/api/church-events/{id}")
    public ChurchEvent getChurchEvent(@PathVariable UUID id) {
        return service.findChurchEventById(id);
    }

    @PostMapping("/api/church-events")
    public ResponseEntity<ChurchEvent> createChurchEvent(@Valid @RequestBody ChurchEvent churchEvent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createChurchEvent(churchEvent));
    }

    @PutMapping("/api/church-events/{id}")
    public ChurchEvent updateChurchEvent(@PathVariable UUID id, @Valid @RequestBody ChurchEvent churchEvent) {
        return service.updateChurchEvent(id, churchEvent);
    }

    @DeleteMapping("/api/church-events/{id}")
    public ResponseEntity<Void> deleteChurchEvent(@PathVariable UUID id) {
        service.deleteChurchEvent(id);
        return ResponseEntity.noContent().build();
    }

    // ── EventRegistration ─────────────────────────────────────────────────────

    @GetMapping("/api/events/{eventId}/registrations")
    public Page<EventRegistration> listRegistrations(@PathVariable UUID eventId, Pageable pageable) {
        return service.findRegistrationsByEvent(eventId, pageable);
    }

    @GetMapping("/api/event-registrations/{id}")
    public EventRegistration getRegistration(@PathVariable UUID id) {
        return service.findRegistrationById(id);
    }

    @PostMapping("/api/event-registrations")
    public ResponseEntity<EventRegistration> createRegistration(@Valid @RequestBody EventRegistration registration) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRegistration(registration));
    }

    @PutMapping("/api/event-registrations/{id}")
    public EventRegistration updateRegistration(@PathVariable UUID id, @Valid @RequestBody EventRegistration registration) {
        return service.updateRegistration(id, registration);
    }

    @DeleteMapping("/api/event-registrations/{id}")
    public ResponseEntity<Void> deleteRegistration(@PathVariable UUID id) {
        service.deleteRegistration(id);
        return ResponseEntity.noContent().build();
    }

    // ── EventResource ─────────────────────────────────────────────────────────

    @GetMapping("/api/events/{eventId}/resources")
    public Page<EventResource> listResources(@PathVariable UUID eventId, Pageable pageable) {
        return service.findResourcesByEvent(eventId, pageable);
    }

    @GetMapping("/api/event-resources/{id}")
    public EventResource getResource(@PathVariable UUID id) {
        return service.findResourceById(id);
    }

    @PostMapping("/api/event-resources")
    public ResponseEntity<EventResource> createResource(@Valid @RequestBody EventResource resource) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createResource(resource));
    }

    @PutMapping("/api/event-resources/{id}")
    public EventResource updateResource(@PathVariable UUID id, @Valid @RequestBody EventResource resource) {
        return service.updateResource(id, resource);
    }

    @DeleteMapping("/api/event-resources/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        service.deleteResource(id);
        return ResponseEntity.noContent().build();
    }
}
