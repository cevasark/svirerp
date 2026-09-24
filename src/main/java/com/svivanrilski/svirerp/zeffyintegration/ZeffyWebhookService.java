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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
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
    private final ZeffyPaymentProcessingCoordinator processingCoordinator;
    private final ZeffyPaymentLifecycleCoordinator lifecycleCoordinator;
    private final ZeffyContactCoordinator contactCoordinator;
    private final ZeffyPaymentChangeRepository changeRepository;
    private final ZeffyRefundRepository refundRepository;
    private final ZeffyDisputeRepository disputeRepository;

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
            if ("LIVE".equalsIgnoreCase(mode) && supported) {
                if (envelope.type().startsWith("payment.")) {
                    processPaymentEvent(inserted.getId(), envelope.type());
                } else {
                    contactCoordinator.process(inserted.getId());
                }
                String finalStatus = eventRepository.findById(inserted.getId())
                        .map(ZeffyWebhookEvent::getStatus).orElse(inserted.getStatus());
                return new ReceiptResponse(inserted.getId(), finalStatus, false);
            }
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

    public EventResponse reprocess(UUID eventId) {
        String mode = settingService.getDecryptedValue(MODE).orElse("DISABLED");
        if (!"LIVE".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Zeffy payment reprocessing requires LIVE mode");
        }
        ZeffyWebhookEvent stored = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        if (stored.getEventType().startsWith("payment.")) processPaymentEvent(eventId, stored.getEventType());
        else if (stored.getEventType().startsWith("contact.")) contactCoordinator.process(eventId);
        else throw new IllegalArgumentException("Unsupported Zeffy event type: " + stored.getEventType());
        ZeffyWebhookEvent event = eventRepository.findDetailedById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public LifecycleResponse lifecycle(UUID eventId) {
        ZeffyWebhookEvent event = eventRepository.findDetailedById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy webhook event not found: " + eventId));
        ZeffyPayment payment = event.getZeffyPayment();
        if (payment == null) {
            return new LifecycleResponse(eventId, null, null, List.of(), List.of(), List.of());
        }
        List<PaymentChangeResponse> changes = changeRepository
                .findByZeffyPayment_IdOrderByObservedAtDesc(payment.getId()).stream()
                .map(change -> new PaymentChangeResponse(change.getId(), change.getChangeKind(),
                        change.getChangedFields(), change.getSummary(), text(change.getObservedAt()),
                        change.getPreviousAmount(), change.getCurrentAmount(), change.getCorrectionStatus(),
                        change.getCorrectionJournalEntry() == null ? null
                                : change.getCorrectionJournalEntry().getId(),
                        change.getCorrectionSummary(), text(change.getCorrectedAt())))
                .toList();
        List<RefundResponse> refunds = refundRepository
                .findByZeffyPayment_IdOrderByRefundCreatedAtAsc(payment.getId()).stream()
                .map(refund -> new RefundResponse(refund.getId(), refund.getZeffyRefundId(), refund.getAmount(),
                        refund.getCurrency(), refund.getStatus(), text(refund.getRefundCreatedAt()),
                        refund.getCorrectionStatus(), refund.getCorrectionJournalEntry() == null
                                ? null : refund.getCorrectionJournalEntry().getId(),
                        refund.getCorrectedAmount(), refund.getCorrectionSummary(), text(refund.getCorrectedAt())))
                .toList();
        List<DisputeResponse> disputes = disputeRepository
                .findByZeffyPayment_IdOrderByDisputeCreatedAtAsc(payment.getId()).stream()
                .map(dispute -> new DisputeResponse(dispute.getId(), dispute.getZeffyDisputeId(),
                        dispute.getAmount(), dispute.getCurrency(), dispute.getStatus(), dispute.getReason(),
                        text(dispute.getDisputeCreatedAt()), dispute.getCorrectionStatus(),
                        dispute.getCorrectionJournalEntry() == null
                                ? null : dispute.getCorrectionJournalEntry().getId(),
                        dispute.getCorrectedAmount(), dispute.getCorrectionSummary(), text(dispute.getCorrectedAt())))
                .toList();
        return new LifecycleResponse(eventId, payment.getId(), text(payment.getDeletedAt()),
                changes, refunds, disputes);
    }

    private void processPaymentEvent(UUID eventId, String eventType) {
        if ("payment.completed".equals(eventType)) processingCoordinator.process(eventId);
        else lifecycleCoordinator.process(eventId);
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
        ZeffyPayment payment = event.getZeffyPayment();
        return new EventResponse(event.getId(), event.getZeffyEventId(), event.getEventType(),
                event.getSchemaVersion(), event.getResourceType(), event.getZeffyResourceId(),
                event.getStatus(), event.getDeliveryCount(), text(event.getDispatchedAt()),
                text(event.getReceivedAt()), text(event.getLastReceivedAt()),
                event.getProcessingAttemptCount(), text(event.getLastAttemptedAt()),
                text(event.getProcessedAt()), event.getErrorSummary(),
                event.getProcessingSummary(),
                payment == null ? null : payment.getId(),
                payment == null ? null : payment.getStatus(),
                payment == null ? null : payment.getAmount(),
                payment == null ? null : payment.getEligibleAmount(),
                payment == null ? null : payment.getCurrency(),
                payment == null ? null : text(payment.getPaymentCreatedAt()),
                payment == null ? null : payment.getCampaignId(),
                payment == null ? null : payment.getCampaignTitle(),
                payment == null ? null : payment.getMappingAction(),
                payment == null || payment.getMappedFund() == null ? null : payment.getMappedFund().getFundName(),
                payment == null || payment.getMappedAccount() == null ? null
                        : payment.getMappedAccount().getAccountNumber() + " " + payment.getMappedAccount().getAccountName(),
                payment != null && Boolean.TRUE.equals(payment.getMembershipCredit()),
                payment == null ? null : payment.getBuyerEmail(),
                payment == null || payment.getPerson() == null ? null : payment.getPerson().getId(),
                payment == null || payment.getMember() == null ? null : payment.getMember().getId(),
                payment == null || payment.getMemberPayment() == null ? null : payment.getMemberPayment().getId(),
                payment == null || payment.getJournalEntry() == null ? null : payment.getJournalEntry().getId());
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
                                String lastAttemptedAt, String processedAt, String errorSummary,
                                String processingSummary,
                                UUID paymentRecordId, String paymentStatus, BigDecimal amount,
                                BigDecimal eligibleAmount, String currency, String paymentCreatedAt,
                                String campaignId, String campaignTitle, String mappingAction,
                                String mappedFund, String mappedAccount, boolean membershipCredit,
                                String buyerEmail, UUID personId, UUID memberId,
                                UUID memberPaymentId, UUID journalEntryId) {
    }

    public record LifecycleResponse(UUID eventId, UUID paymentRecordId, String deletedAt,
                                    List<PaymentChangeResponse> changes,
                                    List<RefundResponse> refunds,
                                    List<DisputeResponse> disputes) {
    }

    public record PaymentChangeResponse(UUID id, String changeKind, String changedFields,
                                        String summary, String observedAt,
                                        BigDecimal previousAmount, BigDecimal currentAmount,
                                        String correctionStatus, UUID correctionJournalEntryId,
                                        String correctionSummary, String correctedAt) {
    }

    public record RefundResponse(UUID id, String zeffyRefundId, BigDecimal amount, String currency,
                                 String status, String refundCreatedAt, String correctionStatus,
                                 UUID correctionJournalEntryId, BigDecimal correctedAmount,
                                 String correctionSummary,
                                 String correctedAt) {
    }

    public record DisputeResponse(UUID id, String zeffyDisputeId, BigDecimal amount, String currency,
                                  String status, String reason, String disputeCreatedAt,
                                  String correctionStatus, UUID correctionJournalEntryId,
                                  BigDecimal correctedAmount, String correctionSummary, String correctedAt) {
    }
}
