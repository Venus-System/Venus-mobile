package com.venussystem.venusmobile.model;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/**
 * Token espacial do OCR.
 *
 * Representa um Element do ML Kit com sua posição na imagem.
 * O texto é sempre preservado como retornado pelo OCR.
 */
public final class ScanOcrToken {

    private final String text;
    private final Rect boundingBox;
    private final float confidence;
    private final float rotation;
    private final int blockIndex;
    private final int lineIndex;
    private final int elementIndex;

    public ScanOcrToken(
            @NonNull String text,
            Rect boundingBox,
            float confidence,
            float rotation,
            int blockIndex,
            int lineIndex,
            int elementIndex
    ) {
        this.text = text;
        this.boundingBox = boundingBox == null
                ? null
                : new Rect(boundingBox);
        this.confidence = confidence;
        this.rotation = rotation;
        this.blockIndex = blockIndex;
        this.lineIndex = lineIndex;
        this.elementIndex = elementIndex;
    }

    @NonNull
    public String getText() {
        return text;
    }

    public Rect getBoundingBox() {
        return boundingBox == null
                ? null
                : new Rect(boundingBox);
    }

    public float getConfidence() {
        return confidence;
    }

    public float getRotation() {
        return rotation;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public int getElementIndex() {
        return elementIndex;
    }

    public int getCenterX() {
        return boundingBox == null
                ? 0
                : boundingBox.centerX();
    }

    public int getCenterY() {
        return boundingBox == null
                ? 0
                : boundingBox.centerY();
    }

    public int getWidth() {
        return boundingBox == null
                ? 0
                : boundingBox.width();
    }

    public int getHeight() {
        return boundingBox == null
                ? 0
                : boundingBox.height();
    }

    @NonNull
    @Override
    public String toString() {
        return "ScanOcrToken{" +
                "text='" + text + '\'' +
                ", boundingBox=" + boundingBox +
                ", confidence=" + confidence +
                ", rotation=" + rotation +
                ", blockIndex=" + blockIndex +
                ", lineIndex=" + lineIndex +
                ", elementIndex=" + elementIndex +
                '}';
    }
}
