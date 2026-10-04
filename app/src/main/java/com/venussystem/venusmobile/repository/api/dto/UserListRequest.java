package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/user-lists (os tres campos obrigatorios) e do
 * PATCH /api/user-lists/{id}. No PATCH o app manda so o nome: o Gson nao
 * escreve campo null, e a API mantem o que nao veio.
 *
 * listType e um de SHOPPING, ROUTINE, FAVORITES ou CUSTOM.
 */
public class UserListRequest {
    public Long userId;
    public String name;
    public String listType;
}
