package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class ZeffyWebhookSignatureVerifier {

    static final long TOLERANCE_SECONDS = 5 * 60;
    private final Clock clock;

    public ZeffyWebhookSignatureVerifier() {
        this(Clock.systemUTC());
    }

    ZeffyWebhookSignatureVerifier(Clock clock) {
        this.clock = clock;
    }

    public VerifiedSignature verify(byte[] rawBody, String signatureHeader, String secret) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw invalid("Missing Zeffy-Signature header");
        }
        if (secret == null || secret.isBlank()) {
            throw new ZeffyWebhookException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Zeffy webhook signing secret is not configured");
        }

        String timestampText = null;
        List<String> signatureTexts = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            int separator = part.indexOf('=');
            if (separator <= 0) throw invalid("Malformed Zeffy-Signature header");
            String key = part.substring(0, separator).trim();
            String value = part.substring(separator + 1).trim();
            if ("t".equals(key)) {
                if (timestampText != null || value.isEmpty()) {
                    throw invalid("Malformed Zeffy-Signature timestamp");
                }
                timestampText = value;
            } else if ("v1".equals(key) && !value.isEmpty()) {
                signatureTexts.add(value);
            }
        }
        if (timestampText == null || signatureTexts.isEmpty()) {
            throw invalid("Malformed Zeffy-Signature header");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException ex) {
            throw invalid("Malformed Zeffy-Signature timestamp");
        }
        long now = clock.instant().getEpochSecond();
        if (timestamp < now - TOLERANCE_SECONDS || timestamp > now + TOLERANCE_SECONDS) {
            throw invalid("Zeffy-Signature timestamp is outside the allowed five-minute window");
        }

        byte[] expected = hmac(secret, timestampText, rawBody);
        boolean matched = false;
        for (String signatureText : signatureTexts) {
            byte[] received = decodeSignature(signatureText);
            matched |= MessageDigest.isEqual(expected, received);
        }
        if (!matched) throw invalid("Invalid Zeffy webhook signature");

        return new VerifiedSignature(OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC));
    }

    private byte[] hmac(String secret, String timestampText, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestampText.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    private byte[] decodeSignature(String value) {
        try {
            byte[] decoded = HexFormat.of().parseHex(value);
            return decoded.length == 32 ? decoded : new byte[0];
        } catch (IllegalArgumentException ex) {
            return new byte[0];
        }
    }

    private ZeffyWebhookException invalid(String message) {
        return new ZeffyWebhookException(HttpStatus.BAD_REQUEST, message);
    }

    public record VerifiedSignature(OffsetDateTime signedAt) {
    }
}
