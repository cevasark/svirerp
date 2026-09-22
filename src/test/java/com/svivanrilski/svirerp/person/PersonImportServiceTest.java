package com.svivanrilski.svirerp.person;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Covers PersonImportService's file parsing and per-row error isolation — the actual create/skip
 *  decision is PersonImportRowApplier's job (see PersonImportRowApplierTest), so it's mocked here.
 *  All collaborators mocked; doesn't stand up a Spring context or a real DB — only the real
 *  CSV-parsing code runs. */
@ExtendWith(MockitoExtension.class)
class PersonImportServiceTest {

    private static final String HEADER = String.join(",",
            "First name", "Last name", "Email", "Secondary email", "Language", "Address", "City",
            "Postal code", "Country", "Region", "Phone", "Unsubscribed");

    @Mock private PersonImportRowApplier rowApplier;

    @InjectMocks
    private PersonImportService service;

    private UUID orgId;

    private MockMultipartFile csvOf(String... dataRows) {
        String content = HEADER + "\n" + String.join("\n", dataRows) + "\n";
        return new MockMultipartFile("file", "contacts.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private String row(String firstName, String lastName, String email, String unsubscribed) {
        return String.join(",", firstName, lastName, email, "", "English", "123 Main St", "Springfield",
                "62704", "USA", "IL", "555-0100", unsubscribed);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
    }

    @Test
    void createdRow_incrementsCreatedCount() {
        when(rowApplier.applyRow(any(), any())).thenReturn(PersonImportRowApplier.ApplyOutcome.CREATED);

        PersonImportService.PersonImportResult result =
                service.importPeople(orgId, csvOf(row("Jane", "Doe", "jane@example.com", "No")));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skippedExisting()).isZero();
        assertThat(result.failed()).isEmpty();
    }

    @Test
    void existingRow_incrementsSkippedExistingCount() {
        when(rowApplier.applyRow(any(), any())).thenReturn(PersonImportRowApplier.ApplyOutcome.SKIPPED_EXISTING);

        PersonImportService.PersonImportResult result =
                service.importPeople(orgId, csvOf(row("Jane", "Doe", "jane@example.com", "No")));

        assertThat(result.created()).isZero();
        assertThat(result.skippedExisting()).isEqualTo(1);
    }

    @Test
    void missingRequiredField_goesToFailedList_withoutAbortingTheBatch() {
        when(rowApplier.applyRow(any(), any())).thenReturn(PersonImportRowApplier.ApplyOutcome.CREATED);

        PersonImportService.PersonImportResult result = service.importPeople(orgId, csvOf(
                row("", "Doe", "blank-first-name@example.com", "No"),
                row("John", "Smith", "john@example.com", "No")));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).rowNumber()).isEqualTo(2);
        assertThat(result.failed().get(0).email()).isEqualTo("blank-first-name@example.com");
        assertThat(result.failed().get(0).message()).contains("First name");
    }

    @Test
    void unsubscribedColumn_isParsedCaseInsensitively() {
        ArgumentCaptor<PersonImportRow> rowCaptor = ArgumentCaptor.forClass(PersonImportRow.class);
        when(rowApplier.applyRow(any(), rowCaptor.capture())).thenReturn(PersonImportRowApplier.ApplyOutcome.CREATED);

        service.importPeople(orgId, csvOf(row("Jane", "Doe", "jane@example.com", "YES")));

        assertThat(rowCaptor.getValue().unsubscribed()).isTrue();
    }

    @Test
    void wrongFileFormat_throwsBeforeProcessingAnyRow() {
        MockMultipartFile badFile = new MockMultipartFile("file", "wrong.csv", "text/csv",
                "Not,The,Right,Columns\na,b,c,d\n".getBytes(StandardCharsets.UTF_8));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.importPeople(orgId, badFile));
    }
}
