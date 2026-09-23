package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svivanrilski.svirerp.settings.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZeffyWebhookService {

    private static final String WEBHOOK_SECRET = "zeffy.webhook-signing-secret";
    private static final String MODE = "zeffy.integration-mode";
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "payment.completed", "payment.created", "payment.updated", "payment.deleted",
            "contact.created", "contact.updated", "contact.deleted");

    private final AppSettingService settingService;
    private final ZeffyWebhookSignatureVerifier signatureVerifier;
    private final ZeffyWebhookEventStore eventStore;
    private final ZeffyWebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public ReceiptResponse receive(byte[] rawBody, String signatureHeader) {
        String mode = settingService.getDecryptedValue(MODE).orElse("DISABLED");
        if ("DISABLED".equalsIgnoreCase(mode)) {
            throw new ZeffyWebhookException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Zeffy webhook receipt is disabled");
        }
        String secret = settingService.getDecryptedValue(WEBHOOK_SECRET)
                .orElseThrow(() -> new ZeffyWebhookException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Zeffy webhook signing secret is not configured"));
        ZeffyWebhookSignatureVerifier.VerifiedSignature signature =
                signatureVerifier.verify(rawBody, signatureHeader, secret);
        Envelope envelope = parseEnvelope(rawBody);
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String payloadHash = sha256(rawBody);
        boolean supported = envelope.version() == 1 && SUPPORTED_TYPES.contains(envelope.type());

        ZeffyWebhookEvent event = ZeffyWebhookEvent.builder()
                .zeffyEventId(envelope.id())
                .eventType(envelope.type())
                .schemaVersion(envelope.version())
                .resourceType(resourceType(envelope.type()))
                .zeffyResourceId(envelope.resourceId())
                .dispatchedAt(envelope.dispatchedAt())
                .signatureTimestamp(signature.signedAt())
                .rawPayload(new String(rawBody, StandardCharsets.UTF_8))
                .payloadSha256(payloadHash)
                .status(supported ? "RECEIVED" : "UNSUPPORTED")
                .receivedAt(receivedAt)
                .lastReceivedAt(receivedAt)
                .build();

        try {
            ZeffyWebhookEvent inserted = eventStore.insert(event);
            return new ReceiptResponse(inserted.getId(), inserted.getStatus(), false);
        } catch (DataIntegrityViolationException duplicateOrConstraintFailure) {
            try {
                ZeffyWebhookEvent duplicate = eventStore.recordDuplicate(
                        envelope.id(), payloadHash, signature.signedAt(), receivedAt);
                return new ReceiptResponse(duplicate.getId(), duplicate.getStatus(), true);
            } catch (RuntimeException reloadFailure) {
                duplicateOrConstraintFailure.addSuppressed(reloadFailure);
                throw new ZeffyWebhookException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not store the Zeffy webhook event");
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> findEvents(String eventType, String status, String resourceId,
                                          OffsetDateTime receivedFrom, OffsetDateTime receivedTo,
                                          Pageable pageable) {
        Specification<ZeffyWebhookEvent> spec = Specification.where(null);
        if (hasText(eventType)) {
            String value = eventType.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("eventType")), value));
        }
        if (hasText(status)) {
            String value = status.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), value));
        }
        if (hasText(resourceId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("zeffyResourceId"), resourceId.trim()));
        }
        if (receivedFrom != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("receivedAt"), receivedFrom));
        }
        if (receivedTo != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("receivedAt"), receivedTo));
        }
        return eventRepository.findAll(spec, pageable).map(this::toResponse);
    }

    private Envelope parseEnvelope(byte[] rawBody) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException ex) {
            throw badRequest("Zeffy webhook body is not valid JSON");
        }
        if (root == null || !root.isObject()) throw badRequest("Zeffy webhook body must be a JSON object");

        String id = requiredText(root, "id");
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw badRequest("Zeffy webhook event ID must be a UUID");
        }
        String type = requiredText(root, "type");
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isIntegralNumber() || !versionNode.canConvertToInt()) {
            throw badRequest("Zeffy webhook version is required and must be an integer");
        }
        int version = versionNode.intValue();
        OffsetDateTime dispatchedAt;
        try {
            dispatchedAt = OffsetDateTime.parse(requiredText(root, "dispatchedAt"));
        } catch (DateTimeParseException ex) {
            throw badRequest("Zeffy webhook dispatchedAt must be an ISO-8601 timestamp");
        }
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) throw badRequest("Zeffy webhook data object is required");
        String resourceId = requiredText(data, "id");
        return new Envelope(id, type, version, dispatchedAt, resourceId);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw badRequest("Zeffy webhook " + field + " is required");
        }
        return value.textValue();
    }

    private String resourceType(String eventType) {
        if (eventType.startsWith("payment.")) return "PAYMENT";
        if (eventType.startsWith("contact.")) return "CONTACT";
        return "UNKNOWN";
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private EventResponse toResponse(ZeffyWebhookEvent event) {
        return new EventResponse(event.getId(), event.getZeffyEventId(), event.getEventType(),
                event.getSchemaVersion(), event.getResourceType(), event.getZeffyResourceId(),
                event.getStatus(), event.getDeliveryCount(), text(event.getDispatchedAt()),
                text(event.getReceivedAt()), text(event.getLastReceivedAt()),
                event.getProcessingAttemptCount(), text(event.getLastAttemptedAt()),
                text(event.getProcessedAt()), event.getErrorSummary());
    }

    private String text(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ZeffyWebhookException badRequest(String message) {
        return new ZeffyWebhookException(HttpStatus.BAD_REQUEST, message);
    }

    private record Envelope(String id, String type, int version, OffsetDateTime dispatchedAt,
                            String resourceId) {
    }

    public record ReceiptResponse(UUID id, String status, boolean duplicate) {
    }

    public record EventResponse(UUID id, String zeffyEventId, String eventType, int schemaVersion,
                                String resourceType, String zeffyResourceId, String status,
                                int deliveryCount, String dispatchedAt, String receivedAt,
                                String lastReceivedAt, int processingAttemptCount,
                                String lastAttemptedAt, String processedAt, String errorSummary) {
    }
}
