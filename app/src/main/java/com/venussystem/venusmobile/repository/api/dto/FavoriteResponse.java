package com.venussystem.venusmobile.repository.api.dto;

/**
 * Produto curtido pela pessoa (GET /api/favorites/user/{userId}). O id cresce
 * com o tempo, entao serve para ordenar do mais recente para o mais antigo.
 */
public class FavoriteResponse {
    public Long id;
    public Long productId;
}
