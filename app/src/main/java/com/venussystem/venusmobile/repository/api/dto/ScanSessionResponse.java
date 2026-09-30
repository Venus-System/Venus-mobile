package com.venussystem.venusmobile.repository.api.dto;

/** Confirmation only. Review/matching fields remain owned by the backend. */
public final class ScanSessionResponse {
    public String id, scanId, status;
    public Source source;
    public ScanSessionRequest.Images images;
    public static final class Source { public String firebaseUid; }
}
