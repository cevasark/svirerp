package com.svivanrilski.svirerp.person;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.membership.MembershipService;

import java.util.UUID;

/**
 * Applies one People-import row inside its own transaction — a separate bean call per row (see
 * {@link PersonImportService}) so one bad row can't mark a shared transaction rollback-only and
 * silently discard earlier successful rows, mirroring the same per-row-isolation pattern used by
 * the Member CSV import and the Zeffy importer.
 */
@Service
@RequiredArgsConstructor
public class PersonImportRowApplier {

    public enum ApplyOutcome {
        CREATED, SKIPPED_EXISTING
    }

    private final PersonService personService;
    private final MembershipService membershipService;

    /** SKIPPED_EXISTING for any email already on file — the People import only ever enrolls
     *  brand-new people, never touches or backfills a Follower membership for someone who already
     *  existed in the system before this import (even if they currently have no Member record at
     *  all). */
    @Transactional
    public ApplyOutcome applyRow(UUID orgId, PersonImportRow row) {
        if (personService.findByEmailIfExists(row.email()).isPresent()) {
            return ApplyOutcome.SKIPPED_EXISTING;
        }
        Person person = personService.create(Person.builder()
                .firstName(row.firstName())
                .lastName(row.lastName())
                .email(row.email())
                .phone(row.phone())
                .addressLine1(row.addressLine1())
                .city(row.city())
                .zip(row.zip())
                .build());
        membershipService.createFollowerMember(person.getId(), orgId, !row.unsubscribed());
        return ApplyOutcome.CREATED;
    }
}
