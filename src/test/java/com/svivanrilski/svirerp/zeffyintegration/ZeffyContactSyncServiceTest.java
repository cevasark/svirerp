package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ZeffyContactSyncServiceTest {

    @Test
    void paginatesAndRecordsIndependentContactOutcomes() throws Exception {
        ZeffyApiClient api = mock(ZeffyApiClient.class);
        ZeffyContactProcessor processor = mock(ZeffyContactProcessor.class);
        ZeffySyncRunService runs = mock(ZeffySyncRunService.class);
        ZeffyContactSyncService service = new ZeffyContactSyncService(api, processor, runs);
        UUID runId = UUID.randomUUID();
        ZeffySyncRun running = ZeffySyncRun.builder().id(runId).build();
        ZeffySyncRun finished = ZeffySyncRun.builder().id(runId).status("PARTIAL").build();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode first = mapper.readTree("{\"id\":\"contact-1\"}");
        JsonNode second = mapper.readTree("{\"id\":\"contact-2\"}");
        JsonNode third = mapper.readTree("{\"id\":\"contact-3\"}");

        when(runs.start("CONTACTS", "MANUAL", "admin@example.com")).thenReturn(running);
        when(api.fetchContactPage(null)).thenReturn(
                new ZeffyApiClient.ContactPageFetch(List.of(first, second), true, "cursor-2"));
        when(api.fetchContactPage("cursor-2")).thenReturn(
                new ZeffyApiClient.ContactPageFetch(List.of(third), false, null));
        when(processor.applyFromSync(first)).thenReturn(
                new ZeffyContactProcessor.ApplyResult(true, false, false, false, UUID.randomUUID()));
        when(processor.applyFromSync(second)).thenReturn(
                new ZeffyContactProcessor.ApplyResult(true, false, false, true, UUID.randomUUID()));
        when(processor.applyFromSync(third)).thenThrow(new IllegalArgumentException("bad contact"));
        when(runs.finishContacts(eq(runId), any(), eq("cursor-2"))).thenReturn(finished);

        assertThat(service.synchronize("admin@example.com")).isSameAs(finished);

        ArgumentCaptor<ZeffySyncRunService.ContactCounts> counts =
                ArgumentCaptor.forClass(ZeffySyncRunService.ContactCounts.class);
        verify(runs).finishContacts(eq(runId), counts.capture(), eq("cursor-2"));
        assertThat(counts.getValue()).isEqualTo(
                new ZeffySyncRunService.ContactCounts(3, 2, 0, 0, 1, 1, 1));
        verify(runs, times(2)).checkpointContacts(eq(runId), any(), any());
    }
}
