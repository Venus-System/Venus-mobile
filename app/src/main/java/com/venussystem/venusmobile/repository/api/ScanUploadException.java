package com.venussystem.venusmobile.repository.api;

import java.io.IOException;

/** Separate from API errors: a Cloudinary 403 must not trigger account registration. */
public final class ScanUploadException extends IOException {
    public enum Kind { NETWORK, TIMEOUT, HTTP, INVALID_RESPONSE, EXPIRED_SIGNATURE, LOCAL, CONFIGURATION }
    public final Kind kind;
    public final int httpCode;
    public ScanUploadException(Kind kind, int httpCode, String message) {
        super(message); this.kind = kind; this.httpCode = httpCode;
    }
}
