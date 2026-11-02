package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ScanProductMatch {
    public enum ResolutionStatus {
        RESOLVED,
        NOT_FOUND,
        AMBIGUOUS,
        ERROR
    }

    private final boolean found;
    private final Produto produto;
    private final double score;
    private final String matchedBrand;
    private final String matchedProduct;
    private final String errorMessage;
    private final ResolutionStatus resolutionStatus;
    private final boolean needsReview;
    private final double scoreGap;

    private ScanProductMatch(
            boolean found,
            @Nullable Produto produto,
            double score,
            @Nullable String matchedBrand,
            @Nullable String matchedProduct,
            @Nullable String errorMessage,
            @NonNull ResolutionStatus resolutionStatus,
            boolean needsReview,
            double scoreGap
    ) {
        this.found = found;
        this.produto = produto;
        this.score = score;
        this.matchedBrand = matchedBrand;
        this.matchedProduct = matchedProduct;
        this.errorMessage = errorMessage;
        this.resolutionStatus = resolutionStatus;
        this.needsReview = needsReview;
        this.scoreGap = scoreGap;
    }

    @NonNull
    public static ScanProductMatch encontrado(
            @NonNull Produto produto,
            double score,
            @Nullable String matchedBrand,
            @Nullable String matchedProduct
    ) {
        return encontrado(produto, score, matchedBrand, matchedProduct, 1.0);
    }

    @NonNull
    public static ScanProductMatch encontrado(
            @NonNull Produto produto,
            double score,
            @Nullable String matchedBrand,
            @Nullable String matchedProduct,
            double scoreGap
    ) {
        return new ScanProductMatch(
                true,
                produto,
                score,
                matchedBrand,
                matchedProduct,
                null,
                ResolutionStatus.RESOLVED,
                false,
                Math.max(0.0, scoreGap)
        );
    }

    @NonNull
    public static ScanProductMatch semMatch() {
        return new ScanProductMatch(
                false,
                null,
                0.0,
                null,
                null,
                null,
                ResolutionStatus.NOT_FOUND,
                true,
                0.0
        );
    }

    @NonNull
    public static ScanProductMatch ambiguo(
            double score,
            double scoreGap,
            @Nullable String matchedBrand,
            @Nullable String matchedProduct
    ) {
        return new ScanProductMatch(
                false,
                null,
                score,
                matchedBrand,
                matchedProduct,
                null,
                ResolutionStatus.AMBIGUOUS,
                true,
                Math.max(0.0, scoreGap)
        );
    }

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
                mensagem,
                ResolutionStatus.ERROR,
                true,
                0.0
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

    @NonNull
    public ResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public boolean needsReview() {
        return needsReview;
    }

    public double getScoreGap() {
        return scoreGap;
    }
}
