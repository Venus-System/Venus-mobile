package com.venussystem.venusmobile.repository.api.dto;

/** Corpo do POST /api/classifications. Sem scoringModelId a API usa o modelo ativo. */
public class ClassificationRequest {
    public Long userId;
    public Long productVersionId;

    public ClassificationRequest(Long userId, Long productVersionId) {
        this.userId = userId;
        this.productVersionId = productVersionId;
    }
}
