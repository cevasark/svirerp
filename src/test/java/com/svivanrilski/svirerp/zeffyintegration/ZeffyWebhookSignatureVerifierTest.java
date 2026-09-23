package com.svivanrilski.svirerp.zeffyintegration;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZeffyWebhookSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final String SECRET = "whsec_test-secret";
    private final ZeffyWebhookSignatureVerifier verifier =
            new ZeffyWebhookSignatureVerifier(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidRawBodySignatureWithinTolerance() throws Exception {
        byte[] body = "{ \"id\": \"unchanged whitespace\" }".getBytes(StandardCharsets.UTF_8);
        long timestamp = NOW.getEpochSecond() - 299;

        var verified = verifier.verify(body, header(timestamp, body), SECRET);

        assertThat(verified.signedAt().toInstant()).isEqualTo(Instant.ofEpochSecond(timestamp));
    }

    @Test
    void acceptsAnyMatchingV1SignatureDuringSecretRotation() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        long timestamp = NOW.getEpochSecond();
        String valid = signature(timestamp, body);

        assertThat(verifier.verify(body,
                "t=" + timestamp + ",v1=" + "00".repeat(32) + ",v1=" + valid, SECRET))
                .isNotNull();
    }

    @Test
    void rejectsInvalidMalformedStaleAndFutureSignatures() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        long now = NOW.getEpochSecond();

        assertThatThrownBy(() -> verifier.verify(body, null, SECRET))
                .isInstanceOf(ZeffyWebhookException.class);
        assertThatThrownBy(() -> verifier.verify(body, "t=nope,v1=abc", SECRET))
                .isInstanceOf(ZeffyWebhookException.class);
        assertThatThrownBy(() -> verifier.verify(body, header(now - 301, body), SECRET))
                .isInstanceOf(ZeffyWebhookException.class);
        assertThatThrownBy(() -> verifier.verify(body, header(now + 301, body), SECRET))
                .isInstanceOf(ZeffyWebhookException.class);
        assertThatThrownBy(() -> verifier.verify("changed".getBytes(StandardCharsets.UTF_8),
                        header(now, body), SECRET))
                .isInstanceOf(ZeffyWebhookException.class);
    }

    private String header(long timestamp, byte[] body) throws Exception {
        return "t=" + timestamp + ",v1=" + signature(timestamp, body);
    }

    private String signature(long timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(
                (timestamp + "." + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));
    }
}
