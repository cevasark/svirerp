package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZeffyPaymentSyncServiceTest {

    private ZeffyApiClient api;
    private ZeffyPaymentProcessor processor;
    private ZeffySyncRunService runs;
    private ZeffySyncPaymentResultService results;
    private ZeffyPaymentSyncService service;

    @BeforeEach
    void setUp() {
        api = mock(ZeffyApiClient.class);
        processor = mock(ZeffyPaymentProcessor.class);
        runs = mock(ZeffySyncRunService.class);
        results = mock(ZeffySyncPaymentResultService.class);
        service = new ZeffyPaymentSyncService(api, processor, runs, results);
    }

    @Test
    void chicagoDateRangeAndEveryPageArePersistedDuringPreview() throws Exception {
        UUID runId = UUID.randomUUID();
        ZeffySyncRun started = ZeffySyncRun.builder().id(runId).build();
        ZeffySyncRun finished = ZeffySyncRun.builder().id(runId).status("COMPLETED").build();
        JsonNode first = payment("pay-1");
        JsonNode second = payment("pay-2");
        when(runs.startPayment(eq("PREVIEW"), eq("admin"), any(), any(), isNull())).thenReturn(started);
        when(api.fetchPaymentPage(anyLong(), anyLong(), isNull()))
                .thenReturn(new ZeffyApiClient.PaymentPageFetch(List.of(first), true, "cursor-1"));
        when(api.fetchPaymentPage(anyLong(), anyLong(), eq("cursor-1")))
                .thenReturn(new ZeffyApiClient.PaymentPageFetch(List.of(second), false, null));
        when(processor.previewApiPayment(any(), any())).thenReturn(
                new ZeffyPaymentProcessor.ProcessingResult("pay-1", UUID.randomUUID(),
                        "ELIGIBLE", null, "hash-1", true),
                new ZeffyPaymentProcessor.ProcessingResult("pay-2", UUID.randomUUID(),
                        "NEEDS_MAPPING", "missing mapping", "hash-2", true));
        when(runs.finishPayment(eq(runId), any(), eq("cursor-1"))).thenReturn(finished);

        assertThat(service.preview(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 8), "admin"))
                .isSameAs(finished);

        verify(api).fetchPaymentPage(
                OffsetDateTime.parse("2026-03-08T00:00:00-06:00").toEpochSecond(),
                OffsetDateTime.parse("2026-03-08T23:59:59-05:00").toEpochSecond(), null);
        verify(api).fetchPaymentPage(anyLong(), anyLong(), eq("cursor-1"));
        ArgumentCaptor<ZeffySyncRunService.PaymentCounts> counts =
                ArgumentCaptor.forClass(ZeffySyncRunService.PaymentCounts.class);
        verify(runs).finishPayment(eq(runId), counts.capture(), eq("cursor-1"));
        assertThat(counts.getValue().fetched()).isEqualTo(2);
        assertThat(counts.getValue().eligible()).isEqualTo(1);
        assertThat(counts.getValue().needsMapping()).isEqualTo(1);
    }

    @Test
    void applyHoldsPaymentWhosePayloadChangedAfterPreview() throws Exception {
        UUID previewId = UUID.randomUUID();
        UUID applyId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00-06:00");
        ZeffySyncRun preview = ZeffySyncRun.builder().id(previewId).syncType("PAYMENTS")
                .executionMode("PREVIEW").status("COMPLETED").requestedFrom(from).build();
        ZeffySyncPaymentResult previewResult = ZeffySyncPaymentResult.builder()
                .zeffyPaymentId("pay-1").payloadSha256("old-hash").build();
        JsonNode current = payment("pay-1");
        when(runs.getDetailed(previewId)).thenReturn(preview);
        when(results.findAll(previewId)).thenReturn(List.of(previewResult));
        when(runs.startPayment("APPLY", "admin", from, null, previewId))
                .thenReturn(ZeffySyncRun.builder().id(applyId).build());
        when(api.fetchPaymentPage(from.toEpochSecond(), null, null))
                .thenReturn(new ZeffyApiClient.PaymentPageFetch(List.of(current), false, null));
        when(processor.fingerprint(current)).thenReturn("new-hash");
        when(runs.finishPayment(eq(applyId), any(), isNull()))
                .thenReturn(ZeffySyncRun.builder().id(applyId).status("COMPLETED").build());

        service.apply(previewId, "admin");

        verify(processor, never()).applyApiPayment(any(), any());
        verify(results).record(eq(applyId), eq("pay-1"), isNull(),
                eq("CHANGED_AFTER_PREVIEW"), eq("new-hash"), contains("changed"), any());
    }

    private JsonNode payment(String id) throws Exception {
        return new ObjectMapper().readTree("{\"id\":\"" + id + "\"}");
    }
}
