package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/user-profile-tags: liga uma etiqueta do catalogo a pessoa.
 */
public class UserProfileTagRequest {
    public Long userId;
    public Long profileTagId;
}
