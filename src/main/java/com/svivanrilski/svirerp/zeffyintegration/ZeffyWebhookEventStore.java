package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ZeffyWebhookEventStore {

    private static final String PAYLOAD_MISMATCH =
            "A duplicate Zeffy event ID was received with a different signed payload";

    private final ZeffyWebhookEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffyWebhookEvent insert(ZeffyWebhookEvent event) {
        return repository.saveAndFlush(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZeffyWebhookEvent recordDuplicate(String eventId, String payloadHash,
                                              OffsetDateTime signatureTimestamp,
                                              OffsetDateTime receivedAt) {
        ZeffyWebhookEvent existing = repository.findByZeffyEventIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Duplicate event could not be reloaded"));
        existing.setDeliveryCount(existing.getDeliveryCount() + 1);
        existing.setSignatureTimestamp(signatureTimestamp);
        existing.setLastReceivedAt(receivedAt);
        if (!existing.getPayloadSha256().equals(payloadHash)) {
            existing.setStatus("NEEDS_REVIEW");
            existing.setErrorSummary(PAYLOAD_MISMATCH);
        }
        return repository.save(existing);
    }
}
