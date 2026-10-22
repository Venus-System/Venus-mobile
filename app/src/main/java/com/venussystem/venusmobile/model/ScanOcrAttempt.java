package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

/** Safe metadata about one local OCR pass; never contains the recognized text. */
public final class ScanOcrAttempt implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String variant;
    private final int characterCount;
    private final int lineCount;
    private final double score;
    private final boolean successful;
    private final boolean selected;
    private final String errorCode;

    public ScanOcrAttempt(
            @NonNull String variant,
            int characterCount,
            int lineCount,
            double score,
            boolean successful,
            boolean selected,
            @Nullable String errorCode
    ) {
        this.variant = variant;
        this.characterCount = Math.max(0, characterCount);
        this.lineCount = Math.max(0, lineCount);
        this.score = Double.isFinite(score) ? Math.max(0d, Math.min(1d, score)) : 0d;
        this.successful = successful;
        this.selected = selected;
        this.errorCode = errorCode;
    }

    @NonNull public String getVariant() { return variant; }
    public int getCharacterCount() { return characterCount; }
    public int getLineCount() { return lineCount; }
    public double getScore() { return score; }
    public boolean isSuccessful() { return successful; }
    public boolean isSelected() { return selected; }
    @Nullable public String getErrorCode() { return errorCode; }

    /** Contrast keeps the source dimensions; crop/rotation do not. */
    public boolean preservesSourceGeometry() {
        return "ORIGINAL".equals(variant) || "CONTRAST".equals(variant);
    }

    @NonNull
    public ScanOcrAttempt asSelected(boolean value) {
        return new ScanOcrAttempt(
                variant,
                characterCount,
                lineCount,
                score,
                successful,
                value,
                errorCode
        );
    }
}
