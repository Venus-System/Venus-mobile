package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

public class ScanProductCandidate {

    private final Produto produto;
    private final double score;
    private final String evidencias;

    public ScanProductCandidate(
            @NonNull Produto produto,
            double score,
            @NonNull String evidencias
    ) {
        this.produto = produto;
        this.score = score;
        this.evidencias = evidencias;
    }

    @NonNull
    public Produto getProduto() {
        return produto;
    }

    public double getScore() {
        return score;
    }

    @NonNull
    public String getEvidencias() {
        return evidencias;
    }
}