package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.io.Serializable;

/** Ingrediente identificado no OCR do verso. */
public class ScanBackIngredientCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int position;
    private final String rawName;
    private final String normalizedName;
    private final String status;

    public ScanBackIngredientCandidate(
            int position,
            @NonNull String rawName,
            @NonNull String normalizedName,
            @NonNull String status
    ) {
        this.position = position;
        this.rawName = rawName;
        this.normalizedName = normalizedName;
        this.status = status;
    }

    public int getPosition() {
        return position;
    }

    @NonNull
    public String getRawName() {
        return rawName;
    }

    @NonNull
    public String getNormalizedName() {
        return normalizedName;
    }

    @NonNull
    public String getStatus() {
        return status;
    }
}
