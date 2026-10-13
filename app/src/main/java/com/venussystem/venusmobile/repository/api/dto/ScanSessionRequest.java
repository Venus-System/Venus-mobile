package com.venussystem.venusmobile.repository.api.dto;

import java.util.List;

/** Wire contract of Venus-CRUD POST /api/scan-sessions. No local paths or secrets. */
public final class ScanSessionRequest {
    public String scanId;
    public String firebaseUid;
    public Device device;
    public String startedAt;
    public String finishedAt;
    public QualityCheck qualityCheck;
    public Images images;
    public Ocr ocr;
    public List<Ingredient> ingredients;

    public static final class Device {
        public String deviceId, platform, appVersion, model;
        public Device(String deviceId, String platform, String appVersion, String model) {
            this.deviceId = deviceId; this.platform = platform;
            this.appVersion = appVersion; this.model = model;
        }
    }

    public static final class QualityCheck {
        public Double blurScore, brightness;
        public Boolean backgroundOk;
        public String status;
        public QualityCheck(Double blurScore, Double brightness, Boolean backgroundOk, String status) {
            this.blurScore = blurScore; this.brightness = brightness;
            this.backgroundOk = backgroundOk; this.status = status;
        }
    }

    public static final class Image {
        public String publicId, format;
        public Integer width, height;
        public Long bytes;
    }

    public static final class Images { public Image front, back; }
    public static final class Ocr { public Front front; public Back back; }
    public static final class Front {
        public String fullText;
        public List<String> lines;
        public FrontExtracted extracted;
    }
    public static final class Back {
        public String fullText;
        public List<String> lines;
        public BackExtracted extracted;
    }
    public static final class FrontExtracted {
        public String brand, productName, presentation, capacity, category;
        public List<String> claims;
    }
    public static final class BackExtracted {
        public String ingredientsText, manufacturer, manufacturerAddress, contact, country;
        public String batch, registrationNumber, netContent, usage, precautions, warnings, barcode, otherText;
        public List<String> claims;
    }
    public static final class Ingredient {
        public Integer position;
        public String rawName;
        public Ingredient(Integer position, String rawName) {
            this.position = position; this.rawName = rawName;
        }
    }
}
