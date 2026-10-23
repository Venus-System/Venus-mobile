package com.venussystem.venusmobile.repository.api.dto;

/**
 * Efeito de um ingrediente sobre quem tem uma etiqueta de perfil
 * (GET /api/ingredient-effects/ingredient/{ingredientId}).
 * effectCategory: BENEFIT, WARNING, CONTRAINDICATION ou NEUTRAL.
 */
public class IngredientEffectResponse {
    public Long id;
    public Long ingredientId;
    public Long profileTagId;
    public String effectCategory;
    public String effectName;
    public String effectDescription;
    public String reviewStatus;
}
