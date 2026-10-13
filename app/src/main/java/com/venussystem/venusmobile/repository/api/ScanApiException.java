package com.venussystem.venusmobile.repository.api;

import java.io.IOException;

/** Safe, user-facing errors. Never includes a server body, token or signature. */
public final class ScanApiException extends IOException {
    public enum Kind { AUTH, FORBIDDEN, NETWORK, TIMEOUT, HTTP, INVALID_RESPONSE, INVALID_REQUEST }
    public final Kind kind;
    public final int httpCode;

    public ScanApiException(Kind kind, int httpCode, String message) {
        super(message);
        this.kind = kind;
        this.httpCode = httpCode;
    }
}
