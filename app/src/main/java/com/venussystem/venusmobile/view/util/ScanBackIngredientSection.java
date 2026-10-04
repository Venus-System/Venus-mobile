package com.venussystem.venusmobile.view.util;

import android.util.Log;
import com.venussystem.venusmobile.domain.scan.ScanIngredientPolicy;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanBackInciVocabulary.contarSinaisINCI;
import static com.venussystem.venusmobile.view.util.ScanBackInciVocabulary.linhaComecaComInciConhecido;
import static com.venussystem.venusmobile.view.util.ScanBackRules.ADDRESS_INLINE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CEP_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CNPJ_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.COMMON_INCI_BOUNDARIES;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CONTACT_CONTEXT_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMAIL_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMPTY;
import static com.venussystem.venusmobile.view.util.ScanBackRules.INGREDIENT_HEADINGS;
import static com.venussystem.venusmobile.view.util.ScanBackRules.INLINE_COMPANY_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.PHONE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.REGISTRATION_MARKER_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.STREET_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.TAG;
import static com.venussystem.venusmobile.view.util.ScanBackRules.URL_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackText.HeadingMatch;
import static com.venussystem.venusmobile.view.util.ScanBackText.compactarEspacos;
import static com.venussystem.venusmobile.view.util.ScanBackText.encontrarHeading;
import static com.venussystem.venusmobile.view.util.ScanBackText.indexOfMarkerWithBoundary;
import static com.venussystem.venusmobile.view.util.ScanBackText.levenshtein;
import static com.venussystem.venusmobile.view.util.ScanBackText.limparLinhaIngredientes;
import static com.venussystem.venusmobile.view.util.ScanBackText.limparPontosFinais;
import static com.venussystem.venusmobile.view.util.ScanBackText.limparRestoHeading;
import static com.venussystem.venusmobile.view.util.ScanBackText.localizarIndiceAproximadoOriginal;
import static com.venussystem.venusmobile.view.util.ScanBackText.localizarIndiceOriginalPorRegex;
import static com.venussystem.venusmobile.view.util.ScanBackText.localizarIndiceOriginalPorTextoComFronteira;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizarOCRBasico;
import static com.venussystem.venusmobile.view.util.ScanBackText.pareceAdministrativoForte;
import static com.venussystem.venusmobile.view.util.ScanBackText.safeSubstring;
import static com.venussystem.venusmobile.view.util.ScanBackText.substringSafe;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackIngredientSection {
    private ScanBackIngredientSection() { }

    /**
     * Marcadores fortes usados especificamente durante o fechamento
     * da seção de ingredientes.
     *
     * Mantemos uma lista separada para deixar explícita a intenção
     * desta etapa do parser.
     */
    private static final String[] STRONG_INGREDIENT_SECTION_STOPS = {
            "LOT",
            "CONTEUDO",
            "PESO LIQUIDO",
            "VOLUME",
            "FABRICADO POR",
            "FABRICADO",
            "FABRICANTE",
            "MANUFACTURER",
            "MANUFACTURED BY",
            "MANUFACTURED",
            "PRODUZIDO POR",
            "PRODUZIDO",
            "MANUFACTURADO POR",
            "ELABORADO POR",
            "ELABORADO",
            "IMPORTADO POR",
            "IMPORTADO",
            "IMPORTADOR",
            "IMPORTED BY",
            "IMPORTED",
            "DISTRIBUIDO POR",
            "DISTRIBUIDO",
            "DISTRIBUIDORA",
            "DISTRIBUTOR",
            "DISTRIBUTED BY",
            "CNPJ",
            "CEP",
            "AUTFUNC",
            "AUT FUNC",
            "AUT. FUNC",
            "AUTORIZACAO",
            "REGISTRO ANVISA",
            "NUMERO DE REGISTRO",
            "PROCESSO ANVISA",
            "CODIGO DE BARRAS",
            "CODIGO EAN",
            "EAN",
            "GTIN",
            "LOTE",
            "LQTE",
            "L0TE",
            "BATCH",
            "VALIDADE",
            "VENCIMENTO",
            "EXPIRY",
            "MODO DE USO",
            "MODO DE USAR",
            "MODO DE EMPLEO",
            "INSTRUCOES DE USO",
            "COMO USAR",
            "COMO UTILIZAR",
            "HOW TO USE",
            "DIRECTIONS FOR USE",
            "PRECAUCOES",
            "PRECAUCAO",
            "PRECAUTIONS",
            "CAUTIONS",
            "CUIDADOS",
            "CUIDADO",
            "ADVERTENCIA",
            "ADVERTENCIAS",
            "WARNING",
            "WARNINGS",
            "SAC",
            "ATENDIMENTO AO CONSUMIDOR",
            "CENTRAL DE ATENDIMENTO",
            "WHATSAPP",
            "QUESTIONS",
            "QUESTION",
            "MAIL SUPPORT",
            "MAIL SUPOR",
            "CUSTOMER SERVICE",
            "ATENDIMENTO AO CONSUMIDOR",
            "CONTACT",
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA BRASILEIRO",
            "NDUSTRIA BRASILEIRA",
            "INDUSTRA BRASILEIRA",
            "INDUSTRIA BRASILENA",
            "PAIS DE ORIGEM",
            "MADE IN",
            "ORIGIN"
    };

    private static final Pattern CITY_STATE_PATTERN =
            Pattern.compile(
                    "(?i)\\b[A-ZÁÉÍÓÚÃÕÇ]{3,}"
                            + "(?:\\s+[A-ZÁÉÍÓÚÃÕÇ]{2,}){0,6}"
                            + "\\s*/\\s*[A-Z]{2}\\b"
            );

    private static final Pattern COMPANY_REGISTRATION_FRAGMENT_PATTERN =
            Pattern.compile(
                    "(?i)(?<!\\d)\\d{2,4}\\s*/\\s*[A-Z0-9]{3,8}"
            );

    private static final Pattern ORIGIN_INLINE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:HECHO\\s+EN|FEITO\\s+NO|MADE\\s+IN|"
                            + "FABRICADO\\s+NO|ORIGEM)\\b"
            );

    private static final Pattern INGREDIENT_HEADING_PREFIX_OCR_PATTERN =
            Pattern.compile(
                    "(?i)^(?:N?GREDIENTES(?:INGREDIENTS|INGREDIENTES)?|"
                            + "INGREDIENTES(?:INGREDIENTS)?|"
                            + "INGREDIENTS(?:INGREDIENTES)?)"
                            + "\\s*[:;.,#\\-]*\\s*"
            );

    /*
     * Limite de segurança.
     *
     * Não significa que uma composição real necessariamente tenha
     * este tamanho. É apenas uma proteção contra OCR contaminado ou
     * uma captura em que nenhum marcador administrativo foi reconhecido.
     */
    private static final int MAX_INGREDIENT_SECTION_LINES =
            64;

    /*
     * O OCR espacial pode intercalar uma coluna de advertências/marketing
     * no meio da composição. Depois de uma barreira desse tipo, permitimos
     * recuperar no máximo duas continuações com sinais fortes de INCI.
     * Barreiras de empresa, endereço e registro continuam definitivas.
     */
    private static final int MAX_INGREDIENT_SECTION_RECOVERIES = 2;
    private static final int INGREDIENT_CONTINUATION_LOOKAHEAD = 24;

    /*
     * ================================================================
     * INGREDIENTES
     * ================================================================
     */
    static IngredientSection localizarSecaoIngredientes(
            List<String> lines
    ) {
        int start = -1;
        int headingEndColumn = 0;
        String heading = EMPTY;
        boolean detectedByContent = false;
        /*
         * ============================================================
         * 1) TENTATIVA NORMAL
         * ============================================================
         */
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            String line =
                    lines.get(i);
            HeadingMatch match =
                    encontrarHeadingIngredientesRobusto(
                            line
                    );
            if (match != null) {
                start =
                        i;
                headingEndColumn =
                        Math.min(
                                line.length(),
                                match.endInOriginal
                        );
                heading =
                        match.term;
                break;
            }
        }
        /*
         * ============================================================
         * 2) FALLBACK POR CONTEÚDO INCI
         * ============================================================
         *
         * Caso o OCR destrua completamente "INGREDIENTES".
         *
         * Exemplo real:
         *
         * UENTES: BUTANE: ISOBUTANE: PROPANE...
         */
        if (start < 0) {
            for (
                    int i = 0;
                    i < lines.size();
                    i++
            ) {
                if (
                        linhaPareceInicioDeIngredientes(
                                lines,
                                i
                        )
                ) {
                    start =
                            i;
                    headingEndColumn =
                            0;
                    heading =
                            "INCI_CONTENT_FALLBACK";
                    detectedByContent =
                            true;
                    break;
                }
            }
        }
        if (start < 0) {
            return new IngredientSection(
                    false,
                    -1,
                    -1,
                    EMPTY,
                    "NOT_FOUND"
            );
        }
        List<String> collected =
                new ArrayList<>();
        /*
         * ============================================================
         * PRIMEIRA LINHA
         * ============================================================
         */
        String firstLine =
                lines.get(start);
        String firstRemainder;
        if (detectedByContent) {
            /*
             * Quando o heading não foi reconhecido, tentamos cortar
             * tudo antes do primeiro INCI conhecido.
             */
            firstRemainder =
                    cortarPrefixoAntesDoPrimeiroINCI(
                            firstLine
                    );
            firstRemainder =
                    removerPrefixoHeadingIngredientesOCR(
                            firstRemainder
                    );
        } else {
            firstRemainder =
                    substringSafe(
                            firstLine,
                            headingEndColumn
                    );
            firstRemainder =
                    limparRestoHeading(
                            firstRemainder
                    );
        }
        String firstStopReason =
                null;
        boolean firstLineRecovered = false;
        if (
                !firstRemainder.isEmpty()
        ) {
            /*
             * A primeira linha é um caso especial: quando o OCR coloca
             * \"INGREDIENTES: ... MADE IN BRASIL\" ou \"... Ltda. Av\"
             * na mesma linha do heading, o loop das linhas seguintes não
             * chega a executar detectarFimIngredientes().
             *
             * Reutilizamos a mesma lógica aqui, sem criar um segundo
             * parser, para manter o comportamento atual.
             */
            List<String> seedContent =
                    Collections.singletonList(
                            firstRemainder
                    );
            StopMatch firstStop =
                    detectarFimIngredientes(
                            firstRemainder,
                            seedContent
                    );
            if (firstStop != null) {
                String prefix =
                        extrairPrefixoAntesDoStop(
                                firstRemainder,
                                firstStop
                        );
                firstRemainder =
                        prefix;
                if (isRecoverableIngredientBarrier(firstStop)
                        && localizarContinuacaoIngredientes(
                        lines,
                        start + 1
                ) >= 0) {
                    /*
                     * O heading pode ter sido seguido por uma coluna de
                     * marketing. Mantemos o prefixo útil e retomamos quando
                     * aparecer uma sequência com sinais de ingredientes.
                     */
                    firstStopReason = null;
                    firstLineRecovered = true;
                } else {
                    firstStopReason =
                            firstStop.reason;
                }
            }
            if (!firstRemainder.isEmpty()) {
                collected.add(
                        limparLinhaIngredientes(
                                firstRemainder
                        )
                );
            }
        }
        int end =
                start;
        String stopReason;
        if (firstStopReason != null) {
            stopReason = firstStopReason;
        } else if (firstLineRecovered) {
            stopReason = "RECOVERED_AFTER_SAFETY_OR_MARKETING";
        } else {
            stopReason = "END_OF_TEXT";
        }
        int recoveryCount =
                firstLineRecovered ? 1 : 0;
        /*
         * ============================================================
         * DEMAIS LINHAS
         * ============================================================
         */
        for (
                int i = start + 1;
                firstStopReason == null && i < lines.size();
                i++
        ) {
            if (
                    i - start
                            > MAX_INGREDIENT_SECTION_LINES
            ) {
                stopReason =
                        "MAX_INGREDIENT_SECTION_LINES";
                end =
                        i - 1;
                break;
            }
            String line =
                    lines.get(i);
            if (
                    line == null
                            || line.trim().isEmpty()
            ) {
                continue;
            }
            // A terminal period plus an entirely administrative/code suffix is
            // evidence of the section ending. A code in the middle is retained.
            if (!collected.isEmpty()
                    && collected.get(collected.size() - 1).trim().endsWith(".")
                    && isPackagingSuffix(lines, i)) {
                stopReason = "PACKAGING_TAIL";
                Log.d(TAG, "INGREDIENT_SECTION_STOP reason=PACKAGING_TAIL line=" + i);
                break;
            }
            StopMatch stop =
                    detectarFimIngredientes(
                            line,
                            collected
                    );
            if (stop != null) {
                String prefix =
                        extrairPrefixoAntesDoStop(
                                line,
                                stop
                        );
                if (!prefix.isEmpty()) {
                    collected.add(
                            limparLinhaIngredientes(
                                    prefix
                            )
                    );
                }
                int continuation =
                        recoveryCount < MAX_INGREDIENT_SECTION_RECOVERIES
                                && isRecoverableIngredientBarrier(stop)
                                ? localizarContinuacaoIngredientes(
                                lines,
                                i + 1
                        )
                                : -1;
                if (continuation >= 0) {
                    recoveryCount++;
                    end = i;
                    /*
                     * Ignora apenas o trecho administrativo intercalado.
                     * A linha de continuação volta ao fluxo normal e ainda
                     * passa por todas as barreiras existentes.
                     */
                    i = continuation - 1;
                    stopReason = "RECOVERED_AFTER_" + stop.reason;
                    continue;
                }
                stopReason =
                        stop.reason;
                end =
                        i;
                break;
            }
            String clean =
                    limparLinhaIngredientes(
                            line
                    );
            if (!clean.isEmpty()) {
                collected.add(
                        clean
                );
                end =
                        i;
            }
        }
        String raw =
                compactarEspacos(
                        String.join(
                                " ",
                                collected
                        )
                );
        raw =
                limparPontosFinais(
                        raw
                );
        Log.d(
                TAG,
                "Secao ingredientes: heading="
                        + heading
                        + " start="
                        + start
                        + " end="
                        + end
                        + " found=true stop="
                        + stopReason
                        + " fallback="
                        + detectedByContent
        );
        return new IngredientSection(
                true,
                start,
                end,
                raw,
                stopReason
        );
    }

    private static boolean isRecoverableIngredientBarrier(StopMatch stop) {
        return stop != null
                && "SAFETY_OR_MARKETING".equals(stop.reason);
    }

    /**
     * Procura uma nova sequência de linhas que pareça composição INCI após
     * uma barreira de segurança/marketing. Exige dois sinais consecutivos ou
     * dois sinais na mesma linha para não reabrir a seção por acaso.
     */
    private static int localizarContinuacaoIngredientes(
            List<String> lines,
            int fromIndex
    ) {
        if (lines == null || fromIndex < 0 || fromIndex >= lines.size()) {
            return -1;
        }
        int runStart = -1;
        int runSignals = 0;
        int limit = Math.min(
                lines.size(),
                fromIndex + INGREDIENT_CONTINUATION_LOOKAHEAD
        );
        for (int i = fromIndex; i < limit; i++) {
            String normalized = normalizar(lines.get(i));
            if (normalized.isEmpty()) continue;
            if (pareceAdministrativoForte(normalized)) {
                runStart = -1;
                runSignals = 0;
                continue;
            }
            int signals = contarSinaisINCI(normalized);
            if (signals >= 2) {
                return i;
            }
            if (signals > 0) {
                if (runStart < 0) runStart = i;
                runSignals++;
                if (runSignals >= 2) return runStart;
            } else {
                runStart = -1;
                runSignals = 0;
            }
        }
        return -1;
    }

    private static boolean isPackagingSuffix(List<String> lines, int start) {
        for (int i = start; i < lines.size(); i++) {
            if (lines.get(i) == null || lines.get(i).trim().isEmpty()) continue;
            if (!ScanIngredientPolicy.isPackagingTailLine(lines.get(i))) return false;
        }
        return true;
    }

    static HeadingMatch encontrarHeadingIngredientesRobusto(
            String line
    ) {
        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return null;
        }
        String normalized =
                normalizar(
                        line
                );
        /*
         * 1) Correspondência exata.
         */
        HeadingMatch exact =
                encontrarHeading(
                        normalized,
                        INGREDIENT_HEADINGS,
                        false
                );
        if (exact != null) {
            return exact;
        }
        /*
         * 2) Correção de confusões comuns do OCR.
         *
         * Só usamos isso para heading.
         */
        String ocrAdjusted =
                normalizarHeadingOCR(
                        normalized
                );
        exact =
                encontrarHeading(
                        ocrAdjusted,
                        INGREDIENT_HEADINGS,
                        false
                );
        if (exact != null) {
            return exact;
        }
        /*
         * 3) Fuzzy mais tolerante somente para headings.
         */
        String[] tokens =
                normalized.split(
                        "\\s+"
                );
        for (
                String token
                : tokens
        ) {
            // Fuzzy headings must be at the start; punctuation is not part of the word.
            if (!token.equals(tokens[0])) break;
            token = token.replaceAll("^[^A-Z]+|[^A-Z]+$", "");
            if (
                    token.length() < 6
            ) {
                continue;
            }
            for (
                    String heading
                    : INGREDIENT_HEADINGS
            ) {
                String h =
                        normalizar(
                                heading
                        );
                /*
                 * Só fazemos fuzzy em headings de uma palavra.
                 */
                if (
                        h.contains(" ")
                ) {
                    continue;
                }
                int distance =
                        levenshtein(
                                token,
                                h
                        );
                int allowed =
                        h.length() >= 10
                                ? 3
                                : 2;
                if (
                        distance <= allowed
                ) {
                    int idx =
                            normalized.indexOf(
                                    token
                            );
                    if (idx >= 0) {
                        return new HeadingMatch(
                                heading,
                                idx + token.length()
                        );
                    }
                }
            }
        }
        return null;
    }

    private static String normalizarHeadingOCR(
            String value
    ) {
        if (value == null) {
            return EMPTY;
        }
        /*
         * Essas substituições só são aplicadas na tentativa
         * de reconhecer o heading.
         */
        return value
                .replace(
                        '0',
                        'O'
                )
                .replace(
                        '1',
                        'I'
                )
                .replace(
                        '5',
                        'S'
                )
                .replace(
                        '8',
                        'B'
                )
                .replaceAll(
                        "[^A-Z ]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static boolean linhaPareceInicioDeIngredientes(
            List<String> lines,
            int index
    ) {
        if (
                lines == null
                        || index < 0
                        || index >= lines.size()
        ) {
            return false;
        }
        String current =
                normalizar(
                        lines.get(index)
                );
        if (current.isEmpty()) {
            return false;
        }
        /*
         * Nunca inicia composição em linha claramente administrativa.
         */
        if (
                pareceAdministrativoForte(
                        current
                )
        ) {
            return false;
        }
        int currentSignals =
                contarSinaisINCI(
                        current
                );
        // Look-ahead must not pull a warning/marketing line into the section.
        if (currentSignals == 0) return false;
        /*
         * Caso muito forte:
         *
         * BUTANE, ISOBUTANE, PROPANE
         */
        if (
                currentSignals >= 2
        ) {
            return true;
        }
        /*
         * Caso a composição esteja distribuída:
         *
         * AQUA
         * GLYCERIN
         * PARFUM
         */
        int consecutiveSignals =
                currentSignals > 0
                        ? 1
                        : 0;
        for (
                int i = index + 1;
                i < Math.min(
                        lines.size(),
                        index + 4
                );
                i++
        ) {
            String next =
                    normalizar(
                            lines.get(i)
                    );
            if (
                    next.isEmpty()
            ) {
                continue;
            }
            if (
                    pareceAdministrativoForte(
                            next
                    )
            ) {
                break;
            }
            int signals =
                    contarSinaisINCI(
                            next
                    );
            if (
                    signals > 0
            ) {
                consecutiveSignals++;
            } else {
                break;
            }
        }
        return consecutiveSignals >= 2;
    }

    private static String cortarPrefixoAntesDoPrimeiroINCI(
            String line
    ) {
        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return EMPTY;
        }
        String normalized =
                normalizar(
                        line
                );
        int bestIndex =
                Integer.MAX_VALUE;
        String bestTerm =
                null;
        /*
         * Primeiro procura INCI conhecido.
         */
        for (
                String known
                : COMMON_INCI_BOUNDARIES
        ) {
            String k =
                    normalizar(
                            known
                    );
            int index =
                    indexOfMarkerWithBoundary(
                            normalized,
                            k
                    );
            if (
                    index >= 0
                            && index < bestIndex
            ) {
                bestIndex =
                        index;
                bestTerm =
                        known;
            }
        }
        if (
                bestIndex
                        != Integer.MAX_VALUE
        ) {
            /*
             * Precisamos retornar uma posição correspondente
             * na linha original.
             */
            int originalIndex =
                    localizarIndiceOriginalPorTextoComFronteira(
                            line,
                            bestTerm
                    );
            if (originalIndex < 0) {
                originalIndex =
                        localizarIndiceAproximadoOriginal(
                                line,
                                bestTerm
                        );
            }
            if (
                    originalIndex >= 0
                            && originalIndex < line.length()
            ) {
                return line.substring(
                        originalIndex
                ).trim();
            }
        }
        /*
         * Caso não tenhamos encontrado INCI conhecido,
         * simplesmente preservamos a linha inteira.
         */
        return removerPrefixoHeadingIngredientesOCR(
                line.trim()
        );
    }

    private static String removerPrefixoHeadingIngredientesOCR(
            String line
    ) {
        if (line == null || line.trim().isEmpty()) {
            return EMPTY;
        }
        String value = normalizarOCRBasico(line).trim();
        Matcher matcher = INGREDIENT_HEADING_PREFIX_OCR_PATTERN.matcher(value);
        if (matcher.find()) {
            return value.substring(matcher.end()).trim();
        }
        value = value.replaceFirst(
                "(?i)^N?GREDIENTESINGREDIENTS\\s*[:;.,#\\-]*\\s*",
                EMPTY
        );
        value = value.replaceFirst(
                "(?i)^INGREDIENTESINGREDIENTS\\s*[:;.,#\\-]*\\s*",
                EMPTY
        );
        return value.trim();
    }

    static StopMatch detectarFimIngredientes(
            String line,
            List<String> collected
    ) {
        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return null;
        }
        String normalized =
                normalizar(
                        line
                );
        if (normalized.isEmpty()) {
            return null;
        }
        Matcher safety = Pattern.compile(
                "(?i)\\b(?:PROCURAR\\s+ORIENTA[ÇC][ÃA]O\\s+M[EÉ]DICA|"
                        + "N[ÃA]O\\s+CONT[EÉ]M\\s+CFC|(?:MAIS|M[AÁ]S)\\s+VENDIDO|"
                        + "MERCADO\\s+DE\\s+DESODORANTES|ALERGIA|PRURIDO|"
                        + "IRRITA[ÇC][ÃA]O|VIDE\\s+FUNDO)\\b").matcher(line);
        if (safety.find()) {
            String prefix = line.substring(0, safety.start());
            StopMatch explicitSafety = encontrarPrimeiroStop(line, STRONG_INGREDIENT_SECTION_STOPS);
            int start = explicitSafety == null ? safety.start()
                    : Math.min(safety.start(), explicitSafety.positionInLine);
            if (!linhaComecaComInciConhecido(prefix)
                    && encontrarHeadingIngredientesRobusto(prefix) == null) start = 0;
            return new StopMatch("SAFETY_OR_MARKETING", start);
        }
        /*
         * ============================================================
         * 1) MARCADORES ADMINISTRATIVOS EXPLÍCITOS
         * ============================================================
         */
        StopMatch explicit =
                encontrarPrimeiroStop(
                        line,
                        STRONG_INGREDIENT_SECTION_STOPS
                );
        if (explicit != null) {
            if (
                    jaTemConteudoIngrediente(
                            collected
                    )
                            || ehStopSempreForte(
                            explicit.reason
                    )
            ) {
                return explicit;
            }
        }
        /*
         * ============================================================
         * 2) ORIGEM DENTRO DA MESMA LINHA
         * ============================================================
         *
         * Exemplo real:
         *
         * ... PEEL OIL (CITRUS LIMON) ...
         * - São José dos Pinhais/PR - Brasil
         * ... Made in Brasil
         */
        Matcher origin =
                ORIGIN_INLINE_PATTERN.matcher(
                        line
                );
        if (origin.find()) {
            return new StopMatch(
                    origin.group(),
                    origin.start()
            );
        }
        /*
         * ============================================================
         * 3) ENDEREÇO DENTRO DA MESMA LINHA
         * ============================================================
         *
         * Exemplo:
         *
         * ... Ltda., Av. Rui Barbosa, 007/0001-57
         */
        Matcher address =
                ADDRESS_INLINE_PATTERN.matcher(
                        line
                );
        if (
                address.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {
            int cut =
                    localizarInicioEmpresaAntesDaBarreira(
                            line,
                            address.start()
                    );
            if (cut >= 0) {
                return new StopMatch(
                        "COMPANY_INLINE",
                        cut
                );
            }
            return new StopMatch(
                    address.group(),
                    address.start()
            );
        }
        /*
         * ============================================================
         * 4) EMPRESA COLADA AO FINAL DA COMPOSIÇÃO
         * ============================================================
         *
         * Caso o OCR não preserve nem mesmo o endereço, ainda podemos
         * encontrar algo como:
         *
         * ... PEEL OIL cial Farmacêutica Ltda
         *
         * O sufixo empresarial é usado apenas como barreira final.
         */
        Matcher inlineCompany =
                INLINE_COMPANY_PATTERN.matcher(
                        line
                );
        if (
                inlineCompany.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {
            return new StopMatch(
                    "COMPANY_INLINE",
                    inlineCompany.start()
            );
        }
        /*
         * ============================================================
         * 5) MUNICÍPIO / UF
         * ============================================================
         *
         * Exemplo:
         *
         * São José dos Pinhais/PR
         */
        Matcher cityState =
                CITY_STATE_PATTERN.matcher(
                        line
                );
        if (
                cityState.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {
            return new StopMatch(
                    "CITY_STATE",
                    cityState.start()
            );
        }
        /*
         * ============================================================
         * 5) FRAGMENTO DE CNPJ
         * ============================================================
         */
        Matcher companyRegistration =
                COMPANY_REGISTRATION_FRAGMENT_PATTERN.matcher(
                        line
                );
        if (
                companyRegistration.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {
            return new StopMatch(
                    "COMPANY_REGISTRATION",
                    companyRegistration.start()
            );
        }
        /*
         * ============================================================
         * 6) REGISTRO / PROCESSO
         * ============================================================
         */
        Matcher registrationMarker =
                REGISTRATION_MARKER_PATTERN.matcher(
                        normalized
                );
        if (registrationMarker.find()) {
            return new StopMatch(
                    registrationMarker.group(),
                    registrationMarker.start()
            );
        }
        /*
         * ============================================================
         * 7) CNPJ / CEP
         * ============================================================
         */
        Matcher cnpj =
                CNPJ_PATTERN.matcher(
                        line
                );
        if (cnpj.find()) {
            return new StopMatch(
                    "CNPJ",
                    0
            );
        }
        Matcher cep =
                CEP_PATTERN.matcher(
                        line
                );
        if (cep.find()) {
            return new StopMatch(
                    "CEP",
                    0
            );
        }
        /*
         * ============================================================
         * 8) CONTATO DEFORMADO POR OCR
         * ============================================================
         */
        Matcher contactContext = CONTACT_CONTEXT_PATTERN.matcher(line);
        if (contactContext.find() && jaTemConteudoIngrediente(collected)) {
            return new StopMatch("CONTACT_TEXT", contactContext.start());
        }
        /*
         * ============================================================
         * 9) TELEFONE / EMAIL / URL
         * ============================================================
         */
        Matcher phone =
                PHONE_PATTERN.matcher(
                        line
                );
        if (phone.find()) {
            return new StopMatch(
                    "PHONE",
                    0
            );
        }
        Matcher email =
                EMAIL_PATTERN.matcher(
                        line
                );
        if (email.find()) {
            return new StopMatch(
                    "EMAIL",
                    0
            );
        }
        Matcher url =
                URL_PATTERN.matcher(
                        line
                );
        if (url.find()) {
            int start = url.start();
            Matcher label = Pattern.compile("(?i)\\b(?:SITE|WEBSITE|ACESSE|VISITE|VISIT)\\s*:?\\s*$")
                    .matcher(line.substring(0, start));
            if (label.find()) start = label.start();
            return new StopMatch("URL", start);
        }
        /*
         * ============================================================
         * 9) ENDEREÇO COM NÚMERO
         * ============================================================
         */
        Matcher street =
                STREET_PATTERN.matcher(
                        line
                );
        if (
                street.find()
                        && (
                        normalized.matches(
                                ".*\\d{1,6}.*"
                        )
                                || CEP_PATTERN
                                .matcher(
                                        normalized
                                )
                                .find()
                )
        ) {
            return new StopMatch(
                    street.group(),
                    street.start()
            );
        }
        return null;
    }

    private static boolean ehStopSempreForte(
            String reason
    ) {
        if (reason == null) {
            return false;
        }
        String n =
                normalizar(
                        reason
                );
        return "CNPJ".equals(n)
                || "CEP".equals(n)
                || "AUTFUNC".equals(n)
                || "AUT FUNC".equals(n)
                || "AUTORIZACAO".equals(n)
                || "REGISTRO ANVISA".equals(n)
                || "NUMERO DE REGISTRO".equals(n)
                || "CODIGO DE BARRAS".equals(n)
                || "GTIN".equals(n)
                || "EAN".equals(n)
                || "LOTE".equals(n)
                || "BATCH".equals(n)
                || "VALIDADE".equals(n)
                || "VENCIMENTO".equals(n)
                || "SAC".equals(n)
                || "ATENDIMENTO AO CONSUMIDOR".equals(n)
                || "WHATSAPP".equals(n)
                || "PHONE".equals(n)
                || "EMAIL".equals(n)
                || "URL".equals(n);
    }

    private static StopMatch encontrarPrimeiroStop(
            String originalLine,
            String[] markers
    ) {
        String normalized =
                normalizar(
                        originalLine
                );
        int bestIndex =
                Integer.MAX_VALUE;
        String bestMarker =
                null;
        for (
                String marker
                : markers
        ) {
            if (
                    marker == null
                            || marker.trim().isEmpty()
            ) {
                continue;
            }
            String normalizedMarker =
                    normalizar(
                            marker
                    );
            int index =
                    indexOfMarkerWithBoundary(
                            normalized,
                            normalizedMarker
                    );
            if (
                    index >= 0
                            && index < bestIndex
            ) {
                bestIndex =
                        index;
                bestMarker =
                        marker;
            }
        }
        if (bestMarker == null) {
            return null;
        }
        return new StopMatch(
                bestMarker,
                bestIndex
        );
    }

    @NonNull
    static String extrairPrefixoAntesDoStop(
            String line,
            StopMatch stop
    ) {
        if (
                line == null
                        || line.isEmpty()
        ) {
            return EMPTY;
        }
        if (stop == null) {
            return line.trim();
        }
        /*
         * Elementos administrativos que começam a linha.
         */
        if (
                "PHONE".equals(stop.reason)
                        || "EMAIL".equals(stop.reason)
                        || "CNPJ".equals(stop.reason)
                        || "CEP".equals(stop.reason)
        ) {
            return EMPTY;
        }
        if ("CONTACT_TEXT".equals(stop.reason)) {
            int originalIndex = localizarIndiceOriginalPorRegex(
                    line,
                    CONTACT_CONTEXT_PATTERN
            );
            if (originalIndex <= 0) {
                return EMPTY;
            }
            return safeSubstring(line, 0, originalIndex).trim();
        }
        /*
         * Para as barreiras encontradas diretamente na linha original,
         * usamos a posição recebida sem fazer uma segunda conversão.
         * Isso evita o vazamento clássico: \"... Ltda. Av\".
         */
        if (
                "URL".equals(stop.reason)
                        || "SAFETY_OR_MARKETING".equals(stop.reason)
                        || "COMPANY_INLINE".equals(stop.reason)
                        || "CITY_STATE".equals(stop.reason)
                        || "COMPANY_REGISTRATION".equals(stop.reason)
        ) {
            int originalIndex;
            if ("CITY_STATE".equals(stop.reason)) {
                originalIndex =
                        localizarIndiceOriginalPorRegex(
                                line,
                                CITY_STATE_PATTERN
                        );
            } else if ("COMPANY_REGISTRATION".equals(stop.reason)) {
                originalIndex =
                        localizarIndiceOriginalPorRegex(
                                line,
                                COMPANY_REGISTRATION_FRAGMENT_PATTERN
                        );
            } else {
                originalIndex =
                        stop.positionInLine;
            }
            if (originalIndex <= 0) {
                return EMPTY;
            }
            return safeSubstring(
                    line,
                    0,
                    originalIndex
            ).trim();
        }
        /*
         * Origem e endereço devem ser localizados diretamente na linha
         * original, preservando exatamente onde o OCR colocou o marcador.
         */
        if (
                ORIGIN_INLINE_PATTERN
                        .matcher(line)
                        .find()
        ) {
            Matcher m =
                    ORIGIN_INLINE_PATTERN.matcher(
                            line
                    );
            if (m.find() && m.start() > 0) {
                return safeSubstring(
                        line,
                        0,
                        m.start()
                ).trim();
            }
        }
        if (
                ADDRESS_INLINE_PATTERN
                        .matcher(line)
                        .find()
        ) {
            Matcher m =
                    ADDRESS_INLINE_PATTERN.matcher(
                            line
                    );
            if (m.find() && m.start() > 0) {
                return safeSubstring(
                        line,
                        0,
                        m.start()
                ).trim();
            }
        }
        /*
         * Para markers administrativos explícitos, tente primeiro localizar
         * o marcador diretamente na linha original.
         *
         * Isso é importante quando o OCR/normalização altera espaços,
         * pontuação ou acentuação. A conversão por índice normalizado pode
         * parar alguns caracteres antes e deixar um fragmento como
         * "FABRI" dentro do último ingrediente.
         */
        int originalIndex =
                localizarIndiceOriginalPorTextoComFronteira(
                        line,
                        stop.reason
                );
        if (originalIndex < 0) {
            originalIndex =
                    localizarIndiceAproximadoOriginal(
                            line,
                            stop.reason
                    );
        }
        if (originalIndex <= 0) {
            return EMPTY;
        }
        return safeSubstring(
                line,
                0,
                originalIndex
        ).trim();
    }

    /**
     * Quando endereço e empresa aparecem colados na mesma linha, tenta
     * localizar o início do pequeno bloco empresarial antes do endereço.
     *
     * Exemplo alvo:
     *   ... PEEL OIL cial Farmacêuticaa Ltda. Av
     *
     * O corte é feito no início do match empresarial, não no \"Ltda\",
     * evitando que o fragmento \"cial Farmacêuticaa\" vire ingrediente.
     */
    private static int localizarInicioEmpresaAntesDaBarreira(
            String line,
            int barrierIndex
    ) {
        if (
                line == null
                        || barrierIndex <= 0
        ) {
            return -1;
        }
        String prefix =
                safeSubstring(
                        line,
                        0,
                        barrierIndex
                );
        Matcher company =
                INLINE_COMPANY_PATTERN.matcher(
                        prefix
                );
        int best =
                -1;
        while (company.find()) {
            if (company.end() <= barrierIndex) {
                best = company.start();
            }
        }
        return best;
    }

    private static boolean jaTemConteudoIngrediente(
            List<String> collected
    ) {
        if (
                collected == null
                        || collected.isEmpty()
        ) {
            return false;
        }
        String text =
                compactarEspacos(
                        String.join(
                                " ",
                                collected
                        )
                );
        return text.length() >= 5;
    }

    /*
     * ================================================================
     * ESTRUTURAS PRIVADAS
     * ================================================================
     */
    static final class IngredientSection {
        final boolean found;
        final int startIndex;
        final int endIndex;
        final String rawText;
        final String stopReason;
        IngredientSection(
                boolean found,
                int startIndex,
                int endIndex,
                String rawText,
                String stopReason
        ) {
            this.found =
                    found;
            this.startIndex =
                    startIndex;
            this.endIndex =
                    endIndex;
            this.rawText =
                    rawText == null
                            ? EMPTY
                            : rawText;
            this.stopReason =
                    stopReason == null
                            ? EMPTY
                            : stopReason;
        }
    }

    static final class StopMatch {
        final String reason;
        final int positionInLine;
        StopMatch(
                String reason,
                int positionInLine
        ) {
            this.reason =
                    reason;
            this.positionInLine =
                    positionInLine;
        }
    }
}
