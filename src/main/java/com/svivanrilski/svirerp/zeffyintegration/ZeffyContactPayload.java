package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ZeffyContactPayload {

    private final ObjectMapper objectMapper;

    public record ContactData(
            String id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String email, String firstName, String lastName, String phoneNumber,
            String addressLine1, String city, String state, String postalCode, String country,
            String donorType, BigDecimal totalContribution, String currency, Integer donationCount,
            OffsetDateTime firstDonationAt, OffsetDateTime lastDonationAt,
            String payloadSha256, JsonNode payload) {
    }

    public ContactData parseEvent(String rawEnvelope) {
        try {
            JsonNode root = objectMapper.readTree(rawEnvelope);
            return parse(root.path("data"));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Zeffy contact event payload is not valid JSON", ex);
        }
    }

    public ContactData parse(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Zeffy contact payload must be an object");
        }
        String id = requiredText(node, "id");
        JsonNode address = node.path("address");
        String email = text(node, "email");
        if (email != null) email = email.toLowerCase(Locale.ROOT);
        return new ContactData(
                id, unixTime(node.get("created"), "created"), unixTime(node.get("updated"), "updated"),
                email, text(node, "first_name"), text(node, "last_name"), text(node, "phone_number"),
                text(address, "line1"), text(address, "city"), text(address, "state"),
                text(address, "postal_code"), text(address, "country"), text(node, "donor_type"),
                cents(node.get("total_contribution")), text(node, "currency"), integer(node.get("donation_count")),
                optionalUnixTime(node.get("first_donation_date")),
                optionalUnixTime(node.get("last_donation_date")), fingerprint(node), node.deepCopy());
    }

    private OffsetDateTime unixTime(JsonNode node, String field) {
        OffsetDateTime value = optionalUnixTime(node);
        if (value == null) throw new IllegalArgumentException("Zeffy contact " + field + " is missing or invalid");
        return value;
    }

    private OffsetDateTime optionalUnixTime(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isNumber()) throw new IllegalArgumentException("Zeffy contact timestamp is invalid");
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(node.decimalValue()
                .movePointRight(3).longValue()), ZoneOffset.UTC);
    }

    private BigDecimal cents(JsonNode node) {
        if (node == null || !node.isIntegralNumber()) {
            throw new IllegalArgumentException("Zeffy contact total contribution is missing or invalid");
        }
        return BigDecimal.valueOf(node.longValue(), 2);
    }

    private Integer integer(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException("Zeffy contact donation count is missing or invalid");
        }
        return node.intValue();
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) throw new IllegalArgumentException("Zeffy contact " + field + " is required");
        return value;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Zeffy contact " + field + " is invalid");
        String trimmed = value.textValue().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String fingerprint(JsonNode node) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(node.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
