package com.svivanrilski.svirerp.person;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonControllerTest {

    @Mock private PersonService service;
    @Mock private PersonOverviewService overviewService;

    @InjectMocks
    private PersonController controller;

    @Test
    @DisplayName("overview delegates filters with parsed UUID membershipTypeId")
    void overview_withFilters_delegatesToOverviewService() {
        UUID typeId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<PersonOverviewService.PersonOverview> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(overviewService.findAll("John", "john%", typeId, null, false, pageable))
                .thenReturn(emptyPage);

        Page<PersonOverviewService.PersonOverview> result = controller.overview(
                "John", "john%", typeId.toString(), null, null, pageable);

        assertThat(result).isSameAs(emptyPage);
        verify(overviewService).findAll("John", "john%", typeId, null, false, pageable);
    }

    @Test
    @DisplayName("overview parses membershipType string as UUID when valid")
    void overview_withStringUuidMembershipType_parsesAsUuid() {
        UUID typeId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<PersonOverviewService.PersonOverview> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(overviewService.findAll(null, null, typeId, null, false, pageable))
                .thenReturn(emptyPage);

        Page<PersonOverviewService.PersonOverview> result = controller.overview(
                null, null, null, typeId.toString(), null, pageable);

        assertThat(result).isSameAs(emptyPage);
        verify(overviewService).findAll(null, null, typeId, null, false, pageable);
    }

    @Test
    @DisplayName("overview passes non-UUID membershipType string as name")
    void overview_withMembershipTypeName_passesAsName() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<PersonOverviewService.PersonOverview> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(overviewService.findAll(null, null, null, "Benefactor", false, pageable))
                .thenReturn(emptyPage);

        Page<PersonOverviewService.PersonOverview> result = controller.overview(
                null, null, null, "Benefactor", null, pageable);

        assertThat(result).isSameAs(emptyPage);
        verify(overviewService).findAll(null, null, null, "Benefactor", false, pageable);
    }

    @Test
    @DisplayName("overview delegates with noMembership=true when membershipTypeId is 'none'")
    void overview_withNoneMembershipType_delegatesWithNoMembershipTrue() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<PersonOverviewService.PersonOverview> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(overviewService.findAll(null, null, null, null, true, pageable))
                .thenReturn(emptyPage);

        Page<PersonOverviewService.PersonOverview> result = controller.overview(
                null, null, "none", null, null, pageable);

        assertThat(result).isSameAs(emptyPage);
        verify(overviewService).findAll(null, null, null, null, true, pageable);
    }

    @Test
    @DisplayName("list delegates filters to PersonService")
    void list_withFilters_delegatesToPersonService() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Person> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(service.findAll("Smith", null, null, null, false, pageable))
                .thenReturn(emptyPage);

        Page<Person> result = controller.list("Smith", null, null, null, null, pageable);

        assertThat(result).isSameAs(emptyPage);
        verify(service).findAll("Smith", null, null, null, false, pageable);
    }

    @Test
    @DisplayName("list delegates with noMembership=true when membershipType is 'none'")
    void list_withNoneMembershipType_delegatesWithNoMembershipTrue() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Person> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(service.findAll(null, null, null, null, true, pageable))
                .thenReturn(emptyPage);

        Page<Person> result = controller.list(null, null, null, "none", null, pageable);

        assertThat(result).isSameAs(emptyPage);
        verify(service).findAll(null, null, null, null, true, pageable);
    }
}
