package com.venussystem.venusmobile.view.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CEP_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CNPJ_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CONTACT_CONTEXT_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMPTY;
import static com.venussystem.venusmobile.view.util.ScanBackRules.PHONE_PATTERN;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackText {
    private ScanBackText() { }

    /*
     * ================================================================
     * LIXO ADMINISTRATIVO
     * ================================================================
     */
    private static final String[] ADMIN_GARBAGE = {
            "CNPJ",
            "CEP",
            "AUTFUNC",
            "AUT FUNC",
            "AUTORIZACAO",
            "REGISTRO",
            "PROCESSO",
            "LOTE",
            "LQTE",
            "L0TE",
            "BATCH",
            "VALIDADE",
            "VENCIMENTO",
            "EXPIRY",
            "SAC",
            "ATENDIMENTO",
            "CONSUMIDOR",
            "WHATSAPP",
            "IMPORTADO",
            "DISTRIBUIDO",
            "FABRICADO",
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA",
            "SARGENTO",
            "RUA",
            "AVENIDA",
            "CALLE",
            "CARRERA",
            "PISO",
            "PAIS DE ORIGEM",
            "MADE IN",
            "WWW.",
            "HTTP",
            "HTTPS",
            "LTDA",
            "LTD",
            "S/A",
            "S A",
            "SAS",
            "SRL",
            "LLC",
            "INC",
            "CORP",
            "CORPORATION",
            "PLC",
            "GMBH",
            "EIRELI"
    };

    /**
     * Encontra o início do primeiro match de um Pattern diretamente na
     * string original. Retorna -1 quando não houver match.
     */
    static int localizarIndiceOriginalPorRegex(
            String original,
            Pattern pattern
    ) {
        if (
                original == null
                        || original.isEmpty()
                        || pattern == null
        ) {
            return -1;
        }
        Matcher matcher =
                pattern.matcher(
                        original
                );
        return matcher.find()
                ? matcher.start()
                : -1;
    }

    private static int localizarIndiceAproximadoPorNormalizedIndex(
            String original,
            int normalizedIndex
    ) {
        if (
                original == null
                        || normalizedIndex <= 0
        ) {
            return 0;
        }
        StringBuilder normalized =
                new StringBuilder();
        int originalIndex =
                0;
        while (
                originalIndex < original.length()
        ) {
            String piece =
                    normalizar(
                            String.valueOf(
                                    original.charAt(
                                            originalIndex
                                    )
                            )
                    );
            if (!piece.isEmpty()) {
                normalized.append(
                        piece
                );
            }
            if (
                    normalized.length()
                            >= normalizedIndex
            ) {
                break;
            }
            originalIndex++;
        }
        return Math.min(
                originalIndex,
                original.length()
        );
    }

    /**
     * Localiza um termo INCI na linha original respeitando fronteiras de
     * palavra. Isso é preferível ao mapeamento por índice normalizado para
     * evitar perder o primeiro caractere do ingrediente, como em:
     *   \"UENTES: BUTANE...\" -> \"BUTANE...\"
     */
    static int localizarIndiceOriginalPorTextoComFronteira(
            String original,
            String marker
    ) {
        if (
                original == null
                        || marker == null
                        || marker.trim().isEmpty()
        ) {
            return -1;
        }
        String normalizedMarker =
                normalizar(
                        marker
                );
        if (normalizedMarker.isEmpty()) {
            return -1;
        }
        /*
         * 1) Primeiro tenta no texto ORIGINAL.
         * Isso preserva a posição exata e evita cortes tardios como
         * "LINALOOL .FABRI".
         */
        Pattern originalPattern =
                Pattern.compile(
                        "(?i)(?<![A-Z0-9])"
                                + Pattern.quote(marker.trim())
                                + "(?![A-Z0-9])"
                );
        Matcher originalMatcher =
                originalPattern.matcher(original);
        if (originalMatcher.find()) {
            return originalMatcher.start();
        }
        /*
         * 2) Fallback para OCR que perdeu acentos ou alterou espaçamento.
         */
        Pattern normalizedPattern =
                Pattern.compile(
                        "(?i)(?<![A-Z0-9])"
                                + Pattern.quote(normalizedMarker)
                                + "(?![A-Z0-9])"
                );
        Matcher normalizedMatcher =
                normalizedPattern.matcher(
                        normalizar(original)
                );
        if (!normalizedMatcher.find()) {
            return -1;
        }
        return localizarIndiceAproximadoPorNormalizedIndex(
                original,
                normalizedMatcher.start()
        );
    }

    static int localizarIndiceAproximadoOriginal(
            String original,
            String marker
    ) {
        String nOriginal =
                normalizar(
                        original
                );
        String nMarker =
                normalizar(
                        marker
                );
        int normalizedIndex =
                nOriginal.indexOf(
                        nMarker
                );
        if (normalizedIndex < 0) {
            return -1;
        }
        int originalPos =
                0;
        int normalizedPos =
                0;
        while (
                originalPos
                        < original.length()
                        && normalizedPos
                        < normalizedIndex
        ) {
            char c =
                    original.charAt(
                            originalPos++
                    );
            String one =
                    normalizar(
                            String.valueOf(
                                    c
                            )
                    );
            if (!one.isEmpty()) {
                normalizedPos +=
                        one.length();
            }
        }
        return Math.min(
                originalPos,
                original.length()
        );
    }

    /*
     * ================================================================
     * HELPERS
     * ================================================================
     */
    static String joinLines(
            List<String> lines
    ) {
        if (
                lines == null
                        || lines.isEmpty()
        ) {
            return EMPTY;
        }
        List<String> clean =
                new ArrayList<>();
        for (
                String line
                : lines
        ) {
            if (
                    line != null
                            && !line.trim().isEmpty()
            ) {
                clean.add(
                        line.trim()
                );
            }
        }
        return String.join(
                "\n",
                clean
        );
    }

    static List<String> normalizarLinhas(
            List<String> source
    ) {
        if (source == null) {
            return Collections.emptyList();
        }
        List<String> result =
                new ArrayList<>();
        for (
                String line
                : source
        ) {
            if (line == null) {
                continue;
            }
            String clean =
                    line
                            .replace(
                                    '\u0000',
                                    ' '
                            )
                            .replaceAll(
                                    "[\\t\\r]+",
                                    " "
                            )
                            .replaceAll(
                                    "\\s+",
                                    " "
                            )
                            .trim();
            if (!clean.isEmpty()) {
                result.add(
                        clean
                );
            }
        }
        return result;
    }

    static String normalizarOCRBasico(
            String value
    ) {
        if (value == null) {
            return EMPTY;
        }
        return value
                .replace(
                        '\u0000',
                        ' '
                )
                .replace(
                        '’',
                        '\''
                )
                .replace(
                        '“',
                        '"'
                )
                .replace(
                        '”',
                        '"'
                )
                .replace(
                        '–',
                        '-'
                )
                .replace(
                        '—',
                        '-'
                )
                .replaceAll(
                        "[\\t\\r\\n]+",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    static String normalizar(
            String value
    ) {
        if (value == null) {
            return EMPTY;
        }
        String text =
                Normalizer
                        .normalize(
                                value,
                                Normalizer.Form.NFD
                        )
                        .replaceAll(
                                "\\p{InCombiningDiacriticalMarks}+",
                                EMPTY
                        )
                        .toUpperCase(
                                Locale.ROOT
                        )
                        .replace(
                                'Ç',
                                'C'
                        );
        return text
                .replaceAll(
                        "[^A-Z0-9./:%+@#&' -]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    static String compactarEspacos(
            String value
    ) {
        return value == null
                ? EMPTY
                : value
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    static String limparPontosFinais(
            String value
    ) {
        if (value == null) {
            return EMPTY;
        }
        return value
                .replaceAll(
                        "[. ]+$",
                        ""
                )
                .trim();
    }

    static String substringSafe(
            String value,
            int start
    ) {
        if (
                value == null
                        || start >= value.length()
        ) {
            return EMPTY;
        }
        return value.substring(
                Math.max(
                        0,
                        start
                )
        );
    }

    static String safeSubstring(
            String value,
            int start,
            int end
    ) {
        if (value == null) {
            return EMPTY;
        }
        int s =
                Math.max(
                        0,
                        Math.min(
                                start,
                                value.length()
                        )
                );
        int e =
                Math.max(
                        s,
                        Math.min(
                                end,
                                value.length()
                        )
                );
        return value.substring(
                s,
                e
        );
    }

    static String limparRestoHeading(
            String value
    ) {
        if (value == null) {
            return EMPTY;
        }
        return value
                .replaceFirst(
                        "^[\\s:;.,#\\-]+",
                        EMPTY
                )
                .trim();
    }

    static HeadingMatch encontrarHeading(
            String normalizedLine,
            String[] headings,
            boolean allowFuzzy
    ) {
        if (normalizedLine == null) {
            return null;
        }
        String line =
                compactarEspacos(
                        normalizar(
                                normalizedLine
                        )
                );
        for (
                String heading
                : headings
        ) {
            String h =
                    normalizar(
                            heading
                    );
            int idx =
                    indexOfMarkerWithBoundary(
                            line,
                            h
                    );
            if (idx >= 0) {
                return new HeadingMatch(
                        heading,
                        idx + h.length()
                );
            }
        }
        /*
         * Fuzzy somente para heading longo.
         */
        if (
                !allowFuzzy
                        || line.length() > 80
        ) {
            return null;
        }
        String[] tokens =
                line.split(
                        " "
                );
        for (
                String heading
                : headings
        ) {
            String h =
                    normalizar(
                            heading
                    );
            String[] ht =
                    h.split(
                            " "
                    );
            if (
                    ht.length == 1
                            && ht[0].length() >= 7
            ) {
                for (
                        String token
                        : tokens
                ) {
                    if (
                            token.length() >= 6
                                    && levenshtein(
                                    token,
                                    ht[0]
                            ) <= 1
                    ) {
                        return new HeadingMatch(
                                heading,
                                line.indexOf(
                                        token
                                )
                                        + token.length()
                        );
                    }
                }
            }
        }
        return null;
    }

    static boolean containsHeading(
            String line,
            String[] headings
    ) {
        return encontrarHeading(
                line,
                headings,
                true
        ) != null;
    }

    static boolean ehNovoBloco(
            String line,
            String[] headings
    ) {
        return containsHeading(
                line,
                headings
        );
    }

    static boolean pareceAdministrativoForte(
            String line
    ) {
        if (
                line == null
                        || line.isEmpty()
        ) {
            return false;
        }
        String n =
                normalizar(
                        line
                );
        if (
                CNPJ_PATTERN
                        .matcher(
                                n
                        )
                        .find()
                        || CEP_PATTERN
                        .matcher(
                                n
                        )
                        .find()
        ) {
            return true;
        }
        if (
                PHONE_PATTERN
                        .matcher(
                                n
                        )
                        .find()
                        && !n.matches(
                        ".*\\b(?:AQUA|WATER|ALCOHOL)\\b.*"
                )
        ) {
            /*
             * Telefone isolado é administrativo.
             */
            return true;
        }
        for (
                String token
                : ADMIN_GARBAGE
        ) {
            if (
                    contémTermoComFronteira(
                            n,
                            token
                    )
            ) {
                return true;
            }
        }
        return false;
    }

    static boolean ehTextoAdministrativoObvio(
            String value
    ) {
        if (value == null) {
            return false;
        }
        String n =
                normalizar(
                        value
                );
        return CNPJ_PATTERN
                .matcher(n)
                .find()
                || CEP_PATTERN
                .matcher(n)
                .find()
                || CONTACT_CONTEXT_PATTERN.matcher(n).find()
                || temContextoAdministrativo(n);
    }

    static boolean temContextoAdministrativo(
            String n
    ) {
        if (n == null) {
            return false;
        }
        for (
                String token
                : ADMIN_GARBAGE
        ) {
            if (
                    contémTermoComFronteira(
                            n,
                            token
                    )
            ) {
                return true;
            }
        }
        return false;
    }

    static boolean temRuidoAdministrativo(
            String n
    ) {
        return temContextoAdministrativo(
                n
        )
                || n.contains(
                "CONTEUDO SOB PRESSAO"
        )
                || n.contains(
                "PROTEGER OS OLHOS"
        )
                || n.contains(
                "ATENDIMENTO AO CONSUMIDOR"
        );
    }

    static boolean temRuidoAdministrativoExcessivo(
            String line
    ) {
        String n =
                normalizar(
                        line
                );
        int hits =
                0;
        for (
                String token
                : ADMIN_GARBAGE
        ) {
            if (
                    contémTermoComFronteira(
                            n,
                            token
                    )
            ) {
                hits++;
            }
        }
        return hits >= 2;
    }

    static int indexOfMarkerWithBoundary(
            String text,
            String marker
    ) {
        if (
                text == null
                        || marker == null
                        || marker.isEmpty()
        ) {
            return -1;
        }
        int idx =
                text.indexOf(
                        marker
                );
        while (
                idx >= 0
        ) {
            int end =
                    idx
                            + marker.length();
            boolean left =
                    idx == 0
                            || !Character.isLetterOrDigit(
                            text.charAt(
                                    idx - 1
                            )
                    );
            boolean right =
                    end >= text.length()
                            || !Character.isLetterOrDigit(
                            text.charAt(
                                    end
                            )
                    );
            if (
                    left
                            && right
            ) {
                return idx;
            }
            idx =
                    text.indexOf(
                            marker,
                            idx + 1
                    );
        }
        return -1;
    }

    static boolean contémTermoComFronteira(
            String text,
            String term
    ) {
        if (
                text == null
                        || term == null
        ) {
            return false;
        }
        String t =
                normalizar(
                        term
                );
        String n =
                normalizar(
                        text
                );
        return indexOfMarkerWithBoundary(
                n,
                t
        ) >= 0;
    }

    static boolean temQualquerToken(
            String line,
            String[] tokens
    ) {
        for (
                String token
                : tokens
        ) {
            if (
                    contémTermoComFronteira(
                            line,
                            token
                    )
            ) {
                return true;
            }
        }
        return false;
    }

    static String limparLinhaIngredientes(
            String line
    ) {
        String n =
                normalizarOCRBasico(
                        line
                );
        /*
         * Mantemos pontuação interna.
         * Ela ainda pode ser necessária para segmentação.
         */
        return n
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    static int contarLetras(
            String value
    ) {
        int count =
                0;
        for (
                int i = 0;
                i < value.length();
                i++
        ) {
            if (
                    Character.isLetter(
                            value.charAt(
                                    i
                            )
                    )
            ) {
                count++;
            }
        }
        return count;
    }

    static String limitarTexto(
            String value,
            int max
    ) {
        if (value == null) {
            return EMPTY;
        }
        String v =
                compactarEspacos(
                        value
                );
        return v.length() <= max
                ? v
                : v.substring(
                0,
                max
        ).trim();
    }

    static List<String> deduplicarPreservandoOrdem(
            List<String> values
    ) {
        LinkedHashSet<String> set =
                new LinkedHashSet<>();
        for (
                String value
                : values
        ) {
            String clean =
                    compactarEspacos(
                            value
                    );
            if (!clean.isEmpty()) {
                set.add(
                        clean
                );
            }
        }
        return new ArrayList<>(
                set
        );
    }

    /*
     * ================================================================
     * LEVENSHTEIN
     * ================================================================
     */
    static int levenshtein(
            String a,
            String b
    ) {
        int[] prev =
                new int[
                        b.length() + 1
                        ];
        int[] curr =
                new int[
                        b.length() + 1
                        ];
        for (
                int j = 0;
                j <= b.length();
                j++
        ) {
            prev[j] =
                    j;
        }
        for (
                int i = 1;
                i <= a.length();
                i++
        ) {
            curr[0] =
                    i;
            for (
                    int j = 1;
                    j <= b.length();
                    j++
            ) {
                int cost =
                        a.charAt(
                                i - 1
                        )
                                == b.charAt(
                                j - 1
                        )
                                ? 0
                                : 1;
                curr[j] =
                        Math.min(
                                Math.min(
                                        curr[j - 1] + 1,
                                        prev[j] + 1
                                ),
                                prev[j - 1] + cost
                        );
            }
            int[] temp =
                    prev;
            prev =
                    curr;
            curr =
                    temp;
        }
        return prev[
                b.length()
                ];
    }

    static final class HeadingMatch {
        final String term;
        final int endInOriginal;
        HeadingMatch(
                String term,
                int endInOriginal
        ) {
            this.term =
                    term;
            this.endInOriginal =
                    endInOriginal;
        }
    }
}
