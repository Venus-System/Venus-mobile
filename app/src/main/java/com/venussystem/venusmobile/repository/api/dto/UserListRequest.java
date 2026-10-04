package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/user-lists. userId, name e listType sao obrigatorios;
 * description e coverKey so vao quando a lista tem (o Gson nao escreve campo
 * null). O PATCH usa o UserListPatchRequest.
 *
 * listType e um de SHOPPING, ROUTINE, FAVORITES ou CUSTOM. coverKey e a capa
 * padrao das listas de exemplo: FAVORITOS, ESCANEADOS ou SKINCARE, sempre em
 * maiusculo (minusculo da 400). description tem ate 500 caracteres.
 */
public class UserListRequest {
    public Long userId;
    public String name;
    public String listType;
    public String description;
    public String coverKey;
}
