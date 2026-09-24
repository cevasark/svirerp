package com.svivanrilski.svirerp.person;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PersonController {

    private final PersonService service;
    private final PersonOverviewService overviewService;

    @GetMapping("/api/persons")
    public Page<Person> list(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/api/persons/overview")
    public Page<PersonOverviewService.PersonOverview> overview(Pageable pageable) {
        return overviewService.findAll(pageable);
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
