package com.venussystem.venusmobile.domain.scan;

import com.venussystem.venusmobile.model.ScanPhotoQuality;

/** Production policy for quality measurements sent with a scan. */
public final class ScanPhotoQualityPolicy {
    private static final double MAX_BLUR_SCORE = .85d;
    private static final double MIN_BRIGHTNESS = .08d;
    private static final double MAX_BRIGHTNESS = .95d;

    private ScanPhotoQualityPolicy() { }

    /** A non-uniform background is advisory; it must not discard a readable label. */
    public static boolean backgroundIsAdvisory(ScanPhotoQuality quality) {
        return quality != null && !quality.isBackgroundOk() && !shouldBlock(quality);
    }

    /** Only measurements that make OCR unreliable enough are blocked locally. */
    public static boolean shouldBlock(ScanPhotoQuality quality) {
        if (quality == null) return true;
        return quality.getBlurScore() >= MAX_BLUR_SCORE
                || quality.getBrightness() < MIN_BRIGHTNESS
                || quality.getBrightness() > MAX_BRIGHTNESS;
    }

    public static String blockReason(ScanPhotoQuality quality) {
        if (quality == null) return "QUALITY_MISSING";
        if (quality.getBlurScore() >= MAX_BLUR_SCORE) return "BLUR_TOO_HIGH";
        if (quality.getBrightness() < MIN_BRIGHTNESS) return "TOO_DARK";
        if (quality.getBrightness() > MAX_BRIGHTNESS) return "TOO_BRIGHT";
        return "NONE";
    }
}
