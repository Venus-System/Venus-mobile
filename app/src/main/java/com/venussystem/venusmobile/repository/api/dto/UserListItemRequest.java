package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/user-list-items. Os tres campos sao obrigatorios, e a
 * posicao nao pode repetir dentro da mesma lista.
 */
public class UserListItemRequest {
    public Long userListId;
    public Long productId;
    public Integer positionOrder;
}
