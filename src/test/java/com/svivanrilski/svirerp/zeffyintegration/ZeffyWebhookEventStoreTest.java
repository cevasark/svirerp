package com.svivanrilski.svirerp.zeffyintegration;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZeffyWebhookEventStoreTest {

    @Test
    void duplicateWithSamePayloadOnlyUpdatesDeliveryAudit() {
        ZeffyWebhookEventRepository repository = mock(ZeffyWebhookEventRepository.class);
        ZeffyWebhookEventStore store = new ZeffyWebhookEventStore(repository);
        ZeffyWebhookEvent event = ZeffyWebhookEvent.builder()
                .zeffyEventId("event-1").payloadSha256("same").status("RECEIVED")
                .deliveryCount(1).build();
        when(repository.findByZeffyEventIdForUpdate("event-1")).thenReturn(Optional.of(event));
        when(repository.save(event)).thenReturn(event);

        store.recordDuplicate("event-1", "same", OffsetDateTime.now(), OffsetDateTime.now());

        assertThat(event.getDeliveryCount()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo("RECEIVED");
        assertThat(event.getErrorSummary()).isNull();
    }

    @Test
    void duplicateIdWithDifferentPayloadIsFlaggedForReview() {
        ZeffyWebhookEventRepository repository = mock(ZeffyWebhookEventRepository.class);
        ZeffyWebhookEventStore store = new ZeffyWebhookEventStore(repository);
        ZeffyWebhookEvent event = ZeffyWebhookEvent.builder()
                .zeffyEventId("event-1").payloadSha256("original").status("RECEIVED")
                .deliveryCount(1).build();
        when(repository.findByZeffyEventIdForUpdate("event-1")).thenReturn(Optional.of(event));
        when(repository.save(event)).thenReturn(event);

        store.recordDuplicate("event-1", "different", OffsetDateTime.now(), OffsetDateTime.now());

        assertThat(event.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(event.getErrorSummary()).contains("different signed payload");
    }
}
