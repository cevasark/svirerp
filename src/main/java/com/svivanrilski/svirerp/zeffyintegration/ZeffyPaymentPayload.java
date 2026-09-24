package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Parses, fingerprints, compares, and applies the normalized Zeffy payment snapshot. */
@Component
@RequiredArgsConstructor
public class ZeffyPaymentPayload {

    private final ObjectMapper objectMapper;

    public PaymentData parseEvent(String rawPayload) {
        final JsonNode data;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            data = root == null ? null : root.get("data");
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored Zeffy event payload is not valid JSON");
        }
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("Stored Zeffy event has no payment data object");
        }
        return parse(data);
    }

    public PaymentData parse(JsonNode data) {
        if (data == null || !data.isObject()) throw new IllegalArgumentException("Payment data must be an object");
        JsonNode buyer = data.path("buyer");
        JsonNode address = buyer.path("address");
        JsonNode disputeNode = data.path("dispute");
        List<RefundData> refunds = new ArrayList<>();
        JsonNode refundsNode = data.path("refunds");
        if (refundsNode.isArray()) {
            for (JsonNode refund : refundsNode) refunds.add(parseRefund(refund));
        }
        DisputeData dispute = disputeNode.isObject() ? parseDispute(disputeNode) : null;
        return new PaymentData(
                text(data, "id"), text(data, "status"), text(data, "refund_status"),
                dispute == null ? null : dispute.status(),
                cents(data.get("amount")), cents(data.get("eligible_amount")), text(data, "currency"),
                text(data, "type"), unixTime(data.get("created")), text(data, "campaign_id"),
                text(data, "description"), text(data, "contact"), text(buyer, "email"),
                text(buyer, "first_name"), text(buyer, "last_name"), text(address, "line1"),
                text(address, "city"), text(address, "state"), text(address, "postal_code"),
                data.toString(), data, List.copyOf(refunds), dispute);
    }

    public String fingerprint(JsonNode payment) {
        if (payment == null) throw new IllegalArgumentException("Payment payload is missing");
        try {
            Object value = objectMapper.convertValue(payment, Object.class);
            String canonical = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value);
            return sha256(canonical);
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            throw new IllegalArgumentException("Payment payload could not be fingerprinted", ex);
        }
    }

    public List<String> changedFields(ZeffyPayment previous, PaymentData current) {
        List<String> changed = new ArrayList<>();
        changed(changed, "status", previous.getStatus(), current.status());
        changed(changed, "refund_status", previous.getRefundStatus(), current.refundStatus());
        changed(changed, "dispute", previous.getDisputeStatus(), current.disputeStatus());
        changed(changed, "amount", previous.getAmount(), current.amount());
        changed(changed, "eligible_amount", previous.getEligibleAmount(), current.eligibleAmount());
        changed(changed, "currency", previous.getCurrency(), upper(current.currency()));
        changed(changed, "payment_type", previous.getPaymentType(), current.paymentType());
        changed(changed, "campaign_id", previous.getCampaignId(), current.campaignId());
        changed(changed, "campaign_title", previous.getCampaignTitle(), current.campaignTitle());
        changed(changed, "contact_id", previous.getContactId(), current.contactId());
        changed(changed, "buyer_email", previous.getBuyerEmail(), normalizedEmail(current.email()));
        changed(changed, "buyer_first_name", previous.getBuyerFirstName(), trim(current.firstName()));
        changed(changed, "buyer_last_name", previous.getBuyerLastName(), trim(current.lastName()));
        return List.copyOf(changed);
    }

    public void applySnapshot(ZeffyPayment target, PaymentData source, String origin,
                              OffsetDateTime observedAt) {
        target.setStatus(source.status());
        target.setRefundStatus(source.refundStatus());
        target.setDisputeStatus(source.disputeStatus());
        target.setAmount(source.amount());
        target.setEligibleAmount(source.eligibleAmount());
        target.setCurrency(upper(source.currency()));
        target.setPaymentType(source.paymentType());
        target.setPaymentCreatedAt(source.createdAt());
        target.setCampaignId(source.campaignId());
        target.setCampaignTitle(source.campaignTitle());
        target.setContactId(source.contactId());
        target.setBuyerEmail(normalizedEmail(source.email()));
        target.setBuyerFirstName(trim(source.firstName()));
        target.setBuyerLastName(trim(source.lastName()));
        target.setLatestPayload(source.payload());
        target.setLatestPayloadSha256(fingerprint(source.payloadNode()));
        target.setDeletedAt(null);
        if ("WEBHOOK".equals(origin) || "API_FETCH".equals(origin)) {
            if (target.getLastEventAt() == null || observedAt.isAfter(target.getLastEventAt())) {
                target.setLastEventAt(observedAt);
            }
        }
        if ("API_SYNC".equals(origin)) {
            target.setLastSyncedAt(observedAt);
        }
        if ("API_FETCH".equals(origin)) target.setLastFetchedAt(OffsetDateTime.now(ZoneOffset.UTC));
        else if ("API_SYNC".equals(origin)) target.setLastFetchedAt(observedAt);
    }

    public String normalizedEmail(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    public String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RefundData parseRefund(JsonNode node) {
        return new RefundData(text(node, "id"), cents(node.get("amount")), text(node, "currency"),
                text(node, "status"), unixTime(node.get("created")));
    }

    private DisputeData parseDispute(JsonNode node) {
        return new DisputeData(text(node, "id"), cents(node.get("amount")), text(node, "currency"),
                text(node, "status"), text(node, "reason"), unixTime(node.get("created")));
    }

    private void changed(List<String> result, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) result.add(field);
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private BigDecimal cents(JsonNode value) {
        if (value == null || !value.isNumber()) return null;
        try {
            BigDecimal cents = value.decimalValue().stripTrailingZeros();
            if (cents.scale() > 0) return null;
            return BigDecimal.valueOf(cents.longValueExact(), 2);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private OffsetDateTime unixTime(JsonNode value) {
        if (value == null || !value.isNumber()) return null;
        try {
            BigDecimal seconds = value.decimalValue().stripTrailingZeros();
            if (seconds.scale() > 0) return null;
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds.longValueExact()), ZoneOffset.UTC);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private String text(JsonNode parent, String field) {
        if (parent == null || !parent.isObject()) return null;
        JsonNode value = parent.get(field);
        return value != null && value.isTextual() ? trim(value.textValue()) : null;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record PaymentData(
            String id, String status, String refundStatus, String disputeStatus, BigDecimal amount,
            BigDecimal eligibleAmount, String currency, String paymentType, OffsetDateTime createdAt,
            String campaignId, String campaignTitle, String contactId, String email, String firstName,
            String lastName, String addressLine1, String city, String state, String postalCode,
            String payload, JsonNode payloadNode, List<RefundData> refunds, DisputeData dispute) {
    }

    public record RefundData(String id, BigDecimal amount, String currency, String status,
                             OffsetDateTime createdAt) {
    }

    public record DisputeData(String id, BigDecimal amount, String currency, String status,
                              String reason, OffsetDateTime createdAt) {
    }
}
