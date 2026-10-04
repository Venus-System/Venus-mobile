package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import java.util.Locale;

/** Internal collaborator for ScanCosmeticClassifier; not a second scan entry point. */
final class ScanCosmeticText {
    private ScanCosmeticText() { }

    /*
     * Distância fuzzy controlada.
     * Não usamos fuzzy para termos muito curtos.
     */
    private static final int FUZZY_MAX_DISTANCE_SHORT = 1;

    private static final int FUZZY_MAX_DISTANCE_MEDIUM = 1;

    private static final int FUZZY_MAX_DISTANCE_LONG = 2;

    static boolean temLetras(
            String valor
    ) {
        if (valor == null
                || valor.isEmpty()) {
            return false;
        }
        for (int i = 0;
             i < valor.length();
             i++) {
            if (Character.isLetter(
                    valor.charAt(i)
            )) {
                return true;
            }
        }
        return false;
    }

    static boolean pareceCodigo(
            String valor
    ) {
        if (valor == null
                || valor.isEmpty()) {
            return false;
        }
        int digitos = 0;
        for (int i = 0;
             i < valor.length();
             i++) {
            if (Character.isDigit(
                    valor.charAt(i)
            )) {
                digitos++;
            }
        }
        int semEspacos =
                valor
                        .replace(
                                " ",
                                ""
                        )
                        .length();
        return digitos >= 5
                && semEspacos > 0
                && digitos >= semEspacos * 0.60;
    }

    static boolean pareceCapacidade(
            String valor
    ) {
        if (valor == null
                || valor.isEmpty()) {
            return false;
        }
        String v =
                valor
                        .replace(
                                ",",
                                "."
                        )
                        .replace(
                                " ",
                                ""
                        );
        return v.matches(
                "\\d+(\\.\\d+)?"
                        + "(ML|L|G|KG|MG|OZ|FLOZ)"
        );
    }

    /*
     * =============================================================
     * MATCH DE TEXTO / OCR
     * =============================================================
     */
    static boolean contemTermo(
            String texto,
            String termo,
            boolean permitirFuzzy
    ) {
        String nTexto =
                normalizar(texto);
        String nTermo =
                normalizar(termo);
        if (nTexto.isEmpty()
                || nTermo.isEmpty()) {
            return false;
        }
        /*
         * Match exato por token/frase.
         */
        String paddedTexto =
                " " + nTexto + " ";
        String paddedTermo =
                " " + nTermo + " ";
        if (paddedTexto.contains(
                paddedTermo
        )) {
            return true;
        }
        /*
         * Não executar fuzzy para termos curtos.
         */
        if (!permitirFuzzy
                || nTermo.length() < 7) {
            return false;
        }
        String[] tokensTexto =
                nTexto.split("\\s+");
        String[] tokensTermo =
                nTermo.split("\\s+");
        /*
         * Frases.
         */
        if (tokensTermo.length > 1
                && tokensTexto.length >= tokensTermo.length) {
            for (int i = 0;
                 i <= tokensTexto.length
                         - tokensTermo.length;
                 i++) {
                boolean todosCasam =
                        true;
                for (int j = 0;
                     j < tokensTermo.length;
                     j++) {
                    if (!tokenPareceIgual(
                            tokensTexto[i + j],
                            tokensTermo[j]
                    )) {
                        todosCasam =
                                false;
                        break;
                    }
                }
                if (todosCasam) {
                    return true;
                }
            }
        }
        /*
         * Palavra única.
         */
        if (tokensTermo.length == 1) {
            for (String tokenTexto :
                    tokensTexto) {
                if (tokenPareceIgual(
                        tokenTexto,
                        nTermo
                )) {
                    return true;
                }
            }
        }
        /*
         * OCR que grudou palavras.
         */
        if (tokensTermo.length == 1
                && nTermo.length() >= 8) {
            String compactado =
                    nTexto.replace(
                            " ",
                            ""
                    );
            if (compactado.contains(
                    nTermo
            )) {
                return true;
            }
        }
        return false;
    }

    private static boolean tokenPareceIgual(
            String tokenTexto,
            String tokenTermo
    ) {
        if (tokenTexto == null
                || tokenTermo == null) {
            return false;
        }
        if (tokenTexto.equals(
                tokenTermo
        )) {
            return true;
        }
        if (tokenTexto.length() < 6
                || tokenTermo.length() < 6) {
            return false;
        }
        int maxDistance;
        if (tokenTermo.length() <= 7) {
            maxDistance =
                    FUZZY_MAX_DISTANCE_SHORT;
        } else if (tokenTermo.length() <= 12) {
            maxDistance =
                    FUZZY_MAX_DISTANCE_MEDIUM;
        } else {
            maxDistance =
                    FUZZY_MAX_DISTANCE_LONG;
        }
        return levenshtein(
                tokenTexto,
                tokenTermo
        ) <= maxDistance;
    }

    private static int levenshtein(
            String a,
            String b
    ) {
        int[] previous =
                new int[
                        b.length() + 1
                        ];
        int[] current =
                new int[
                        b.length() + 1
                        ];
        for (int j = 0;
             j <= b.length();
             j++) {
            previous[j] =
                    j;
        }
        for (int i = 1;
             i <= a.length();
             i++) {
            current[0] =
                    i;
            for (int j = 1;
                 j <= b.length();
                 j++) {
                int custo =
                        a.charAt(i - 1)
                                == b.charAt(j - 1)
                                ? 0
                                : 1;
                current[j] =
                        Math.min(
                                Math.min(
                                        current[j - 1]
                                                + 1,
                                        previous[j]
                                                + 1
                                ),
                                previous[j - 1]
                                        + custo
                        );
            }
            int[] temp =
                    previous;
            previous =
                    current;
            current =
                    temp;
        }
        return previous[
                b.length()
                ];
    }

    /*
     * =============================================================
     * NORMALIZAÇÃO
     * =============================================================
     */
    @NonNull
    static String normalizar(
            String texto
    ) {
        if (texto == null) {
            return "";
        }
        return ScanTextNormalizer
                .normalize(texto)
                .replaceAll(
                        "[^A-Z0-9% ]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    /*
     * =============================================================
     * TEXTO COMPLETO
     * =============================================================
     */
    @NonNull
    static String montarTextoCompleto(
            @NonNull ScanOcrResult ocr,
            @NonNull ScanFrontData frontData
    ) {
        StringBuilder builder =
                new StringBuilder();
        adicionarTexto(
                builder,
                ocr.getFullText()
        );
        if (ocr.getLines() != null) {
            for (String linha :
                    ocr.getLines()) {
                adicionarTexto(
                        builder,
                        linha
                );
            }
        }
        if (frontData.getBrandCandidates() != null) {
            for (String item :
                    frontData
                            .getBrandCandidates()) {
                adicionarTexto(
                        builder,
                        item
                );
            }
        }
        if (frontData.getProductCandidates() != null) {
            for (String item :
                    frontData
                            .getProductCandidates()) {
                adicionarTexto(
                        builder,
                        item
                );
            }
        }
        if (frontData.getPresentationCandidates() != null) {
            for (String item :
                    frontData
                            .getPresentationCandidates()) {
                adicionarTexto(
                        builder,
                        item
                );
            }
        }
        return normalizar(
                builder.toString()
        );
    }

    private static void adicionarTexto(
            @NonNull StringBuilder builder,
            String texto
    ) {
        if (texto == null
                || texto.trim().isEmpty()) {
            return;
        }
        builder
                .append(' ')
                .append(texto);
    }

    /*
     * =============================================================
     * RUÍDO
     * =============================================================
     */
    static boolean ehRuidoComum(
            String valor
    ) {
        if (valor == null
                || valor.isEmpty()) {
            return true;
        }
        return valor.equals("ANDROID")
                || valor.equals("ANDROID V")
                || valor.equals("LOGCAT")
                || valor.equals("LOGINACTIVITY")
                || valor.equals("LOGIN")
                || valor.equals("JAVA")
                || valor.equals("KOTLIN")
                || valor.equals("SRC")
                || valor.equals("MAIN")
                || valor.equals("PUBLIC")
                || valor.equals("PRIVATE")
                || valor.equals("STATIC")
                || valor.equals("CLASS")
                || valor.equals("FINAL")
                || valor.equals("INTENT")
                || valor.equals("ACTIVITY")
                || valor.equals("SYSTEM")
                || valor.equals("SERVER")
                || valor.equals("SYSTEM SERVER")
                || valor.equals("SURFACEFLINGER")
                || valor.equals("SCANCONTROLLER")
                || valor.equals("CANFRONTANALYZERJAVA")
                || valor.equals("WIFISTAIFACEHIDLIMPL")
                || valor.equals("VENUSMOBILE")
                || valor.equals("SAMSUNG")
                || valor.equals("DEBUG")
                || valor.equals("CTRL")
                || valor.equals("CTRL I")
                || valor.equals("CTRL TL");
    }
}
