package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/favorites: a pessoa curtiu o produto. Os dois campos sao
 * obrigatorios, e curtir de novo o mesmo produto da 409.
 */
public class FavoriteRequest {
    public Long userId;
    public Long productId;

    public FavoriteRequest(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }
}
