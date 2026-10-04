package com.venussystem.venusmobile.repository.api.dto;

/** Vinculo entre uma versao de produto e um ingrediente da composicao. */
public class ProductIngredientResponse {
    public Long id;
    public Long productVersionId;
    public Long ingredientId;
    public Integer position;
}
