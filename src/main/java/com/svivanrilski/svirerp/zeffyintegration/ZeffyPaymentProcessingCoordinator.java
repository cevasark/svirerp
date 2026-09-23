package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Keeps the apply and error-recording transactions separate. */
@Service
@RequiredArgsConstructor
public class ZeffyPaymentProcessingCoordinator {

    private final ZeffyPaymentProcessor processor;

    public void process(UUID eventId) {
        try {
            processor.applyEvent(eventId);
        } catch (RuntimeException ex) {
            processor.markError(eventId, safeMessage(ex));
        }
    }

    private String safeMessage(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException && ex.getMessage() != null) return ex.getMessage();
        return "Unexpected Zeffy payment processing failure";
    }
}
