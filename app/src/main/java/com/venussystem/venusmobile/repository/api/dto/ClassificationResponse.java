package com.venussystem.venusmobile.repository.api.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ClassificationResponse {

    public static final String MOTIVO_ALERGIA = "ALLERGY";

    public Long userId;
    public Long productVersionId;
    public Integer finalScore;
    public String recommendationLevel;
    public String riskLevel;
    public Double compatibilityPercentage;
    public Breakdown breakdown;
    public List<Reason> reasons;
    public String summary;
    public String calculatedAt;

    public static class Breakdown {
        public Integer qualityScore;
        public Double qualityPoints;
        public Double profilePoints;
    }

    public static class Reason {
        public String source;
        public String text;
        public Boolean warning;
        public String ingredientName;
        public List<String> ingredientNames;
        public String profileTagSlug;
    }

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
