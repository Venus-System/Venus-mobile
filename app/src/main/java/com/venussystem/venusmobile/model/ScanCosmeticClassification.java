package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado da classificação conservadora da foto frontal.
 * O score é uma pontuação heurística interna, não uma probabilidade estatística.
 */
public class ScanCosmeticClassification implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        COSMETIC_CONFIRMED,
        NON_COSMETIC,
        UNCERTAIN
    }

    private final Status status;
    private final double confidenceScore;
    private final List<String> evidence;
    private final List<String> detectedProductTypes;

    public ScanCosmeticClassification(
            @NonNull Status status,
            double confidenceScore,
            @NonNull List<String> evidence,
            @NonNull List<String> detectedProductTypes
    ) {
        this.status = status;
        this.confidenceScore = confidenceScore;
        this.evidence = new ArrayList<>(evidence);
        this.detectedProductTypes = new ArrayList<>(detectedProductTypes);
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    @NonNull
    public List<String> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    @NonNull
    public List<String> getDetectedProductTypes() {
        return Collections.unmodifiableList(detectedProductTypes);
    }

    public boolean isConfirmedCosmetic() {
        return status == Status.COSMETIC_CONFIRMED;
    }

    public boolean isNonCosmetic() {
        return status == Status.NON_COSMETIC;
    }

    public boolean isUncertain() {
        return status == Status.UNCERTAIN;
    }
}
