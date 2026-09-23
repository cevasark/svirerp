package com.svivanrilski.svirerp.zeffyintegration;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class ZeffyWebhookController {

    static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private final ZeffyWebhookService service;

    @PostMapping(value = "/api/webhooks/zeffy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(HttpServletRequest request,
                                        @RequestHeader(value = "Zeffy-Signature", required = false)
                                        String signature) throws IOException {
        if (request.getContentLengthLong() > MAX_PAYLOAD_BYTES) {
            throw new ZeffyWebhookException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Zeffy webhook payload exceeds the one-megabyte limit");
        }
        byte[] payload = request.getInputStream().readNBytes(MAX_PAYLOAD_BYTES + 1);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new ZeffyWebhookException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Zeffy webhook payload exceeds the one-megabyte limit");
        }
        service.receive(payload, signature);
        return ResponseEntity.ok().build();
    }
}
