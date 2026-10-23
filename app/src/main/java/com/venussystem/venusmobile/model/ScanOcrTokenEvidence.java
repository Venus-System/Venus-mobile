package com.venussystem.venusmobile.model;

import android.graphics.Rect;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * Serializable, immutable snapshot of one OCR token.  The Android ML Kit
 * token itself contains an Android {@link Rect}, so it cannot safely cross an
 * Activity/process boundary or be persisted with the draft.
 */
public final class ScanOcrTokenEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String text;
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;
    public final float confidence;
    public final float rotation;
    public final int blockIndex;
    public final int lineIndex;
    public final int elementIndex;

    public ScanOcrTokenEvidence(@NonNull String text, Rect box, float confidence,
                                float rotation, int blockIndex, int lineIndex,
                                int elementIndex) {
        this.text = text;
        this.left = box == null ? 0 : box.left;
        this.top = box == null ? 0 : box.top;
        this.right = box == null ? 0 : box.right;
        this.bottom = box == null ? 0 : box.bottom;
        this.confidence = confidence;
        this.rotation = rotation;
        this.blockIndex = blockIndex;
        this.lineIndex = lineIndex;
        this.elementIndex = elementIndex;
    }

    public static ScanOcrTokenEvidence from(ScanOcrToken token) {
        if (token == null) return null;
        return new ScanOcrTokenEvidence(token.getText(), token.getBoundingBox(),
                token.getConfidence(), token.getRotation(), token.getBlockIndex(),
                token.getLineIndex(), token.getElementIndex());
    }
}
