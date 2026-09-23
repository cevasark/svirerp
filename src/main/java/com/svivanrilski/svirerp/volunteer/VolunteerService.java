package com.svivanrilski.svirerp.volunteer;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.event.CalendarEvent;
import com.svivanrilski.svirerp.event.CalendarEventRepository;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VolunteerService {

    private static final Set<String> HOUR_STATUSES = Set.of("pending", "approved", "rejected");

    private static final List<String> STARTER_AREA_NAMES = List.of(
            "Construction", "Apartment Repair", "Cleaning Crew", "Cooking Crew",
            "Activities", "Outdoor Activities", "General");

    private final VolunteerRepository volunteerRepo;
    private final VolunteerHourRepository hourRepo;
    private final VolunteerAreaRepository areaRepo;
    private final PersonService personService;
    private final CalendarEventRepository calendarEventRepo;

    // ── Volunteer ─────────────────────────────────────────────────────────────

    public Page<Volunteer> findAll(UUID areaId, Pageable pageable) {
        if (areaId == null) {
            return volunteerRepo.findAll(pageable);
        }
        return volunteerRepo.findByAreasId(areaId, pageable);
    }

    public Volunteer findById(UUID id) {
        return volunteerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Volunteer", id));
    }

    @Transactional
    public Volunteer create(Volunteer volunteer) {
        Person person = personService.findById(volunteer.getPerson().getId());
        volunteer.setPerson(person);
        volunteer.setContactPerson(resolveContactPerson(volunteer.getContactPerson()));
        volunteer.setAreas(resolveAreas(volunteer.getAreas()));
        return volunteerRepo.save(volunteer);
    }

    @Transactional
    public Volunteer update(UUID id, Volunteer patch) {
        Volunteer existing = findById(id);
        existing.setOnboardDate(patch.getOnboardDate());
        existing.setIsActive(patch.getIsActive());
        existing.setSkills(patch.getSkills());
        existing.setAvailability(patch.getAvailability());
        existing.setNotes(patch.getNotes());
        existing.setContactPerson(resolveContactPerson(patch.getContactPerson()));
        existing.setAreas(resolveAreas(patch.getAreas()));
        return volunteerRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        if (!volunteerRepo.existsById(id)) throw new ResourceNotFoundException("Volunteer", id);
        volunteerRepo.deleteById(id);
    }

    private Person resolveContactPerson(Person contactPersonRef) {
        return contactPersonRef == null ? null : personService.findById(contactPersonRef.getId());
    }

    private Set<VolunteerArea> resolveAreas(Set<VolunteerArea> areaRefs) {
        if (areaRefs == null || areaRefs.isEmpty()) return new HashSet<>();
        List<UUID> ids = areaRefs.stream().map(VolunteerArea::getId).collect(Collectors.toList());
        return new HashSet<>(areaRepo.findAllById(ids));
    }

    // ── VolunteerArea ─────────────────────────────────────────────────────────

    /** Not read-only: may lazily seed starter areas on an org's first request, so it overrides the class-level readOnly default. */
    @Transactional
    public Page<VolunteerArea> findAreas(Pageable pageable) {
        Page<VolunteerArea> page = areaRepo.findAll(pageable);
        if (page.isEmpty() && pageable.getPageNumber() == 0) {
            seedStarterAreas();
            page = areaRepo.findAll(pageable);
        }
        return page;
    }

    private void seedStarterAreas() {
        for (String name : STARTER_AREA_NAMES) {
            if (!areaRepo.existsByNameIgnoreCase(name)) {
                areaRepo.save(VolunteerArea.builder().name(name).isActive(true).build());
            }
        }
    }

    public VolunteerArea findAreaById(UUID id) {
        return areaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VolunteerArea", id));
    }

    @Transactional
    public VolunteerArea createArea(VolunteerArea area) {
        validateUniqueAreaName(area.getName(), null);
        return areaRepo.save(area);
    }

    @Transactional
    public VolunteerArea updateArea(UUID id, VolunteerArea patch) {
        VolunteerArea existing = findAreaById(id);
        validateUniqueAreaName(patch.getName(), id);
        existing.setName(patch.getName());
        existing.setDescription(patch.getDescription());
        existing.setIsActive(patch.getIsActive());
        return areaRepo.save(existing);
    }

    @Transactional
    public void deleteArea(UUID id) {
        if (!areaRepo.existsById(id)) throw new ResourceNotFoundException("VolunteerArea", id);
        areaRepo.deleteById(id);
    }

    private void validateUniqueAreaName(String name, UUID excludeId) {
        if (name == null) return;
        areaRepo.findByNameIgnoreCase(name)
                .filter(a -> !a.getId().equals(excludeId))
                .ifPresent(a -> {
                    throw new IllegalArgumentException("A volunteer area named '" + name + "' already exists.");
                });
    }

    // ── VolunteerHour ─────────────────────────────────────────────────────────

    public Page<VolunteerHour> findHoursByVolunteer(UUID volunteerId, Pageable pageable) {
        return hourRepo.findByVolunteerId(volunteerId, pageable);
    }

    public VolunteerHour findHourById(UUID id) {
        return hourRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VolunteerHour", id));
    }

    /** Returns total approved hours for a volunteer — for recognition/grant reporting. */
    public BigDecimal totalApprovedHours(UUID volunteerId) {
        return hourRepo.sumApprovedHoursByVolunteer(volunteerId);
    }

    @Transactional
    public VolunteerHour logHours(VolunteerHour hour) {
        validateStatus(hour.getStatus());
        Volunteer volunteer = findById(hour.getVolunteer().getId());
        hour.setVolunteer(volunteer);
        if (hour.getEvent() != null) {
            CalendarEvent event = calendarEventRepo.findById(hour.getEvent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("CalendarEvent", hour.getEvent().getId()));
            hour.setEvent(event);
        }
        return hourRepo.save(hour);
    }

    @Transactional
    public VolunteerHour updateHour(UUID id, VolunteerHour patch) {
        validateStatus(patch.getStatus());
        VolunteerHour existing = findHourById(id);
        existing.setLogDate(patch.getLogDate());
        existing.setHours(patch.getHours());
        existing.setActivityDescription(patch.getActivityDescription());
        existing.setApprovedBy(patch.getApprovedBy());
        existing.setStatus(patch.getStatus());
        if (patch.getEvent() != null) {
            CalendarEvent event = calendarEventRepo.findById(patch.getEvent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("CalendarEvent", patch.getEvent().getId()));
            existing.setEvent(event);
        } else {
            existing.setEvent(null);
        }
        return hourRepo.save(existing);
    }

    @Transactional
    public void deleteHour(UUID id) {
        if (!hourRepo.existsById(id)) throw new ResourceNotFoundException("VolunteerHour", id);
        hourRepo.deleteById(id);
    }

    private void validateStatus(String status) {
        if (status != null && !HOUR_STATUSES.contains(status))
            throw new IllegalArgumentException("Invalid hour status: " + status + ". Allowed: " + HOUR_STATUSES);
    }
}
