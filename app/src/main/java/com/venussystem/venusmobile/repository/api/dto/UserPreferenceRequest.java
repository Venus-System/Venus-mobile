package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/user-preferences e do PUT /api/user-preferences/{userId}.
 * Todos os campos sao obrigatorios na API.
 */
public class UserPreferenceRequest {
    public Long userId;
    public Boolean preferCrueltyFree;
    public Boolean preferVegan;
    public Boolean preferSustainable;
    public Boolean preferFragranceFree;
    public Boolean preferParabenFree;
    public Boolean preferSulfateFree;
    public Boolean preferSiliconeFree;
}
