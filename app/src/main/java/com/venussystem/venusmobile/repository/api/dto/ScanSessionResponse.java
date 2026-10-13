package com.venussystem.venusmobile.repository.api.dto;

/** Summary returned by GET /api/scan-sessions/user/{userId}. */
public final class ScanSessionResponse {
    public String id, scanId, status, startedAt, finishedAt, createdAt, updatedAt;
    public Source source;
    public Images images;
    public Ocr ocr;
    public Sync sync;

    public static final class Source {
        public Long userId;
        public String firebaseUid, userName;
    }

    public static final class Images {
        public Image front, back;
    }

    public static final class Image {
        public String publicId, secureUrl, format;
        public Integer width, height;
        public Long bytes;
    }

    public static final class Ocr {
        public Front front;
    }

    public static final class Front {
        public Extracted extracted;
    }

    public static final class Extracted {
        public String brand, productName, presentation, capacity, category;
    }

    public static final class Sync {
        public Long productId, productVersionId;
    }
}
