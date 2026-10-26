package com.venussystem.venusmobile.repository.api.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resposta da Venus-Classificacao. So os campos que o app usa; o resto (pontos,
 * pesos, tempo de calculo) a API manda e o Gson ignora.
 */
public class ClassificationResponse {

    public static final String MOTIVO_ALERGIA = "ALLERGY";

    public Long userId;
    public Long productVersionId;
    /** Nota final de 0 a 100 para o perfil, ja com as travas de bloqueio e alergia. */
    public Integer finalScore;
    /** IDEAL, RECOMMENDED, ACCEPTABLE, NOT_RECOMMENDED ou CONTRAINDICATED. */
    public String recommendationLevel;
    /** LOW, MEDIUM, HIGH ou CRITICAL. */
    public String riskLevel;
    public Double compatibilityPercentage;
    /** As partes da nota. Os pontos vem no POST e chegam nulos no GET. */
    public Breakdown breakdown;
    public List<Reason> reasons;
    public String summary;
    /** ISO-8601 com fuso, ex.: 2026-10-22T10:58:03.096Z. */
    public String calculatedAt;

    public static class Breakdown {
        /** Nota de qualidade do produto, de 0 a 100, sem olhar o perfil. */
        public Integer qualityScore;
        /** Ate 35 pontos da nota final. */
        public Double qualityPoints;
        /** Ate 65 pontos da nota final; nulo quando nenhuma pergunta do perfil entrou. */
        public Double profilePoints;
    }

    public static class Reason {
        /** INGREDIENT_RULE, ALLERGY, PREFERENCE ou BASE_SCORE. */
        public String source;
        public String text;
        public Boolean warning;
        public String ingredientName;
        /** So no ALLERGY: ingredientes do produto que estao na alergia da pessoa. Null nos outros. */
        public List<String> ingredientNames;
        public String profileTagSlug;
    }

    /**
     * Ingredientes dos motivos de alergia, sem repetir. Vazio quando nao ha
     * alergia, no GET (que nao traz esse motivo) e na API anterior ao campo.
     */
    public List<String> ingredientesDeAlergia() {
        Set<String> nomes = new LinkedHashSet<>();
        if (reasons == null) return new ArrayList<>();
        for (Reason r : reasons) {
            if (r == null || !MOTIVO_ALERGIA.equals(r.source) || r.ingredientNames == null) continue;
            for (String nome : r.ingredientNames) {
                if (nome != null && !nome.trim().isEmpty()) nomes.add(nome.trim());
            }
        }
        return new ArrayList<>(nomes);
    }
}
