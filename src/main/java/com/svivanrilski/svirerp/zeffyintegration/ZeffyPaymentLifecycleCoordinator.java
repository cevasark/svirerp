package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Performs outbound fetches outside database transactions, then records one lifecycle event. */
@Service
@RequiredArgsConstructor
public class ZeffyPaymentLifecycleCoordinator {

    private final ZeffyWebhookEventRepository eventRepository;
    private final ZeffyApiClient apiClient;
    private final ZeffyPaymentPayload payload;
    private final ZeffyPaymentProcessingCoordinator paymentCoordinator;
    private final ZeffyPaymentLifecycleProcessor processor;

    public void process(UUID eventId) {
        ZeffyWebhookEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        try {
            switch (event.getEventType()) {
                case "payment.created" -> {
                    ZeffyPaymentPayload.PaymentData data = payload.parseEvent(event.getRawPayload());
                    if ("succeeded".equalsIgnoreCase(data.status())) {
                        paymentCoordinator.process(eventId);
                        processor.auditCreatedApplication(eventId);
                    } else processor.recordCreated(eventId);
                }
                case "payment.updated" -> fetchAndRecord(eventId, event.getZeffyResourceId());
                case "payment.deleted" -> processor.recordDeleted(eventId, false);
                default -> throw new IllegalArgumentException(
                        "Unsupported Zeffy payment lifecycle event: " + event.getEventType());
            }
        } catch (RuntimeException ex) {
            processor.markError(eventId, safeMessage(ex));
        }
    }

    private void fetchAndRecord(UUID eventId, String paymentId) {
        try {
            processor.recordUpdated(eventId, apiClient.fetchPayment(paymentId));
        } catch (ZeffyApiException ex) {
            if (ex.getStatus() == 404) processor.recordDeleted(eventId, true);
            else throw ex;
        }
    }

    private String safeMessage(RuntimeException ex) {
        if ((ex instanceof ZeffyApiException || ex instanceof IllegalArgumentException)
                && ex.getMessage() != null) return ex.getMessage();
        return "Unexpected Zeffy payment lifecycle processing failure";
    }
}
