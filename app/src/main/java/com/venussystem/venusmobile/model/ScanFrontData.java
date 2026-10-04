package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado da interpretação do OCR da FRENTE.
 *
 * Os candidatos ainda não são uma identificação definitiva.
 * A confirmação será feita posteriormente pelo catálogo.
 */
public class ScanFrontData {

    private final List<String> brandCandidates;
    private final List<String> productCandidates;
    private final List<String> presentationCandidates;

    private final String capacity;
    private final String concentration;

    public ScanFrontData(
            @NonNull List<String> brandCandidates,
            @NonNull List<String> productCandidates,
            @NonNull List<String> presentationCandidates,
            @NonNull String capacity,
            @NonNull String concentration
    ) {
        this.brandCandidates =
                new ArrayList<>(brandCandidates);

        this.productCandidates =
                new ArrayList<>(productCandidates);

        this.presentationCandidates =
                new ArrayList<>(presentationCandidates);

        this.capacity = capacity;
        this.concentration = concentration;
    }

    @NonNull
    public List<String> getBrandCandidates() {
        return Collections.unmodifiableList(
                brandCandidates
        );
    }

    @NonNull
    public List<String> getProductCandidates() {
        return Collections.unmodifiableList(
                productCandidates
        );
    }

    @NonNull
    public List<String> getPresentationCandidates() {
        return Collections.unmodifiableList(
                presentationCandidates
        );
    }

    @NonNull
    public String getCapacity() {
        return capacity;
    }

    @NonNull
    public String getConcentration() {
        return concentration;
    }

    public boolean hasData() {
        return !brandCandidates.isEmpty()
                || !productCandidates.isEmpty()
                || !presentationCandidates.isEmpty()
                || !capacity.isEmpty()
                || !concentration.isEmpty();
    }
}