package com.venussystem.venusmobile.repository.api.dto;

/**
 * Item do catalogo GET /api/allergies. E dessa lista que saem as opcoes da
 * pergunta de alergias e o id que vai no POST /api/user-allergies.
 */
public class AllergyResponse {
    public Long id;
    public String allergyName;
}
