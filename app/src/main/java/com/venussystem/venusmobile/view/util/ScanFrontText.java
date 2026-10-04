package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.ANTIPERSPIRANTE;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.ANTITRANSPIRANTE;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CAPACIDADE_OCR_PREFIX_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CAPACIDADE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CONCENTRACAO_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.FL_OZ_PATTERN;

/** Internal collaborator for ScanFrontExtractor; not a second scan entry point. */
final class ScanFrontText {
    private ScanFrontText() { }

    private static final Pattern HORAS_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d{1,3})\\s*H\\b"
            );

    /*
     * =========================================================
     * REFERÊNCIA GENÉRICA DE COSMÉTICO
     * =========================================================
     */
    static boolean isReferenciaGenericaCosmetica(
            @NonNull String token
    ) {
        String valor =
                normalizar(
                        token
                );
        switch (valor) {
            case "SHAMPOO":
            case "CONDICIONADOR":
            case "CONDITIONER":
            case "HIDRATANTE":
            case "CLEANSER":
            case "TONICO":
            case "TONER":
            case "SERUM":
            case "ESFOLIANTE":
            case "PRIMER":
            case "PROTETOR":
            case "SOLAR":
            case "SUNSCREEN":
            case "DESODORANTE":
            case "DEODORANT":
            case "SABONETE":
            case "SOAP":
            case "PERFUME":
            case "PARFUM":
            case "FRAGRANCE":
            case "COLONIA":
            case "COLOGNE":
            case "BASE":
            case "FOUNDATION":
            case "CORRETIVO":
            case "CONCEALER":
            case "BATOM":
            case "LIPSTICK":
            case "GLOSS":
            case "BLUSH":
            case "BRONZER":
            case "ILUMINADOR":
            case "HIGHLIGHTER":
            case "DELINEADOR":
            case "EYELINER":
            case "SOMBRA":
            case "EYESHADOW":
            case "ESMALTE":
            case "REMOVEDOR":
            case "SPRAY":
            case "AEROSOL":
            case "CREME":
            case "CREAM":
            case "LOCAO":
            case "LOTION":
            case "GEL":
            case "ESPUMA":
            case "FOAM":
            case "OLEO":
            case "OIL":
                return true;
            default:
                return false;
        }
    }

    static boolean contemAntitranspirante(
            @NonNull String linha
    ) {
        String compacta =
                linha
                        .replaceAll(
                                "[^A-Z0-9]",
                                ""
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );
        if (compacta.contains(
                "ANTITRANSPIR"
        )) {
            return true;
        }
        return procurarTokenParecido(
                linha,
                ANTITRANSPIRANTE
        );
    }

    static boolean contemAntiperspirante(
            @NonNull String linha
    ) {
        String compacta =
                linha
                        .replaceAll(
                                "[^A-Z0-9]",
                                ""
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );
        if (compacta.contains(
                "ANTIPERSPIR"
        )) {
            return true;
        }
        return procurarTokenParecido(
                linha,
                ANTIPERSPIRANTE
        );
    }

    private static boolean procurarTokenParecido(
            @NonNull String linha,
            @NonNull String esperado
    ) {
        String[] tokens =
                linha.split(
                        "\\s+"
                );
        for (String token :
                tokens) {
            String normalizado =
                    normalizar(
                            token
                    );
            if (normalizado.length() < 8) {
                continue;
            }
            double score =
                    similaridade(
                            normalizado,
                            esperado
                    );
            if (score >= 0.75) {
                return true;
            }
        }
        return false;
    }

    static boolean isDescritorAntitranspirante(
            @NonNull String texto
    ) {
        String valor =
                normalizar(
                        texto
                );
        return valor.equals(
                ANTITRANSPIRANTE
        )
                || valor.equals(
                "ANTITRANSPIRANT"
        )
                || valor.equals(
                "ANTITRANSPIRAN"
        )
                || valor.equals(
                "ANTITRANSPIRANI"
        )
                || valor.startsWith(
                "ANTITRANSPIR"
        );
    }

    static boolean isDescritorAntiperspirante(
            @NonNull String texto
    ) {
        String valor =
                normalizar(
                        texto
                );
        return valor.equals(
                ANTIPERSPIRANTE
        )
                || valor.equals(
                "ANTIPERSPIRANT"
        )
                || valor.equals(
                "ANTIPERSPIRAN"
        )
                || valor.equals(
                "ANTIPERSPIRANI"
        )
                || valor.startsWith(
                "ANTIPERSPIR"
        );
    }

    /*
     * =========================================================
     * TERMOS EXATOS
     * =========================================================
     */
    static boolean contemTermo(
            @NonNull String texto,
            @NonNull String termo
    ) {
        String textoNormalizado =
                normalizar(
                        texto
                );
        String termoNormalizado =
                normalizar(
                        termo
                );
        if (textoNormalizado.isEmpty()
                || termoNormalizado.isEmpty()) {
            return false;
        }
        String textoComEspacos =
                " "
                        + textoNormalizado
                        + " ";
        String termoComEspacos =
                " "
                        + termoNormalizado
                        + " ";
        return textoComEspacos.contains(
                termoComEspacos
        );
    }

    /*
     * =========================================================
     * CAPACIDADE
     * =========================================================
     */
    @NonNull
    static String extrairCapacidade(
            @NonNull String texto
    ) {
        /*
         * Padrão normal:
         *
         * 250 ML
         * 200 G
         * 1 L
         */
        Matcher matcher =
                CAPACIDADE_PATTERN.matcher(
                        texto
                );
        if (matcher.find()) {
            String numero =
                    matcher.group(1);
            String unidade =
                    matcher.group(2);
            if (numero != null
                    && unidade != null) {
                unidade =
                        unidade
                                .toUpperCase(
                                        Locale.ROOT
                                )
                                .replace(
                                        "M1",
                                        "ML"
                                )
                                .replace(
                                        "ML1",
                                        "ML"
                                );
                return numero
                        + " "
                        + unidade;
            }
        }
        /*
         * Tolerância OCR:
         *
         * E250 ML
         * I250 ML
         * O250 ML
         */
        Matcher ocrMatcher =
                CAPACIDADE_OCR_PREFIX_PATTERN.matcher(
                        texto
                );
        if (ocrMatcher.find()) {
            String numero =
                    ocrMatcher.group(1);
            String unidade =
                    ocrMatcher.group(2);
            if (numero != null
                    && unidade != null) {
                unidade =
                        unidade
                                .toUpperCase(
                                        Locale.ROOT
                                )
                                .replace(
                                        "M1",
                                        "ML"
                                )
                                .replace(
                                        "ML1",
                                        "ML"
                                );
                return numero
                        + " "
                        + unidade;
            }
        }
        return "";
    }

    /*
     * =========================================================
     * CONCENTRAÇÃO
     * =========================================================
     */
    @NonNull
    static String extrairConcentracao(
            @NonNull String texto
    ) {
        Matcher matcher =
                CONCENTRACAO_PATTERN.matcher(
                        texto
                );
        if (!matcher.find()) {
            return "";
        }
        String numero =
                matcher.group(1);
        if (numero == null
                || numero.isEmpty()) {
            return "";
        }
        return numero
                + "%";
    }

    /*
     * =========================================================
     * RUÍDO FORTE
     * =========================================================
     */
    static boolean isLinhaDeRuidoForte(
            @NonNull String linha
    ) {
        String texto =
                normalizar(
                        linha
                );
        if (texto.contains(
                "PRECAU"
        )) {
            return true;
        }
        if (texto.contains(
                "MODO DE USO"
        )) {
            return true;
        }
        if (texto.contains(
                "APLICAR O PRODUTO"
        )) {
            return true;
        }
        if (texto.contains(
                "MANTER FORA"
        )) {
            return true;
        }
        if (texto.contains(
                "NAO USAR"
        )) {
            return true;
        }
        if (texto.contains(
                "EVITAR A INALACAO"
        )) {
            return true;
        }
        if (texto.contains(
                "PROTEGER OS OLHOS"
        )) {
            return true;
        }
        if (texto.contains(
                "EM CASO DE"
        )) {
            return true;
        }
        if (texto.contains(
                "INGREDIENTES"
        )) {
            return true;
        }
        if (texto.contains(
                "INGREDIENTS"
        )) {
            return true;
        }
        return false;
    }

    /*
     * =========================================================
     * LIMPEZA NUMÉRICA
     * =========================================================
     */
    @NonNull
    static String removerDadosNumericos(
            @NonNull String texto
    ) {
        String resultado =
                CAPACIDADE_PATTERN
                        .matcher(
                                texto
                        )
                        .replaceAll(
                                " "
                        );
        /*
         * Remove também:
         *
         * E250 ML
         * I250 ML
         * O250 ML
         */
        resultado =
                CAPACIDADE_OCR_PREFIX_PATTERN
                        .matcher(
                                resultado
                        )
                        .replaceAll(
                                " "
                        );
        /*
         * Remove:
         *
         * 84 FLOZ
         * 84FLOZ
         * 8.4 FL OZ
         */
        resultado =
                FL_OZ_PATTERN
                        .matcher(
                                resultado
                        )
                        .replaceAll(
                                " "
                        );
        resultado =
                CONCENTRACAO_PATTERN
                        .matcher(
                                resultado
                        )
                        .replaceAll(
                                " "
                        );
        /*
         * 72 H -> 72H
         *
         * Continua válido como evidência do produto.
         */
        Matcher matcher =
                HORAS_PATTERN.matcher(
                        resultado
                );
        StringBuffer buffer =
                new StringBuffer();
        while (matcher.find()) {
            String numero =
                    matcher.group(1);
            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(
                            numero + "H"
                    )
            );
        }
        matcher.appendTail(
                buffer
        );
        return normalizar(
                buffer.toString()
        );
    }

    /*
     * =========================================================
     * NORMALIZAÇÃO
     * =========================================================
     */
    @NonNull
    static String normalizar(
            String texto
    ) {
        if (texto == null) {
            return "";
        }
        String resultado =
                ScanTextNormalizer.normalize(
                        texto
                );
        resultado =
                resultado
                        .replaceAll(
                                "[^A-Z0-9 ]",
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();
        /*
         * 72 H -> 72H
         */
        Matcher matcher =
                Pattern.compile(
                        "(?i)\\b(\\d{1,3})\\s+H\\b"
                ).matcher(
                        resultado
                );
        StringBuffer buffer =
                new StringBuffer();
        while (matcher.find()) {
            String numero =
                    matcher.group(1);
            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(
                            numero + "H"
                    )
            );
        }
        matcher.appendTail(
                buffer
        );
        return buffer
                .toString()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    /*
     * =========================================================
     * UTILITÁRIOS
     * =========================================================
     */
    static void adicionarProduto(
            @NonNull List<String> destino,
            String valor
    ) {
        if (valor == null) {
            return;
        }
        String normalizado =
                normalizar(
                        valor
                );
        if (normalizado.isEmpty()) {
            return;
        }
        if (!possuiLetras(
                normalizado
        )) {
            return;
        }
        adicionarUnico(
                destino,
                normalizado
        );
    }

    static void adicionarUnico(
            @NonNull List<String> destino,
            String valor
    ) {
        if (valor == null) {
            return;
        }
        String normalizado =
                normalizar(
                        valor
                );
        if (normalizado.isEmpty()) {
            return;
        }
        if (!destino.contains(
                normalizado
        )) {
            destino.add(
                    normalizado
            );
        }
    }

    static boolean isSomenteNumero(
            @NonNull String texto
    ) {
        return texto.matches(
                "\\d+(?:[.,]\\d+)?"
        );
    }

    static boolean possuiLetras(
            @NonNull String texto
    ) {
        for (int i = 0;
             i < texto.length();
             i++) {
            if (Character.isLetter(
                    texto.charAt(i)
            )) {
                return true;
            }
        }
        return false;
    }

    /*
     * =========================================================
     * SIMILARIDADE
     * =========================================================
     */
    private static double similaridade(
            @NonNull String a,
            @NonNull String b
    ) {
        if (a.equals(
                b
        )) {
            return 1.0;
        }
        int maior =
                Math.max(
                        a.length(),
                        b.length()
                );
        if (maior == 0) {
            return 1.0;
        }
        int distancia =
                levenshtein(
                        a,
                        b
                );
        return 1.0
                - (
                (double)
                        distancia
                        / maior
        );
    }

    private static int levenshtein(
            @NonNull String a,
            @NonNull String b
    ) {
        int[] anterior =
                new int[
                        b.length() + 1
                        ];
        int[] atual =
                new int[
                        b.length() + 1
                        ];
        for (int j = 0;
             j <= b.length();
             j++) {
            anterior[j] =
                    j;
        }
        for (int i = 1;
             i <= a.length();
             i++) {
            atual[0] =
                    i;
            for (int j = 1;
                 j <= b.length();
                 j++) {
                int custo =
                        a.charAt(
                                i - 1
                        )
                                == b.charAt(
                                j - 1
                        )
                                ? 0
                                : 1;
                atual[j] =
                        Math.min(
                                Math.min(
                                        atual[j - 1] + 1,
                                        anterior[j] + 1
                                ),
                                anterior[j - 1]
                                        + custo
                        );
            }
            int[] temp =
                    anterior;
            anterior =
                    atual;
            atual =
                    temp;
        }
        return anterior[
                b.length()
                ];
    }
}
