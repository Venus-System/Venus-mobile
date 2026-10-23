package com.venussystem.venusmobile.repository.api;

import java.io.IOException;

/** Safe, user-facing errors. Never includes a server body, token or signature. */
public final class ScanApiException extends IOException {
    public enum Kind { AUTH, FORBIDDEN, NETWORK, TIMEOUT, HTTP, INVALID_RESPONSE, INVALID_REQUEST }
    public final Kind kind;
    public final int httpCode;
    /** Bounded, server-provided diagnostic fields intended for local logs only. */
    public final String diagnostic;

    public ScanApiException(Kind kind, int httpCode, String message) {
        this(kind, httpCode, message, null);
    }

    public ScanApiException(Kind kind, int httpCode, String message, String diagnostic) {
        super(message);
        this.kind = kind;
        this.httpCode = httpCode;
        this.diagnostic = diagnostic;
    }
}
