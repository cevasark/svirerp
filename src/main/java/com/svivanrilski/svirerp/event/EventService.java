package com.svivanrilski.svirerp.event;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventService {

    private static final Set<String> EVENT_STATUSES = Set.of("scheduled", "cancelled", "completed", "postponed");
    private static final Set<String> VISIBILITIES = Set.of("public", "members_only", "internal");
    private static final Set<String> REGISTRATION_STATUSES =
            Set.of("registered", "attended", "cancelled", "waitlisted", "no_show");

    private final CalendarEventRepository eventRepo;
    private final ChurchEventRepository churchRepo;
    private final EventRegistrationRepository registrationRepo;
    private final EventResourceRepository resourceRepo;
    private final PersonService personService;
    private final GoogleCalendarService googleCalendarService;

    // ── CalendarEvent ─────────────────────────────────────────────────────────

    public Page<CalendarEvent> findEvents(Pageable pageable) {
        return eventRepo.findAll(pageable);
    }

    public Page<CalendarEvent> findEventsByDateRange(OffsetDateTime from,
            OffsetDateTime to, Pageable pageable) {
        return eventRepo.findByStartDatetimeBetween(from, to, pageable);
    }

    public CalendarEvent findEventById(UUID id) {
        return eventRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CalendarEvent", id));
    }

    @Transactional
    public CalendarEvent createEvent(CalendarEvent event) {
        validateEventStatus(event.getStatus());
        validateVisibility(event.getVisibility());
        if (event.getCreatedBy() != null) {
            Person creator = personService.findById(event.getCreatedBy().getId());
            event.setCreatedBy(creator);
        }
        CalendarEvent saved = eventRepo.save(event);
        googleCalendarService.syncToGoogle(saved);
        return eventRepo.save(saved);
    }

    @Transactional
    public CalendarEvent updateEvent(UUID id, CalendarEvent patch) {
        validateEventStatus(patch.getStatus());
        validateVisibility(patch.getVisibility());
        CalendarEvent existing = findEventById(id);
        existing.setTitle(patch.getTitle());
        existing.setDescription(patch.getDescription());
        existing.setEventType(patch.getEventType());
        existing.setStartDatetime(patch.getStartDatetime());
        existing.setEndDatetime(patch.getEndDatetime());
        existing.setIsAllDay(patch.getIsAllDay());
        existing.setLocation(patch.getLocation());
        existing.setVirtualLink(patch.getVirtualLink());
        existing.setIsRecurring(patch.getIsRecurring());
        existing.setRecurrenceRule(patch.getRecurrenceRule());
        existing.setStatus(patch.getStatus());
        existing.setVisibility(patch.getVisibility());
        existing.setCapacity(patch.getCapacity());
        existing.setPublishToOfficial(patch.getPublishToOfficial());
        existing.setPublishToInternal(patch.getPublishToInternal());
        CalendarEvent saved = eventRepo.save(existing);
        googleCalendarService.syncToGoogle(saved);
        return eventRepo.save(saved);
    }

    @Transactional
    public void deleteEvent(UUID id) {
        CalendarEvent existing = findEventById(id);
        googleCalendarService.deleteFromGoogle(existing);
        eventRepo.deleteById(id);
    }

    // ── ChurchEvent ───────────────────────────────────────────────────────────

    public ChurchEvent findChurchEventByCalendarId(UUID calendarEventId) {
        return churchRepo.findByCalendarEventId(calendarEventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ChurchEvent not found for CalendarEvent: " + calendarEventId));
    }

    public ChurchEvent findChurchEventById(UUID id) {
        return churchRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChurchEvent", id));
    }

    @Transactional
    public ChurchEvent createChurchEvent(ChurchEvent churchEvent) {
        CalendarEvent calEvent = findEventById(churchEvent.getCalendarEvent().getId());
        if (churchRepo.existsByCalendarEventId(calEvent.getId())) {
            throw new IllegalArgumentException("A ChurchEvent already exists for CalendarEvent: " + calEvent.getId());
        }
        churchEvent.setCalendarEvent(calEvent);
        return churchRepo.save(churchEvent);
    }

    @Transactional
    public ChurchEvent updateChurchEvent(UUID id, ChurchEvent patch) {
        ChurchEvent existing = findChurchEventById(id);
        existing.setServiceType(patch.getServiceType());
        existing.setLiturgicalSeason(patch.getLiturgicalSeason());
        existing.setOfficiant(patch.getOfficiant());
        existing.setSermonTitle(patch.getSermonTitle());
        existing.setScriptureReadings(patch.getScriptureReadings());
        existing.setMusicSelections(patch.getMusicSelections());
        existing.setSpecialInstructions(patch.getSpecialInstructions());
        existing.setOfferingCollected(patch.getOfferingCollected());
        existing.setAttendanceCount(patch.getAttendanceCount());
        return churchRepo.save(existing);
    }

    @Transactional
    public void deleteChurchEvent(UUID id) {
        if (!churchRepo.existsById(id)) throw new ResourceNotFoundException("ChurchEvent", id);
        churchRepo.deleteById(id);
    }

    // ── EventRegistration ─────────────────────────────────────────────────────

    public Page<EventRegistration> findRegistrationsByEvent(UUID eventId, Pageable pageable) {
        return registrationRepo.findByEventId(eventId, pageable);
    }

    public EventRegistration findRegistrationById(UUID id) {
        return registrationRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventRegistration", id));
    }

    @Transactional
    public EventRegistration createRegistration(EventRegistration registration) {
        validateRegistrationStatus(registration.getStatus());
        CalendarEvent event = findEventById(registration.getEvent().getId());
        Person person = personService.findById(registration.getPerson().getId());
        if (registrationRepo.existsByEventIdAndPersonId(event.getId(), person.getId())) {
            throw new IllegalArgumentException("Person " + person.getId() + " is already registered for this event");
        }
        registration.setEvent(event);
        registration.setPerson(person);
        return registrationRepo.save(registration);
    }

    @Transactional
    public EventRegistration updateRegistration(UUID id, EventRegistration patch) {
        validateRegistrationStatus(patch.getStatus());
        EventRegistration existing = findRegistrationById(id);
        existing.setStatus(patch.getStatus());
        existing.setFeePaid(patch.getFeePaid());
        existing.setTicketNumber(patch.getTicketNumber());
        existing.setNotes(patch.getNotes());
        return registrationRepo.save(existing);
    }

    @Transactional
    public void deleteRegistration(UUID id) {
        if (!registrationRepo.existsById(id)) throw new ResourceNotFoundException("EventRegistration", id);
        registrationRepo.deleteById(id);
    }

    // ── EventResource ─────────────────────────────────────────────────────────

    public Page<EventResource> findResourcesByEvent(UUID eventId, Pageable pageable) {
        return resourceRepo.findByEventId(eventId, pageable);
    }

    public EventResource findResourceById(UUID id) {
        return resourceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventResource", id));
    }

    @Transactional
    public EventResource createResource(EventResource resource) {
        CalendarEvent event = findEventById(resource.getEvent().getId());
        resource.setEvent(event);
        return resourceRepo.save(resource);
    }

    @Transactional
    public EventResource updateResource(UUID id, EventResource patch) {
        EventResource existing = findResourceById(id);
        existing.setResourceType(patch.getResourceType());
        existing.setResourceName(patch.getResourceName());
        existing.setAssignedTo(patch.getAssignedTo());
        existing.setNotes(patch.getNotes());
        return resourceRepo.save(existing);
    }

    @Transactional
    public void deleteResource(UUID id) {
        if (!resourceRepo.existsById(id)) throw new ResourceNotFoundException("EventResource", id);
        resourceRepo.deleteById(id);
    }

    // ── Validators ────────────────────────────────────────────────────────────

    private void validateEventStatus(String status) {
        if (status != null && !EVENT_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid event status: " + status + ". Allowed: " + EVENT_STATUSES);
    }

    private void validateVisibility(String visibility) {
        if (visibility != null && !VISIBILITIES.contains(visibility))
            throw new IllegalArgumentException("Invalid visibility: " + visibility + ". Allowed: " + VISIBILITIES);
    }

    private void validateRegistrationStatus(String status) {
        if (status != null && !REGISTRATION_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid registration status: " + status + ". Allowed: " + REGISTRATION_STATUSES);
    }
}
