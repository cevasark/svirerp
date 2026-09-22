package com.svivanrilski.svirerp.person;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PersonController {

    private final PersonService service;
    private final PersonImportService importService;

    @GetMapping("/api/persons")
    public Page<Person> list(Pageable pageable) {
        return service.findAll(pageable);
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

    // ── Import (Zeffy contacts export — see PersonImportService) ──────────────

    @GetMapping("/api/organizations/{orgId}/persons/import-template")
    public ResponseEntity<byte[]> importTemplate(@PathVariable UUID orgId) {
        byte[] csv = importService.buildImportTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("people-import-template.csv")
                        .build()
                        .toString())
                .body(csv);
    }

    @PostMapping("/api/organizations/{orgId}/persons/import")
    public PersonImportService.PersonImportResult importPeople(
            @PathVariable UUID orgId, @RequestParam("file") MultipartFile file) {
        return importService.importPeople(orgId, file);
    }
}
