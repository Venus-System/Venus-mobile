package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

public class ScanBackIngredientCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int position;
    private final String rawName;
    private final String normalizedName;
    private final String status;
    private final String issueCode;

    public ScanBackIngredientCandidate(
            int position,
            @NonNull String rawName,
            @NonNull String normalizedName,
            @NonNull String status
    ) {
        this(position, rawName, normalizedName, status,
                "UNRESOLVED".equals(status) ? null : status);
    }

    public ScanBackIngredientCandidate(
            int position,
            @NonNull String rawName,
            @NonNull String normalizedName,
            @NonNull String status,
            String issueCode
    ) {
        this.position = position;
        this.rawName = rawName;
        this.normalizedName = normalizedName;
        this.status = status;
        this.issueCode = issueCode;
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

    @Nullable
    public String getIssueCode() {
        return issueCode;
    }
}
