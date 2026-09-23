package com.svivanrilski.svirerp.zeffyintegration;

import org.springframework.http.HttpStatus;

public class ZeffyWebhookException extends RuntimeException {

    private final HttpStatus status;

    public ZeffyWebhookException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
