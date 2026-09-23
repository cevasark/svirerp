package com.svivanrilski.svirerp.person;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.common.SearchSpecifications;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PersonService {

    /** Allow-list for the autocomplete search endpoint — never trust a client-supplied field name
     *  without checking it against this first (see SearchSpecifications). */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of("firstName", "lastName");

    private final PersonRepository repo;

    public Page<Person> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    /** Autocomplete search — case-insensitive "contains" match against one allow-listed field,
     *  capped to 20 results (a typeahead dropdown, not a paginated list). */
    public List<Person> search(String field, String query) {
        if (!SEARCHABLE_FIELDS.contains(field)) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' is not searchable on Person. Allowed: " + SEARCHABLE_FIELDS);
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return repo.findAll(SearchSpecifications.contains(field, query.trim()), PageRequest.of(0, 20)).getContent();
    }

    public Person findById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person", id));
    }

    public Person findByEmail(String email) {
        return repo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found with email: " + email));
    }

    /** Non-throwing lookup, for find-or-create flows (e.g. bulk member import). */
    public Optional<Person> findByEmailIfExists(String email) {
        return repo.findByEmail(email);
    }

    /** Returns every trim/case-insensitive email match so integration flows can refuse an
     * ambiguous identity rather than silently choosing one person. */
    public List<Person> findByNormalizedEmail(String email) {
        if (email == null || email.isBlank()) return List.of();
        return repo.findAllByNormalizedEmail(email.trim());
    }

    @Transactional
    public Person create(Person person) {
        if (person.getEmail() != null && repo.existsByEmail(person.getEmail())) {
            throw new IllegalArgumentException("A person with email '" + person.getEmail() + "' already exists");
        }
        return repo.save(person);
    }

    /**
     * Fills in only currently-null fields on an existing Person from {@code source} — never
     * overwrites data already on file. Used by find-or-create import flows (e.g. Zeffy) where the
     * imported row shouldn't clobber contact info a person may have updated through other means.
     */
    @Transactional
    public Person fillBlankFields(UUID id, Person source) {
        Person existing = findById(id);
        if (isBlank(existing.getPhone())) existing.setPhone(source.getPhone());
        if (isBlank(existing.getAddressLine1())) existing.setAddressLine1(source.getAddressLine1());
        if (isBlank(existing.getCity())) existing.setCity(source.getCity());
        if (isBlank(existing.getState())) existing.setState(source.getState());
        if (isBlank(existing.getZip())) existing.setZip(source.getZip());
        if (existing.getDateOfBirth() == null) existing.setDateOfBirth(source.getDateOfBirth());
        return repo.save(existing);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional
    public Person update(UUID id, Person patch) {
        Person existing = findById(id);
        existing.setFirstName(patch.getFirstName());
        existing.setLastName(patch.getLastName());
        existing.setEmail(patch.getEmail());
        existing.setPhone(patch.getPhone());
        existing.setAddressLine1(patch.getAddressLine1());
        existing.setCity(patch.getCity());
        existing.setState(patch.getState());
        existing.setZip(patch.getZip());
        existing.setDateOfBirth(patch.getDateOfBirth());
        return repo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Person", id);
        repo.deleteById(id);
    }
}
