package com.venussystem.venusmobile.repository.api.dto;

/**
 * Corpo do POST /api/user-profiles e do PUT /api/user-profiles/{userId}.
 *
 * Os Boolean de condicao ficam null quando a pessoa nao respondeu ou preferiu
 * nao dizer: o Gson nao escreve campo null, e a API entende ausente como "nao
 * informado", que e diferente de "nao tem".
 */
public class UserProfileRequest {
    public Long userId;
    public String skinType;
    public String skinPhototype;
    public String hairPattern;
    public String scalpType;
    public String skinSensitivity;
    public String ageRange;
    public String gender;
    public Boolean hasHyperpigmentation;
    public Boolean hasMelasma;
    public Boolean hasRosacea;
    public Boolean hasEczema;
    public Boolean acneProne;
    public Boolean isPregnant;
    public Boolean isBreastfeeding;
}
