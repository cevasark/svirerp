package com.svivanrilski.svirerp.zeffyintegration;

import lombok.Getter;

import java.time.Duration;

@Getter
public class ZeffyApiException extends RuntimeException {

    private final int status;
    private final Duration retryAfter;

    public ZeffyApiException(int status, String message) {
        this(status, message, null, null);
    }

    public ZeffyApiException(int status, String message, Duration retryAfter) {
        this(status, message, retryAfter, null);
    }

    public ZeffyApiException(int status, String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryAfter = retryAfter;
    }
}
