package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;

import com.venussystem.venusmobile.model.ScanFrontData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta exclusivamente o OCR da FRENTE.
 *
 * A responsabilidade desta classe NÃO é identificar definitivamente
 * o produto.
 *
 * Ela deve produzir dados candidatos para uma etapa posterior
 * de validação com o catálogo real.
 *
 * FRONT:
 *
 * - marca candidata
 * - nome/produto candidato
 * - apresentação
 * - capacidade
 * - concentração
 *
 * BACK:
 * NÃO é tratado aqui.
 */
public final class ScanFrontExtractor {

    // ============================================================
    // CAPACIDADE
    // ============================================================

    private static final Pattern CAPACITY_PATTERN =
            Pattern.compile(
                    "(?<![A-Z0-9])"
                            + "(\\d+(?:[.,]\\d+)?)"
                            + "\\s*"
                            + "(ML|L|G|KG|MG|OZ)"
                            + "\\b",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Casos de OCR como:
     *
     * 30 9
     * 50 9
     *
     * Só usamos essa correção quando:
     *
     * - existe contexto PESO;
     * - ou a linha é claramente uma medida.
     */
    private static final Pattern OCR_WEIGHT_PATTERN =
            Pattern.compile(
                    "\\b(?:PESO\\s*)?"
                            + "(\\d+(?:[.,]\\d+)?)"
                            + "\\s*[G9]\\b",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Casos como:
     *
     * 5Og
     * 3Og
     *
     * em que O foi reconhecido no lugar de 0.
     */
    private static final Pattern OCR_ZERO_PATTERN =
            Pattern.compile(
                    "\\b(\\d+[O])\\s*(G|ML|L|KG|MG)\\b",
                    Pattern.CASE_INSENSITIVE
            );

    // ============================================================
    // CONCENTRAÇÃO
    // ============================================================

    private static final Pattern CONCENTRATION_PATTERN =
            Pattern.compile(
                    "(?<![A-Z0-9])"
                            + "(\\d+(?:[.,]\\d+)?)"
                            + "\\s*"
                            + "(MG/G|MG/ML|G/L|%)"
                            + "\\b",
                    Pattern.CASE_INSENSITIVE
            );

    // ============================================================
    // APRESENTAÇÃO
    // ============================================================

    private static final Set<String> PRESENTATIONS =
            createSet(
                    "GEL",
                    "CREME",
                    "CREMA",
                    "SPRAY",
                    "AEROSOL",
                    "SERUM",
                    "SÉRUM",
                    "LOCAO",
                    "LOÇÃO",
                    "SOLUCAO",
                    "SOLUÇÃO",
                    "POMADA",
                    "ESPUMA",
                    "BALM",
                    "STICK",
                    "OLEO",
                    "ÓLEO",
                    "LIQUIDO",
                    "LÍQUIDO",
                    "SHAMPOO",
                    "CONDICIONADOR",
                    "SABONETE",
                    "DESODORANTE",
                    "ANTITRANSPIRANTE",
                    "PROTETOR"
            );

    // ============================================================
    // CONTEXTO NÃO COMERCIAL
    // ============================================================

    private static final Set<String> EXCLUSION_WORDS =
            createSet(
                    "APLICAR",
                    "APLICAÇÃO",
                    "APLICACAO",
                    "USO",
                    "ADULTO",
                    "VIA",
                    "DERMATOLOGICA",
                    "DERMATOLÓGICA",
                    "PESO",
                    "ARMAZENAR",
                    "ARMAZENAMENTO",
                    "INDICAÇÃO",
                    "INDICACAO",
                    "INDICAÇÕES",
                    "INDICACOES",
                    "CONTRAINDICAÇÃO",
                    "CONTRAINDICACAO",
                    "CONTRAINDICAÇÕES",
                    "CONTRAINDICACOES",
                    "PRECAUÇÃO",
                    "PRECAUCAO",
                    "PRECAUÇÕES",
                    "PRECAUCOES",
                    "MANTER",
                    "EVITAR",
                    "CONSULTE",
                    "RECOMENDADO",
                    "RECOMENDADA",
                    "TEMPERATURA",
                    "GRAVIDEZ",
                    "CRIANÇAS",
                    "CRIANCAS",
                    "SENSÍVEIS",
                    "SENSIVEIS",
                    "PROFISSIONAL",
                    "MÉDICO",
                    "MEDICO",
                    "APROVADO",
                    "TESTADO"
            );

    // ============================================================
    // HEADERS
    // ============================================================

    private static final Set<String> SECTION_WORDS =
            createSet(
                    "INGREDIENTES",
                    "INGREDIENTS",
                    "COMPOSICAO",
                    "COMPOSIÇÃO",
                    "PRECAUCOES",
                    "PRECAUÇÕES",
                    "CONTRAINDICACAO",
                    "CONTRAINDICAÇÃO",
                    "INDICACOES",
                    "INDICAÇÕES"
            );

    // ============================================================
    // MARKETING
    // ============================================================

    private static final Set<String> MARKETING_WORDS =
            createSet(
                    "TECNOLOGIA",
                    "PROTECAO",
                    "PROTEÇÃO",
                    "PERFUMACAO",
                    "PERFUMAÇÃO",
                    "RESULTADO",
                    "COMPROVADO",
                    "NAO MANCHA",
                    "NÃO MANCHA",
                    "NAO DEIXA",
                    "NÃO DEIXA",
                    "NOVA TECNOLOGIA",
                    "DERMATOLOGICAMENTE"
            );

    private ScanFrontExtractor() {
    }

    // ============================================================
    // ENTRY POINT
    // ============================================================

    @NonNull
    public static ScanFrontData extract(
            @NonNull List<String> lines
    ) {

        List<String> normalized =
                normalizeLines(lines);

        String capacity =
                extractCapacity(normalized);

        String concentration =
                extractConcentration(normalized);

        List<String> presentations =
                extractPresentations(normalized);

        List<Candidate> commercial =
                extractCommercialCandidates(
                        normalized,
                        capacity,
                        concentration
                );

        List<String> brandCandidates =
                extractBrandCandidates(
                        commercial
                );

        List<String> productCandidates =
                extractProductCandidates(
                        commercial
                );

        return new ScanFrontData(
                brandCandidates,
                productCandidates,
                presentations,
                capacity,
                concentration
        );
    }

    // ============================================================
    // NORMALIZAÇÃO
    // ============================================================

    @NonNull
    private static List<String> normalizeLines(
            @NonNull List<String> lines
    ) {

        List<String> result =
                new ArrayList<>();

        for (String original : lines) {

            if (original == null) {
                continue;
            }

            String line =
                    ScanTextNormalizer.normalize(
                            original
                    );

            line =
                    line
                            .replaceAll(
                                    "\\s+",
                                    " "
                            )
                            .trim();

            if (line.length() < 2) {
                continue;
            }

            /*
             * Corrige apenas um erro claro de pontuação
             * em extremidades.
             *
             * Ex:
             *
             * AZELAN'
             * AZELAN,
             */
            line =
                    line.replaceAll(
                            "^[^A-Z0-9ÁÉÍÓÚÀÃÕÇ]+",
                            ""
                    );

            line =
                    line.replaceAll(
                            "[^A-Z0-9ÁÉÍÓÚÀÃÕÇ]+$",
                            ""
                    );

            if (line.length() >= 2) {
                result.add(line);
            }
        }

        return result;
    }

    // ============================================================
    // CAPACIDADE
    // ============================================================

    @NonNull
    private static String extractCapacity(
            @NonNull List<String> lines
    ) {

        /*
         * 1) Formato normal:
         *
         * 30 G
         * 200 ML
         * 1 L
         */
        for (String line : lines) {

            if (isConcentrationLine(line)) {
                continue;
            }

            Matcher matcher =
                    CAPACITY_PATTERN.matcher(
                            line
                    );

            if (!matcher.find()) {
                continue;
            }

            String value =
                    matcher.group(1);

            String unit =
                    matcher.group(2);

            if (value == null
                    || unit == null) {
                continue;
            }

            return formatMeasurement(
                    value,
                    unit
            );
        }

        /*
         * 2) OCR:
         *
         * 30 9
         *
         * Só permitimos se existir PESO.
         */
        for (String line : lines) {

            if (!containsWholeWord(
                    line,
                    "PESO"
            )) {
                continue;
            }

            Matcher matcher =
                    OCR_WEIGHT_PATTERN.matcher(
                            line
                    );

            if (!matcher.find()) {
                continue;
            }

            String value =
                    matcher.group(1);

            if (value != null) {
                return value + " G";
            }
        }

        /*
         * 3) OCR:
         *
         * PESO
         * 30 9
         */
        for (int i = 0;
             i < lines.size() - 1;
             i++) {

            if (!containsWholeWord(
                    lines.get(i),
                    "PESO"
            )) {
                continue;
            }

            Matcher matcher =
                    OCR_WEIGHT_PATTERN.matcher(
                            lines.get(i + 1)
                    );

            if (matcher.find()) {

                String value =
                        matcher.group(1);

                if (value != null) {
                    return value + " G";
                }
            }
        }

        /*
         * 4) OCR:
         *
         * 5Og
         *
         * Só tratamos como capacidade se a linha tiver
         * estrutura claramente numérica.
         */
        for (String line : lines) {

            Matcher matcher =
                    OCR_ZERO_PATTERN.matcher(
                            line
                    );

            if (!matcher.find()) {
                continue;
            }

            String value =
                    matcher.group(1);

            String unit =
                    matcher.group(2);

            if (value != null
                    && unit != null) {

                value =
                        value.replace(
                                "O",
                                "0"
                        );

                return formatMeasurement(
                        value,
                        unit
                );
            }
        }

        return "";
    }

    // ============================================================
    // CONCENTRAÇÃO
    // ============================================================

    @NonNull
    private static String extractConcentration(
            @NonNull List<String> lines
    ) {

        for (String line : lines) {

            Matcher matcher =
                    CONCENTRATION_PATTERN.matcher(
                            line
                    );

            if (!matcher.find()) {
                continue;
            }

            String value =
                    matcher.group(1);

            String unit =
                    matcher.group(2);

            if (value == null
                    || unit == null) {
                continue;
            }

            return formatMeasurement(
                    value,
                    unit
            );
        }

        return "";
    }

    // ============================================================
    // APRESENTAÇÕES
    // ============================================================

    @NonNull
    private static List<String> extractPresentations(
            @NonNull List<String> lines
    ) {

        Map<String, Integer> found =
                new LinkedHashMap<>();

        for (String line : lines) {

            String upper =
                    line.toUpperCase(
                            Locale.ROOT
                    );

            for (
                    String presentation
                    : PRESENTATIONS
            ) {

                if (containsWholeWord(
                        upper,
                        presentation
                )) {

                    found.put(
                            presentation,
                            found.getOrDefault(
                                    presentation,
                                    0
                            ) + 1
                    );
                }
            }
        }

        return new ArrayList<>(
                found.keySet()
        );
    }

    // ============================================================
    // CANDIDATOS COMERCIAIS
    // ============================================================

    @NonNull
    private static List<Candidate> extractCommercialCandidates(
            @NonNull List<String> lines,
            @NonNull String capacity,
            @NonNull String concentration
    ) {

        Map<String, Candidate> candidates =
                new LinkedHashMap<>();

        /*
         * Primeiro: linhas individuais.
         */
        for (int index = 0;
             index < lines.size();
             index++) {

            String line =
                    lines.get(index);

            if (!isCommercialCandidate(
                    line,
                    capacity,
                    concentration
            )) {
                continue;
            }

            double score =
                    calculateCommercialScore(
                            line,
                            index,
                            lines.size()
                    );

            addCandidate(
                    candidates,
                    line,
                    score
            );
        }

        /*
         * Segundo: juntar somente duas linhas quando
         * isso for linguisticamente plausível.
         *
         * Ex:
         *
         * ACIDO
         * AZELAICO
         *
         * -> ACIDO AZELAICO
         *
         * Não fazemos:
         *
         * CIRATA + AZELAN
         */
        for (int i = 0;
             i < lines.size() - 1;
             i++) {

            String first =
                    lines.get(i);

            String second =
                    lines.get(i + 1);

            if (!canCompose(
                    first,
                    second,
                    capacity,
                    concentration
            )) {
                continue;
            }

            String combined =
                    first + " " + second;

            double score =
                    calculateCommercialScore(
                            combined,
                            i,
                            lines.size()
                    ) + 4.0;

            addCandidate(
                    candidates,
                    combined,
                    score
            );
        }

        List<Candidate> result =
                new ArrayList<>(
                        candidates.values()
                );

        result.sort(
                Comparator
                        .comparingDouble(
                                Candidate::getScore
                        )
                        .reversed()
        );

        /*
         * Mantemos uma quantidade um pouco maior nesta fase.
         * O banco será responsável por validar.
         */
        if (result.size() > 10) {

            return new ArrayList<>(
                    result.subList(
                            0,
                            10
                    )
            );
        }

        return result;
    }

    // ============================================================
    // CLASSIFICAÇÃO
    // ============================================================

    private static boolean isCommercialCandidate(
            @NonNull String line,
            @NonNull String capacity,
            @NonNull String concentration
    ) {

        String text =
                line.toUpperCase(
                        Locale.ROOT
                );

        if (text.length() < 3) {
            return false;
        }

        if (text.length() > 50) {
            return false;
        }

        if (containsCapacity(
                text,
                capacity
        )) {
            return false;
        }

        if (containsConcentration(
                text,
                concentration
        )) {
            return false;
        }

        if (isOnlyNumberOrSymbol(
                text
        )) {
            return false;
        }

        if (isContact(
                text
        )) {
            return false;
        }

        if (isSection(
                text
        )) {
            return false;
        }

        if (isInstruction(
                text
        )) {
            return false;
        }

        if (isOnlyPresentation(
                text
        )) {
            return false;
        }

        return hasLetters(
                text
        );
    }

    /**
     * Somente permite junção controlada.
     */
    private static boolean canCompose(
            @NonNull String first,
            @NonNull String second,
            @NonNull String capacity,
            @NonNull String concentration
    ) {

        if (!isCommercialCandidate(
                first,
                capacity,
                concentration
        )) {
            return false;
        }

        if (!isCommercialCandidate(
                second,
                capacity,
                concentration
        )) {
            return false;
        }

        if (!isCompoundPrefix(
                first
        )) {
            return false;
        }

        /*
         * O segundo termo precisa ser uma palavra.
         */
        if (second.contains(" ")) {
            return false;
        }

        if (second.length() > 20) {
            return false;
        }

        /*
         * Evita juntar com outro candidato muito forte.
         */
        if (looksLikeStandaloneBrand(
                second
        )) {
            return false;
        }

        return true;
    }

    // ============================================================
    // MARCA
    // ============================================================

    /**
     * Retorna somente candidatos que possuem características
     * compatíveis com marca.
     *
     * IMPORTANTE:
     * ainda não afirma que são marcas.
     */
    @NonNull
    private static List<String> extractBrandCandidates(
            @NonNull List<Candidate> commercial
    ) {

        List<String> result =
                new ArrayList<>();

        for (Candidate candidate
                : commercial) {

            String text =
                    candidate.text;

            /*
             * Marca costuma ser:
             *
             * - curta;
             * - uma ou duas palavras;
             * - sem medida;
             * - sem instrução.
             */
            if (text.length() > 25) {
                continue;
            }

            int words =
                    countWords(text);

            if (words > 2) {
                continue;
            }

            /*
             * Não considerar apresentação.
             */
            if (isOnlyPresentation(
                    text
            )) {
                continue;
            }

            /*
             * Não considerar expressões muito descritivas.
             */
            if (looksLikeProductDescription(
                    text
            )) {
                continue;
            }

            result.add(
                    text
            );

            if (result.size() >= 5) {
                break;
            }
        }

        return removeDuplicates(
                result
        );
    }

    // ============================================================
    // PRODUTO
    // ============================================================

    @NonNull
    private static List<String> extractProductCandidates(
            @NonNull List<Candidate> commercial
    ) {

        List<String> result =
                new ArrayList<>();

        for (Candidate candidate
                : commercial) {

            String text =
                    candidate.text;

            /*
             * Nome de produto pode ser:
             *
             * uma palavra
             * duas palavras
             * várias palavras
             */
            if (text.length() < 3
                    || text.length() > 50) {
                continue;
            }

            if (isOnlyPresentation(
                    text
            )) {
                continue;
            }

            result.add(
                    text
            );

            if (result.size() >= 8) {
                break;
            }
        }

        return removeDuplicates(
                result
        );
    }

    // ============================================================
    // SCORE
    // ============================================================

    private static double calculateCommercialScore(
            @NonNull String text,
            int position,
            int total
    ) {

        double score = 0.0;

        int words =
                countWords(text);

        /*
         * Tamanho.
         */
        if (text.length() <= 12) {

            score += 4.0;

        } else if (text.length() <= 25) {

            score += 3.0;

        } else if (text.length() <= 40) {

            score += 1.0;

        } else {

            score -= 2.0;
        }

        /*
         * 1–5 palavras é plausível para nome.
         */
        if (words >= 1
                && words <= 5) {

            score += 1.5;
        }

        /*
         * Palavras únicas podem ser marcas.
         */
        if (words == 1) {
            score += 1.5;
        }

        /*
         * Marketing recebe penalização.
         */
        if (containsMarketing(
                text
        )) {

            score -= 4.0;
        }

        /*
         * Descrição longa.
         */
        if (words >= 6) {
            score -= 3.0;
        }

        /*
         * Pequeno bônus para região inicial.
         *
         * É apenas um sinal, nunca uma decisão.
         */
        if (position <
                Math.max(
                        3,
                        total / 3
                )) {

            score += 0.5;
        }

        return score;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static boolean looksLikeStandaloneBrand(
            @NonNull String text
    ) {

        return !text.contains(" ")
                && text.length() <= 12
                && !isOnlyPresentation(text);
    }

    private static boolean looksLikeProductDescription(
            @NonNull String text
    ) {

        String upper =
                text.toUpperCase(
                        Locale.ROOT
                );

        return upper.contains("ACIDO ")
                && upper.contains("GEL")
                || upper.contains("USO ")
                || upper.contains("VIA ")
                || upper.contains("ADULTO");
    }

    private static boolean isInstruction(
            @NonNull String text
    ) {

        String upper =
                text.toUpperCase(
                        Locale.ROOT
                );

        for (
                String word
                : EXCLUSION_WORDS
        ) {

            if (containsWholeWord(
                    upper,
                    word
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean isSection(
            @NonNull String text
    ) {

        String upper =
                text.toUpperCase(
                        Locale.ROOT
                );

        for (
                String word
                : SECTION_WORDS
        ) {

            if (containsWholeWord(
                    upper,
                    word
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean containsMarketing(
            @NonNull String text
    ) {

        String upper =
                text.toUpperCase(
                        Locale.ROOT
                );

        for (
                String word
                : MARKETING_WORDS
        ) {

            if (containsWholeWord(
                    upper,
                    word
            ) || upper.contains(
                    word
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean isContact(
            @NonNull String text
    ) {

        if (text.contains("@")) {
            return true;
        }

        if (text.contains("WWW.")) {
            return true;
        }

        if (text.contains("HTTP")) {
            return true;
        }

        Matcher phone =
                Pattern.compile(
                        "\\b\\d{4,5}[- ]\\d{4}\\b"
                ).matcher(
                        text
                );

        return phone.find();
    }

    private static boolean isOnlyPresentation(
            @NonNull String text
    ) {

        return PRESENTATIONS.contains(
                text.toUpperCase(
                        Locale.ROOT
                )
        );
    }

    private static boolean hasLetters(
            @NonNull String text
    ) {

        return text.matches(
                ".*[A-ZÁÉÍÓÚÀÃÕÇ].*"
        );
    }

    private static boolean isOnlyNumberOrSymbol(
            @NonNull String text
    ) {

        return !hasLetters(
                text
        );
    }

    private static boolean isConcentrationLine(
            @NonNull String line
    ) {

        return CONCENTRATION_PATTERN
                .matcher(
                        line
                )
                .find();
    }

    private static boolean containsCapacity(
            @NonNull String line,
            @NonNull String capacity
    ) {

        if (capacity.isEmpty()) {
            return false;
        }

        return compact(
                line
        ).contains(
                compact(
                        capacity
                )
        );
    }

    private static boolean containsConcentration(
            @NonNull String line,
            @NonNull String concentration
    ) {

        if (concentration.isEmpty()) {
            return false;
        }

        return compact(
                line
        ).contains(
                compact(
                        concentration
                )
        );
    }

    private static String compact(
            @NonNull String text
    ) {

        return text
                .replace(
                        " ",
                        ""
                )
                .replace(
                        ",",
                        "."
                )
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private static String formatMeasurement(
            @NonNull String value,
            @NonNull String unit
    ) {

        return value
                + " "
                + unit.toUpperCase(
                Locale.ROOT
        );
    }

    private static int countWords(
            @NonNull String text
    ) {

        return text
                .trim()
                .split(
                        "\\s+"
                )
                .length;
    }

    private static boolean isCompoundPrefix(
            @NonNull String text
    ) {

        String normalized =
                text.toUpperCase(
                        Locale.ROOT
                );

        return COMPOUND_PREFIXES.contains(
                normalized
        );
    }

    private static boolean containsWholeWord(
            @NonNull String text,
            @NonNull String word
    ) {

        String normalizedText =
                text.toUpperCase(
                        Locale.ROOT
                );

        String normalizedWord =
                word.toUpperCase(
                        Locale.ROOT
                );

        return Pattern
                .compile(
                        "(^|\\s)"
                                + Pattern.quote(
                                normalizedWord
                        )
                                + "($|\\s|[.,:;!?])"
                )
                .matcher(
                        normalizedText
                )
                .find();
    }

    private static void addCandidate(
            @NonNull Map<String, Candidate> candidates,
            @NonNull String text,
            double score
    ) {

        String normalized =
                ScanTextNormalizer.normalize(
                        text
                );

        Candidate current =
                candidates.get(
                        normalized
                );

        if (current == null
                || score > current.score) {

            candidates.put(
                    normalized,
                    new Candidate(
                            normalized,
                            score
                    )
            );
        }
    }

    @NonNull
    private static List<String> removeDuplicates(
            @NonNull List<String> input
    ) {

        List<String> result =
                new ArrayList<>();

        Set<String> seen =
                new HashSet<>();

        for (String value : input) {

            String normalized =
                    ScanTextNormalizer.normalize(
                            value
                    );

            if (seen.add(
                    normalized
            )) {

                result.add(
                        normalized
                );
            }
        }

        return result;
    }

    @NonNull
    private static Set<String> createSet(
            @NonNull String... values
    ) {

        Set<String> result =
                new HashSet<>();

        for (String value : values) {

            result.add(
                    value.toUpperCase(
                            Locale.ROOT
                    )
            );
        }

        return result;
    }

    private static final Set<String> COMPOUND_PREFIXES =
            createSet(
                    "ACIDO",
                    "ÁCIDO",
                    "ANTI",
                    "HIDRATANTE",
                    "PROTETOR",
                    "DESODORANTE",
                    "CREME",
                    "SABONETE",
                    "SHAMPOO",
                    "CONDICIONADOR",
                    "OLEO",
                    "ÓLEO",
                    "SERUM",
                    "SÉRUM"
            );

    private static class Candidate {

        private final String text;
        private final double score;

        Candidate(
                @NonNull String text,
                double score
        ) {

            this.text = text;
            this.score = score;
        }

        double getScore() {
            return score;
        }
    }
}