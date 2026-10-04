package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;
import java.io.Serializable;

/** Measurements produced locally from the two captured images. */
public final class ScanPhotoQuality implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double blurScore;
    private final double brightness;
    private final boolean backgroundOk;
    private final String status;

    public ScanPhotoQuality(double blurScore, double brightness, boolean backgroundOk,
            @NonNull String status) {
        if (!Double.isFinite(blurScore) || !Double.isFinite(brightness))
            throw new IllegalArgumentException("Métrica de qualidade inválida.");
        this.blurScore = clamp(blurScore);
        this.brightness = clamp(brightness);
        this.backgroundOk = backgroundOk;
        this.status = status;
    }
    public double getBlurScore() { return blurScore; }
    public double getBrightness() { return brightness; }
    public boolean isBackgroundOk() { return backgroundOk; }
    @NonNull public String getStatus() { return status; }
    private static double clamp(double value) { return Math.max(0d, Math.min(1d, value)); }
}
