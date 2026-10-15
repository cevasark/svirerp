package com.svivanrilski.svirerp.person;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class PersonController {

    private final PersonService service;
    private final PersonOverviewService overviewService;

    public PersonController(PersonService service, PersonOverviewService overviewService) {
        this.service = service;
        this.overviewService = overviewService;
    }

    @GetMapping("/api/persons")
    public Page<Person> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String membershipTypeId,
            @RequestParam(required = false) String membershipType,
            @RequestParam(required = false) Boolean noMembership,
            Pageable pageable) {
        TypeFilter filter = resolveTypeFilter(membershipTypeId, membershipType, noMembership);
        return service.findAll(name, email, filter.id(), filter.name(), filter.noMembership(), pageable);
    }

    @GetMapping("/api/persons/overview")
    public Page<PersonOverviewService.PersonOverview> overview(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String membershipTypeId,
            @RequestParam(required = false) String membershipType,
            @RequestParam(required = false) Boolean noMembership,
            Pageable pageable) {
        TypeFilter filter = resolveTypeFilter(membershipTypeId, membershipType, noMembership);
        return overviewService.findAll(name, email, filter.id(), filter.name(), filter.noMembership(), pageable);
    }

    private record TypeFilter(UUID id, String name, boolean noMembership) {
    }

    private TypeFilter resolveTypeFilter(String membershipTypeId, String membershipType, Boolean noMembership) {
        if (Boolean.TRUE.equals(noMembership)
                || "none".equalsIgnoreCase(membershipTypeId)
                || "none".equalsIgnoreCase(membershipType)) {
            return new TypeFilter(null, null, true);
        }
        String candidate = (membershipTypeId != null && !membershipTypeId.isBlank())
                ? membershipTypeId.trim()
                : (membershipType != null && !membershipType.isBlank() ? membershipType.trim() : null);
        if (candidate == null) {
            return new TypeFilter(null, null, false);
        }
        try {
            return new TypeFilter(UUID.fromString(candidate), null, false);
        } catch (IllegalArgumentException e) {
            return new TypeFilter(null, candidate, false);
        }
    }

    /** Autocomplete search — e.g. GET /api/persons/search?field=firstName&q=Jo. */
    @GetMapping("/api/persons/search")
    public List<Person> search(@RequestParam String field, @RequestParam String q) {
        return service.search(field, q);
    }

    @GetMapping("/api/persons/{id}")
    public Person get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/api/persons")
    public ResponseEntity<Person> create(@Valid @RequestBody Person person) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(person));
    }

    @PutMapping("/api/persons/{id}")
    public Person update(@PathVariable UUID id, @Valid @RequestBody Person person) {
        return service.update(id, person);
    }

    @DeleteMapping("/api/persons/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
