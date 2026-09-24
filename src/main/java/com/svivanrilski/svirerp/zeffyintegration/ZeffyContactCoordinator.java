package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Fetches ID-only contact updates outside the database transaction. */
@Service
@RequiredArgsConstructor
public class ZeffyContactCoordinator {

    private final ZeffyWebhookEventRepository eventRepository;
    private final ZeffyApiClient apiClient;
    private final ZeffyContactProcessor processor;

    public void process(UUID eventId) {
        ZeffyWebhookEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        try {
            switch (event.getEventType()) {
                case "contact.created" -> processor.applyCreatedEvent(eventId);
                case "contact.updated" -> fetchAndApply(eventId, event.getZeffyResourceId());
                case "contact.deleted" -> processor.recordDeleted(eventId, false);
                default -> throw new IllegalArgumentException(
                        "Unsupported Zeffy contact event: " + event.getEventType());
            }
        } catch (RuntimeException ex) {
            processor.markError(eventId, safeMessage(ex));
        }
    }

    private void fetchAndApply(UUID eventId, String contactId) {
        try {
            processor.applyFromWebhook(eventId, apiClient.fetchContact(contactId));
        } catch (ZeffyApiException ex) {
            if (ex.getStatus() == 404) processor.recordDeleted(eventId, true);
            else throw ex;
        }
    }

    private String safeMessage(RuntimeException ex) {
        if ((ex instanceof ZeffyApiException || ex instanceof IllegalArgumentException)
                && ex.getMessage() != null) return ex.getMessage();
        return "Unexpected Zeffy contact processing failure";
    }
}
