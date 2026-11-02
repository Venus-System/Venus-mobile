package com.venussystem.venusmobile.repository.api.dto;

public class ClassificationRequest {
    public Long userId;
    public Long productVersionId;

    public ClassificationRequest(Long userId, Long productVersionId) {
        this.userId = userId;
        this.productVersionId = productVersionId;
    }
}
