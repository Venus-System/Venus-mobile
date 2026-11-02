package com.venussystem.venusmobile.repository.api.dto;

public class FavoriteRequest {
    public Long userId;
    public Long productId;

    public FavoriteRequest(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }
}
