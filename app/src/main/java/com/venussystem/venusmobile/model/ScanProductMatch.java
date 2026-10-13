package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Resultado da tentativa de identificar um produto
 * a partir dos dados extraídos da frente da embalagem.
 */
public class ScanProductMatch {

    private final boolean found;
    private final Produto produto;
    private final double score;
    private final String matchedBrand;
    private final String matchedProduct;
    private final String errorMessage;

    private ScanProductMatch(
            boolean found,
            @Nullable Produto produto,
            double score,
            @Nullable String matchedBrand,
            @Nullable String matchedProduct,
            @Nullable String errorMessage
    ) {
        this.found = found;
        this.produto = produto;
        this.score = score;
        this.matchedBrand = matchedBrand;
        this.matchedProduct = matchedProduct;
        this.errorMessage = errorMessage;
    }

    /**
     * Cria resultado com produto identificado.
     */
    @NonNull
    public static ScanProductMatch encontrado(
            @NonNull Produto produto,
            double score,
            @Nullable String matchedBrand,
            @Nullable String matchedProduct
    ) {
        return new ScanProductMatch(
                true,
                produto,
                score,
                matchedBrand,
                matchedProduct,
                null
        );
    }

    /**
     * Cria resultado quando nenhum produto foi identificado.
     */
    @NonNull
    public static ScanProductMatch semMatch() {
        return new ScanProductMatch(
                false,
                null,
                0.0,
                null,
                null,
                null
        );
    }

    /**
     * Cria resultado quando ocorreu erro durante o processo.
     */
    @NonNull
    public static ScanProductMatch comErro(
            @Nullable String mensagem
    ) {
        return new ScanProductMatch(
                false,
                null,
                0.0,
                null,
                null,
                mensagem
        );
    }

    public boolean isFound() {
        return found;
    }

    @Nullable
    public Produto getProduto() {
        return produto;
    }

    public double getScore() {
        return score;
    }

    @Nullable
    public String getMatchedBrand() {
        return matchedBrand;
    }

    @Nullable
    public String getMatchedProduct() {
        return matchedProduct;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null
                && !errorMessage.trim().isEmpty();
    }
}