package com.venussystem.venusmobile.view.util;

import com.venussystem.venusmobile.model.ScanBackIngredientCandidate;
import com.venussystem.venusmobile.model.ScanOcrToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import static com.venussystem.venusmobile.view.util.ScanBackInciVocabulary.inciCompleto;
import static com.venussystem.venusmobile.view.util.ScanBackInciVocabulary.inciOrdenadosPorComprimento;
import static com.venussystem.venusmobile.view.util.ScanBackInciVocabulary.linhaComecaComInciConhecido;
import static com.venussystem.venusmobile.view.util.ScanBackInciVocabulary.prefixoInci;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.IngredientSection;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.StopMatch;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.detectarFimIngredientes;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.encontrarHeadingIngredientesRobusto;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.extrairPrefixoAntesDoStop;
import static com.venussystem.venusmobile.view.util.ScanBackRules.ADDRESS_INLINE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CONTACT_CONTEXT_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMAIL_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMPTY;
import static com.venussystem.venusmobile.view.util.ScanBackRules.INLINE_COMPANY_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.PHONE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.URL_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackText.HeadingMatch;
import static com.venussystem.venusmobile.view.util.ScanBackText.compactarEspacos;
import static com.venussystem.venusmobile.view.util.ScanBackText.deduplicarPreservandoOrdem;
import static com.venussystem.venusmobile.view.util.ScanBackText.ehTextoAdministrativoObvio;
import static com.venussystem.venusmobile.view.util.ScanBackText.limparPontosFinais;
import static com.venussystem.venusmobile.view.util.ScanBackText.limparRestoHeading;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizarOCRBasico;
import static com.venussystem.venusmobile.view.util.ScanBackText.substringSafe;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackIngredients {
    private ScanBackIngredients() { }

    /*
     * ================================================================
     * EXTRAÇÃO DE CANDIDATOS
     * ================================================================
     */
    static List<ScanBackIngredientCandidate> extrairIngredientes(
            IngredientSection section,
            List<ScanOcrToken> spatialTokens
    ) {
        if (
                !section.found
                        || section.rawText.isEmpty()
        ) {
            return Collections.emptyList();
        }
        String raw =
                reconstruirBlocoIngredientesEspacialmente(
                        section,
                        spatialTokens
                );
        if (raw.isEmpty()) {
            raw = section.rawText;
        }
        raw = prepararBlocoIngredientes(raw);
        List<String> parts =
                separarCandidatosIngredientes(
                        raw
                );
        List<ScanBackIngredientCandidate> result =
                new ArrayList<>();
        int position =
                1;
        for (
                String part
                : parts
        ) {
            String cleaned =
                    limparNomeIngrediente(
                            part
                    );
            if (cleaned.isEmpty()) {
                continue;
            }
            ScanIngredientNormalizer.Correction correction = ScanIngredientNormalizer.correct(cleaned);
            result.add(
                    new ScanBackIngredientCandidate(
                            position++,
                            cleaned,
                            correction.normalized,
                            // Correction improves matching but does not confirm the ingredient.
                            "UNRESOLVED"
                    )
            );
        }
        return result;
    }

    /**
     * Rows were already ordered geometrically before section detection.
     * A paragraph stays a paragraph. Only repeated, aligned column pairs with
     * recognized INCI evidence may introduce translation parentheses.
     */
    private static String reconstruirBlocoIngredientesEspacialmente(
            IngredientSection section, List<ScanOcrToken> tokens) {
        if (tokens == null || tokens.isEmpty()) return section.rawText;
        java.util.Map<Integer, List<ScanOcrToken>> rows = new java.util.TreeMap<>();
        for (ScanOcrToken t : tokens) {
            if (t.getLineIndex() >= section.startIndex && t.getLineIndex() <= section.endIndex) {
                rows.computeIfAbsent(t.getLineIndex(), k -> new ArrayList<>()).add(t);
            }
        }
        List<String> candidates = new ArrayList<>();
        String pendingLeft = EMPTY, pendingRight = EMPTY;
        Integer columnX = null;
        int lastBottom = -1;
        for (List<ScanOcrToken> row : rows.values()) {
            String text = ScanBackSpatialLayout.join(row);
            HeadingMatch heading = encontrarHeadingIngredientesRobusto(text);
            if (heading != null) {
                // Standalone heading is harmless; inline heading uses paragraph parsing.
                if (limparRestoHeading(substringSafe(text, heading.endInOriginal)).isEmpty()) continue;
                return section.rawText;
            }
            StopMatch stop = detectarFimIngredientes(text, candidates);
            if (stop != null) {
                if (extrairPrefixoAntesDoStop(text, stop).isEmpty()) break;
                return section.rawText;
            }
            // Punctuation is direct evidence of a running ingredient list.
            if (text.matches(".*[,;()|].*") || row.size() < 2) return section.rawText;
            int split = -1, gap = 0;
            List<Integer> heights = new ArrayList<>();
            for (ScanOcrToken t : row) heights.add(t.getHeight());
            Collections.sort(heights);
            int height = heights.get(heights.size() / 2);
            for (int i = 1; i < row.size(); i++) {
                int distance = row.get(i).getBoundingBox().left - row.get(i - 1).getBoundingBox().right;
                if (distance > gap) { split = i; gap = distance; }
            }
            if (split < 1 || gap < height * 2) return section.rawText;
            int x = row.get(split).getBoundingBox().left;
            if (columnX != null && Math.abs(x - columnX) > height * 3) return section.rawText;
            columnX = columnX == null ? x : columnX;
            String left = ScanBackSpatialLayout.join(row.subList(0, split));
            String right = ScanBackSpatialLayout.join(row.subList(split, row.size()));
            // A known ingredient on the right is not evidence of a translation.
            if (linhaComecaComInciConhecido(right)) return section.rawText;
            if (!pendingLeft.isEmpty()) {
                if (row.get(0).getBoundingBox().top - lastBottom > height * 2) return section.rawText;
                left = pendingLeft + " " + left;
                right = pendingRight + " " + right;
            }
            if (inciCompleto(left)) {
                candidates.add(left + " (" + right + ")");
                pendingLeft = EMPTY;
                pendingRight = EMPTY;
            } else if (prefixoInci(left)) {
                pendingLeft = left;
                pendingRight = right;
            } else {
                return section.rawText;
            }
            lastBottom = row.get(0).getBoundingBox().bottom;
        }
        if (candidates.size() < 2 || !pendingLeft.isEmpty()) return section.rawText;
        return String.join(", ", candidates);
    }

    private static String prepararBlocoIngredientes(
            String value
    ) {
        String s =
                value == null
                        ? EMPTY
                        : value;
        s =
                normalizarOCRBasico(
                        s
                );
        /*
         * Letras isoladas usadas pelo OCR como separadores.
         *
         * Exemplos:
         *
         * DIMETHICONOL E ALPHA...
         * DIMETHICONOL I ALPHA...
         * DIMETHICONOL L ALPHA...
         *
         * Só aplicamos quando existe outro token imediatamente depois.
         */
        s =
                s.replaceAll(
                        "(?i)\\s+[EIL]\\s+(?=[A-Z0-9])",
                        ", "
                );
        /*
         * Ponto como separador.
         *
         * Exemplos:
         *
         * LINALOOL. LIMONENE
         * LINALOOL .FABRICADO
         */
        s =
                s.replaceAll(
                        "(?i)(?<=[A-Z0-9\\)])"
                                + "\\.(?=\\s*[A-Z][A-Z0-9/-]{2,})",
                        ", "
                );
        s =
                compactarEspacos(
                        s
                );
        s = corrigirSeparadoresOCRConhecidos(s);
        return limparPontosFinais(
                s
        );
    }

    /**
     * Restores boundaries that are routinely lost by the OCR on small INCI
     * print. These replacements are deliberately phrase-scoped; unknown text
     * remains untouched for administrative review instead of being guessed.
     */
    private static String corrigirSeparadoresOCRConhecidos(String value) {
        String s = value == null ? EMPTY : value;
        s = s.replaceAll("(?i)\\bPPG[- ]?14\\s+BUT[L1I]\\s*E[T7]E\\b",
                "PPG-14 BUTYL ETHER,");
        s = s.replaceAll("(?i)\\b(?:DO\\.?|OO|O)?PENTAS(?:I|L)OXANE\\b",
                "CYCLOPENTASILOXANE,");
        s = s.replaceAll("(?i)\\bPAR(?:I|1)N\\s+EJANTHUS\\b",
                "PARFUM, HELIANTHUS");
        s = s.replaceAll("(?i)\\bEJANTHUS\\b", "HELIANTHUS");
        s = s.replaceAll("(?i)\\b0CTYLDODECAN(?:O|L)\\b", "OCTYLDODECANOL");
        s = s.replaceAll("(?i)\\bROPYLENE\\s+CARBONATE\\b", "PROPYLENE CARBONATE");
        s = s.replaceAll("(?i)\\bONN\\s+INIO\\b", "IONONE");
        // The OCR commonly reads the C12-15 alkyl benzoate line as CI2494 + OATE.
        s = s.replaceAll("(?i)\\bCI2494\\s+TOATE\\b", "C12-15 ALKYL BENZOATE,");
        return s;
    }

    private static List<String> separarCandidatosIngredientes(
            String raw
    ) {
        List<String> result =
                new ArrayList<>();
        if (
                raw == null
                        || raw.trim().isEmpty()
        ) {
            return result;
        }
        String prepared =
                compactarEspacos(
                        raw
                );
        /*
         * Primeiro:
         * separadores reais.
         */
        String[] explicit =
                prepared.split(
                        "\\s*[,;|•]\\s*"
                );
        /*
         * Caso o OCR tenha perdido as vírgulas e criado grandes espaços.
         */
        if (explicit.length <= 1) {
            explicit =
                    prepared.split(
                            "\\s{2,}"
                    );
        }
        for (
                String chunk
                : explicit
        ) {
            String clean =
                    limparSeparadorResidual(
                            chunk
                    );
            if (clean.isEmpty()) {
                continue;
            }
            List<String> segmented =
                    segmentarPorINCIConhecido(
                            clean
                    );
            if (
                    segmented == null
                            || segmented.isEmpty()
            ) {
                result.add(
                        clean
                );
                continue;
            }
            result.addAll(
                    segmented
            );
        }
        result =
                consolidarFragmentos(
                        result
                );
        return deduplicarPreservandoOrdem(
                result
        );
    }

    private static List<String> segmentarPorINCIConhecido(
            String chunk
    ) {
        String working =
                compactarEspacos(
                        chunk
                );
        if (working.isEmpty()) {
            return Collections.emptyList();
        }
        String upper =
                working.toUpperCase(
                        Locale.ROOT
                );
        List<Boundary> found =
                new ArrayList<>();
        /*
         * A partir da V7.0, um INCI conhecido representa o INÍCIO
         * de um candidato, e não o candidato inteiro.
         *
         * Isso é importante porque o OCR frequentemente devolve:
         *
         *     PROPANE (PROPANO)
         *     GLYCERIN GUCEROL
         *     GLYCERYL STEARATE (MONOESTEARATO ...)
         *
         * O texto após o INCI continua pertencendo ao mesmo candidato
         * até encontrarmos o próximo INCI conhecido.
         */
        for (
                String known
                : inciOrdenadosPorComprimento()
        ) {
            String k =
                    known.toUpperCase(
                            Locale.ROOT
                    );
            int search =
                    0;
            while (
                    search < upper.length()
            ) {
                int idx =
                        upper.indexOf(
                                k,
                                search
                        );
                if (idx < 0) {
                    break;
                }
                int end =
                        idx + k.length();
                boolean left =
                        idx == 0
                                || !Character.isLetterOrDigit(
                                upper.charAt(
                                        idx - 1
                                )
                        );
                boolean right =
                        end >= upper.length()
                                || !Character.isLetterOrDigit(
                                upper.charAt(
                                        end
                                )
                        );
                boolean insideParentheses =
                        estaDentroDeParenteses(
                                upper,
                                idx
                        );
                boolean nestedTranslatedIngredient =
                        insideParentheses
                                && pareceNovoIngredienteComTraducaoAninhada(
                                upper,
                                idx,
                                end
                        );
                if (
                        left
                                && right
                                && (
                                !insideParentheses
                                        || nestedTranslatedIngredient
                        )
                ) {
                    found.add(
                            new Boundary(
                                    idx,
                                    end,
                                    known
                            )
                    );
                }
                search =
                        Math.max(
                                end,
                                search + 1
                        );
            }
        }
        if (found.isEmpty()) {
            return Collections.singletonList(
                    working
            );
        }
        found.sort(
                Comparator.comparingInt(
                        a -> a.start
                )
        );
        /*
         * Remove boundaries duplicadas ou sobrepostas.
         */
        List<Boundary> unique =
                new ArrayList<>();
        int lastStart = -1;
        int lastEnd = -1;
        for (
                Boundary b
                : found
        ) {
            if (
                    b.start == lastStart
                            && b.end <= lastEnd
            ) {
                continue;
            }
            if (
                    b.start < lastEnd
            ) {
                continue;
            }
            unique.add(
                    b
            );
            lastStart =
                    b.start;
            lastEnd =
                    b.end;
        }
        if (unique.isEmpty()) {
            return Collections.singletonList(
                    working
            );
        }
        /*
         * Se o primeiro INCI conhecido não começa o chunk, não temos
         * evidência estrutural suficiente para separar o prefixo.
         *
         * Em vez de correr o risco de transformar a tradução OCR de
         * um ingrediente anterior em um novo ingrediente, preservamos
         * o chunk inteiro. O matcher posterior continua responsável
         * pela resolução semântica.
         */
        if (unique.get(0).start > 0) {
            return Collections.singletonList(
                    working
            );
        }
        List<String> parts =
                new ArrayList<>();
        for (
                int i = 0;
                i < unique.size();
                i++
        ) {
            Boundary current =
                    unique.get(
                            i
                    );
            int segmentEnd =
                    i + 1 < unique.size()
                            ? unique.get(i + 1).start
                            : working.length();
            if (
                    segmentEnd <= current.start
            ) {
                continue;
            }
            String segment =
                    working.substring(
                            current.start,
                            segmentEnd
                    ).trim();
            if (!segment.isEmpty()) {
                parts.add(
                        segment
                );
            }
        }
        if (parts.isEmpty()) {
            return Collections.singletonList(
                    working
            );
        }
        return consolidarFragmentos(
                parts
        );
    }

    private static boolean pareceNovoIngredienteComTraducaoAninhada(
            String text,
            int ingredientStart,
            int ingredientEnd
    ) {
        if (text == null || ingredientStart < 0 || ingredientEnd <= ingredientStart || ingredientEnd >= text.length()) {
            return false;
        }
        int closeOuter = text.indexOf(')', ingredientEnd);
        int openNext = text.indexOf('(', ingredientEnd);
        return openNext >= 0 && (closeOuter < 0 || openNext < closeOuter);
    }

    private static boolean estaDentroDeParenteses(
            String text,
            int index
    ) {
        if (text == null || index <= 0) {
            return false;
        }
        int depth = 0;
        for (int i = 0; i < index && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
        }
        return depth > 0;
    }

    private static List<String> consolidarFragmentos(
            List<String> input
    ) {
        List<String> out =
                new ArrayList<>();
        if (input == null) {
            return out;
        }
        for (
                String item
                : input
        ) {
            String s =
                    limparSeparadorResidual(
                            item
                    );
            if (s.isEmpty()) {
                continue;
            }
            String n =
                    normalizar(
                            s
                    );
            /*
             * Letras isoladas são ruído frequente do OCR.
             */
            if (n.length() == 1) {
                continue;
            }
            /*
             * CI pode ser prefixo de Color Index.
             */
            if ("CI".equals(n)) {
                out.add(
                        s
                );
                continue;
            }
            /*
             * Join a Color Index continuation. Other numeric fragments remain
             * candidates for review instead of silently disappearing.
             */
            if (
                    n.matches(
                            "^[0-9 .:/-]+$"
                    )
            ) {
                if (!out.isEmpty()) {
                    int last =
                            out.size() - 1;
                    String previous =
                            normalizar(
                                    out.get(
                                            last
                                    )
                            );
                    if (
                            "CI".equals(
                                    previous
                            )
                    ) {
                        out.set(
                                last,
                                compactarEspacos(
                                        out.get(
                                                last
                                        )
                                                + " "
                                                + s
                                )
                        );
                        continue;
                    }
                }
            }
            out.add(
                    s
            );
        }
        return out;
    }

    private static String limparNomeIngrediente(
            String value
    ) {
        String s =
                normalizarOCRBasico(
                        value
                )
                        .replaceFirst(
                                "^\\d+[.)]\\s*",
                                EMPTY
                        )
                        .replaceFirst(
                                "^(?:INGREDIENTES?|INGREDIENTS?|INGREDIENTI|COMPOSICAO|COMPOSITION|COMPOSICION|INCI)"
                                        + "\\s*[:;.-]?\\s*",
                                EMPTY
                        )
                        .replaceFirst(
                                "^(?:[IL]\\s+)"
                                        + "(?=(?:LIMONENE|LINALOOL)\\b)",
                                EMPTY
                        )
                        .replaceAll(
                                "^[,;|.\\- ]+",
                                EMPTY
                        )
                        .replaceAll(
                                "[,;|. ]+$",
                                EMPTY
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();
        s = s.replaceFirst(
                "^(?:INGREDIENTS?|INGREDIENTES?)\\s+",
                EMPTY
        );
        s = s.replaceFirst(
                "^[:;,.\\-]+\\s*",
                EMPTY
        );
        s = s.replaceAll(
                "\\s*[:;]\\s*",
                " "
        );
        s = s.replaceAll(
                "\\s+",
                " "
        ).trim();
        if (
                s.isEmpty()
                        || s.length() < 2
        ) {
            return EMPTY;
        }
        if (
                ehTextoAdministrativoObvio(
                        s
                )
        ) {
            return EMPTY;
        }
        if (
                ADDRESS_INLINE_PATTERN.matcher(s).find()
                        || INLINE_COMPANY_PATTERN.matcher(s).find()
        ) {
            return EMPTY;
        }
        /*
         * Proteção adicional contra telefone/e-mail/site.
         */
        if (
                PHONE_PATTERN
                        .matcher(
                                s
                        )
                        .find()
                        || EMAIL_PATTERN
                        .matcher(
                                s
                        )
                        .find()
                        || URL_PATTERN
                        .matcher(s)
                        .find()
                        || CONTACT_CONTEXT_PATTERN.matcher(s).find()
        ) {
            return EMPTY;
        }
        return s;
    }

    private static String limparSeparadorResidual(
            String value
    ) {
        if (value == null) {
            return EMPTY;
        }
        return compactarEspacos(
                value
        )
                .replaceAll(
                        "^[,;|. ]+",
                        EMPTY
                )
                .replaceAll(
                        "[,;|. ]+$",
                        EMPTY
                )
                .trim();
    }

    /*
     * ================================================================
     * ASSINATURA
     * ================================================================
     */
    static String gerarAssinaturaOCR(
            List<ScanBackIngredientCandidate> ingredients
    ) {
        StringBuilder data =
                new StringBuilder();
        for (
                ScanBackIngredientCandidate ingredient
                : ingredients
        ) {
            data.append(
                    ingredient
                            .getNormalizedName()
            );
            data.append(
                    '|'
            );
        }
        return "OCR:"
                + sha256(
                data.toString()
        );
    }

    private static String sha256(
            String input
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );
            byte[] bytes =
                    digest.digest(
                            input.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
            StringBuilder hex =
                    new StringBuilder(
                            bytes.length * 2
                    );
            for (
                    byte b
                    : bytes
            ) {
                hex.append(
                        String.format(
                                Locale.ROOT,
                                "%02x",
                                b
                        )
                );
            }
            return hex.toString();
        } catch (
                NoSuchAlgorithmException e
        ) {
            return "UNAVAILABLE";
        }
    }

    private static final class Boundary {
        final int start;
        final int end;
        final String value;
        Boundary(
                int start,
                int end,
                String value
        ) {
            this.start =
                    start;
            this.end =
                    end;
            this.value =
                    value;
        }
    }
}
