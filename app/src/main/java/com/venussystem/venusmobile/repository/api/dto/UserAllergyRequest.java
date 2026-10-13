package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/user-allergies. Os tres campos sao obrigatorios, e a
 * severidade e um de LOW, MEDIUM, HIGH ou CRITICAL.
 */
public class UserAllergyRequest {
    public Long userId;
    public Long allergyId;
    public String severity;
}
