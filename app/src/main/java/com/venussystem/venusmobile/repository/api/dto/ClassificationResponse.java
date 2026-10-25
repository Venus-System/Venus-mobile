package com.venussystem.venusmobile.repository.api.dto;

import java.util.List;

/**
 * Resposta da Venus-Classificacao. So os campos que o app usa; o resto (pontos,
 * pesos, tempo de calculo) a API manda e o Gson ignora.
 */
public class ClassificationResponse {
    public Long userId;
    public Long productVersionId;
    /** Nota final de 0 a 100 para o perfil, ja com as travas de bloqueio e alergia. */
    public Integer finalScore;
    /** IDEAL, RECOMMENDED, ACCEPTABLE, NOT_RECOMMENDED ou CONTRAINDICATED. */
    public String recommendationLevel;
    /** LOW, MEDIUM, HIGH ou CRITICAL. */
    public String riskLevel;
    public Double compatibilityPercentage;
    public List<Reason> reasons;
    public String summary;
    /** ISO-8601 com fuso, ex.: 2026-10-22T10:58:03.096Z. */
    public String calculatedAt;

    public static class Reason {
        /** INGREDIENT_RULE, ALLERGY, PREFERENCE ou BASE_SCORE. */
        public String source;
        public String text;
        public Boolean warning;
        public String ingredientName;
        public String profileTagSlug;
    }
}
