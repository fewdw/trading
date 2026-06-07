package com.ufc.server.auth;

import org.springframework.http.HttpStatus;

/** An auth failure that maps to an HTTP status and a client-facing message. */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
