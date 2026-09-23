package com.svivanrilski.svirerp.organization;

import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository repository;

    @InjectMocks
    private OrganizationService service;

    @Test
    void getOrganization_returnsTheSingletonProfile() {
        Organization organization = Organization.builder().name("SVIR").build();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(organization));

        assertThat(service.getOrganization()).isSameAs(organization);
    }

    @Test
    void getOrganization_whenProfileDoesNotExist_reportsNotFound() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(service::getOrganization)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No organization exists yet");
    }

    @Test
    void upsert_whenProfileDoesNotExist_createsIt() {
        Organization patch = Organization.builder().name("SVIR").build();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(repository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization saved = service.upsert(patch);

        assertThat(saved.getName()).isEqualTo("SVIR");
        verify(repository).save(saved);
    }

    @Test
    void upsert_updatesTheExistingProfileWithoutReplacingItsIdentity() {
        UUID existingId = UUID.randomUUID();
        Organization existing = Organization.builder().id(existingId).name("Old name").build();
        Organization patch = Organization.builder()
                .id(UUID.randomUUID())
                .name("New name")
                .legalName("New Legal Name")
                .email("office@example.org")
                .build();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        when(repository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization saved = service.upsert(patch);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(existingId);
        assertThat(saved.getName()).isEqualTo("New name");
        assertThat(saved.getLegalName()).isEqualTo("New Legal Name");
        assertThat(saved.getEmail()).isEqualTo("office@example.org");
        verify(repository).save(existing);
    }
}
