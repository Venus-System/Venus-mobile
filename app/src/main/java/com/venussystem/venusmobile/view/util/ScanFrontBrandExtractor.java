package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static com.venussystem.venusmobile.view.util.ScanFrontText.adicionarUnico;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isDescritorAntiperspirante;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isDescritorAntitranspirante;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isLinhaDeRuidoForte;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isReferenciaGenericaCosmetica;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isSomenteNumero;
import static com.venussystem.venusmobile.view.util.ScanFrontText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanFrontText.possuiLetras;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CAPACIDADE_OCR_PREFIX_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CAPACIDADE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CONCENTRACAO_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.NOISE_WORDS;

/** Internal collaborator for ScanFrontExtractor; not a second scan entry point. */
final class ScanFrontBrandExtractor {
    private ScanFrontBrandExtractor() { }

    /*
     * =========================================================
     * TERMOS QUE NÃO DEVEM SER MARCA
     *
     * Eles continuam podendo ser candidatos de PRODUTO.
     * =========================================================
     */
    private static final Set<String> NON_BRAND_WORDS =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "EXTREME",
                            "MEN",
                            "MAN",
                            "WOMEN",
                            "WOMAN",
                            "MASCULINO",
                            "FEMININO",
                            "ADULTO",
                            "INFANTIL",
                            "KIDS",
                            "BABY",
                            "INVISIBLE",
                            "BLACK",
                            "WHITE",
                            "DRY",
                            "FRESH",
                            "SPORT",
                            "CLASSIC",
                            "ORIGINAL",
                            "PROTECAO",
                            "PROTECTION",
                            "PROTECCAO",
                            "PROTECCION",
                            "SECA",
                            "SECO",
                            "LONG",
                            "LASTING",
                            "PROLONGADA",
                            "PROLONGADO",
                            "PROLONGED",
                            "PERFUME",
                            "PERFUMACAO",
                            "SPRAY",
                            "AEROSOL",
                            "ROLL",
                            "ROLLON",
                            "CREME",
                            "LOCAO",
                            "GEL",
                            "ESPUMA",
                            "SABONETE",
                            "ANTITRANSPIRANTE",
                            "ANTITRANSPIRANT",
                            "ANTITRANSPIRAN",
                            "ANTITRANSPIRANI",
                            "ANTIPERSPIRANTE",
                            "ANTIPERSPIRANT",
                            "ANTIPERSPIRAN",
                            "ANTIPERSPIRANI",
                            "USO",
                            "EXTERNO",
                            "ATIVO",
                            "ATIVA",
                            "TESTADO",
                            "TESTADA",
                            "APROVADO",
                            "APROVADA",
                            "NOVA",
                            "NOVO",
                            "NEW",
                            "TECNOLOGIA",
                            "TECHNOLOGY",
                            "FORMULA",
                            "EVITA",
                            "PREVINE",
                            "PREVENTS",
                            "RESIDUOS",
                            "MANCHAS",
                            "STAINS",
                            "ROUPAS",
                            "CLOTHES",
                            "SUOR",
                            "SWEAT",
                            "ODOR",
                            "CONTRA",
                            "MANTER",
                            "AGITE",
                            "AGITAR",
                            "PRECAUCOES",
                            "PRECAUTIONS",
                            "CONTEM",
                            "BAIXO",
                            "POTENCIAL",
                            "SENSIVEIS",
                            "DIRETA",
                            "DIRETO",
                            "LUZ",
                            "SOLAR",
                            "FRESCO",
                            "INFLAMAVEL",
                            "NAO",
                            "USE",
                            "E",
                            "Y",
                            "AND"
                    )
            );

    /*
     * =========================================================
     * CONTINUAÇÕES DE MARCAS COMPOSTAS
     * =========================================================
     */
    private static final Set<String> BRAND_CONTINUATION_WORDS =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "SECRET",
                            "ROCHE",
                            "POSAY",
                            "BODY",
                            "SHOP",
                            "BOTICARIO",
                            "COSMETICS",
                            "COSMETICOS",
                            "BEAUTY",
                            "LAB",
                            "LABS",
                            "PARIS",
                            "LONDON",
                            "YORK",
                            "THE",
                            "DE",
                            "LA",
                            "LE",
                            "LES",
                            "AND",
                            "CO",
                            "COMPANY",
                            "PROFESSIONAL",
                            "PROFISSIONAL"
                    )
            );

    /*
     * =========================================================
     * EXTRAÇÃO DE MARCA
     * =========================================================
     */
    @NonNull
    static String extrairPossivelMarca(
            @NonNull String linha
    ) {
        String normalizada =
                normalizar(
                        linha
                );
        String[] tokens =
                normalizada.split(
                        "\\s+"
                );
        if (tokens.length == 0) {
            return "";
        }
        StringBuilder marca =
                new StringBuilder();
        for (String token :
                tokens) {
            if (token.isEmpty()) {
                continue;
            }
            /*
             * Ignora número no começo do OCR.
             */
            if (token.matches(
                    "\\d+"
            )) {
                if (marca.length() == 0) {
                    continue;
                }
                break;
            }
            /*
             * A partir daqui começou algo que claramente
             * parece produto/descritivo, não marca.
             */
            if (isBloqueadoComoMarca(
                    token
            )) {
                break;
            }
            if (token.length() < 2) {
                continue;
            }
            if (marca.length() > 0) {
                marca.append(' ');
            }
            marca.append(
                    token
            );
            if (marca.length() >= 30) {
                break;
            }
        }
        String resultado =
                marca
                        .toString()
                        .trim();
        if (!pareceMarca(
                resultado
        )) {
            return "";
        }
        return resultado;
    }

    /*
     * =========================================================
     * COMPLETAR MARCA
     * =========================================================
     *
     * Casos:
     *
     * VICTORIA
     * SECRET
     *
     * -> VICTORIA SECRET
     *
     * NATIVA
     * SPA
     *
     * -> NATIVA SPA
     *
     * Não é usado para decidir se a marca é verdadeira.
     * Apenas reconstrói texto.
     */
    @NonNull
    static String completarMarcaEmLinhas(
            @NonNull String marcaInicial,
            @NonNull List<String> linhas,
            int indiceInicial
    ) {
        String marca =
                normalizar(
                        marcaInicial
                );
        if (marca.isEmpty()) {
            return "";
        }
        String[] tokensIniciais =
                marca.split(
                        "\\s+"
                );
        if (tokensIniciais.length >= 3) {
            return marca;
        }
        int palavrasAdicionadas = 0;
        for (int i = indiceInicial + 1;
             i < linhas.size()
                     && palavrasAdicionadas < 2;
             i++) {
            String proximaLinha =
                    normalizar(
                            linhas.get(i)
                    );
            if (proximaLinha.isEmpty()) {
                continue;
            }
            if (proximaLinha.length() > 25) {
                break;
            }
            if (isLinhaDeRuidoForte(
                    proximaLinha
            )) {
                break;
            }
            if (CAPACIDADE_PATTERN
                    .matcher(
                            proximaLinha
                    )
                    .find()
                    || CAPACIDADE_OCR_PREFIX_PATTERN
                    .matcher(
                            proximaLinha
                    )
                    .find()) {
                break;
            }
            if (CONCENTRACAO_PATTERN
                    .matcher(
                            proximaLinha
                    )
                    .find()) {
                break;
            }
            if (proximaLinha.matches(
                    ".*\\d{1,3}H.*"
            )) {
                break;
            }
            String[] tokens =
                    proximaLinha.split(
                            "\\s+"
                    );
            if (tokens.length == 0
                    || tokens.length > 2) {
                break;
            }
            boolean linhaValida =
                    true;
            StringBuilder continuacao =
                    new StringBuilder();
            for (String token :
                    tokens) {
                if (token.isEmpty()) {
                    continue;
                }
                if (!BRAND_CONTINUATION_WORDS.contains(
                        token
                )) {
                    linhaValida = false;
                    break;
                }
                if (isBloqueadoComoMarca(
                        token
                )
                        && !ehConectorDeMarca(
                        token
                )) {
                    linhaValida = false;
                    break;
                }
                if (continuacao.length() > 0) {
                    continuacao.append(' ');
                }
                continuacao.append(
                        token
                );
            }
            if (!linhaValida
                    || continuacao.length() == 0) {
                break;
            }
            String candidata =
                    marca
                            + " "
                            + continuacao;
            if (!marcaPodeSerMarcaComposta(
                    candidata
            )) {
                break;
            }
            marca =
                    candidata;
            palavrasAdicionadas +=
                    tokens.length;
        }
        return marca.trim();
    }

    /*
     * =========================================================
     * MARCA COMPOSTA INICIADA POR FRAGMENTO
     * =========================================================
     *
     * Resolve:
     *
     * O
     * BOTICARIO
     *
     * LA
     * ROCHE
     * POSAY
     *
     * THE
     * BODY
     * SHOP
     */
    @NonNull
    static String extrairMarcaCompostaNasLinhas(
            @NonNull List<String> linhas,
            int indice
    ) {
        if (indice < 0
                || indice >= linhas.size()) {
            return "";
        }
        String atual =
                normalizar(
                        linhas.get(indice)
                );
        if (atual.isEmpty()) {
            return "";
        }
        String[] tokensAtuais =
                atual.split(
                        "\\s+"
                );
        /*
         * Caso já seja uma expressão de duas ou mais palavras,
         * ainda podemos verificar uma continuação conhecida.
         */
        if (tokensAtuais.length >= 2) {
            if (!todosPodemFazerParteDeMarca(
                    tokensAtuais
            )) {
                return "";
            }
            String resultado =
                    atual;
            int adicionadas = 0;
            for (int i = indice + 1;
                 i < linhas.size()
                         && adicionadas < 2;
                 i++) {
                String proxima =
                        normalizar(
                                linhas.get(i)
                        );
                if (proxima.isEmpty()) {
                    continue;
                }
                if (proxima.length() > 25) {
                    break;
                }
                String[] tokens =
                        proxima.split(
                                "\\s+"
                        );
                if (tokens.length != 1) {
                    break;
                }
                String token =
                        tokens[0];
                if (!BRAND_CONTINUATION_WORDS.contains(
                        token
                )) {
                    break;
                }
                if (isBloqueadoComoMarca(
                        token
                )
                        && !ehConectorDeMarca(
                        token
                )) {
                    break;
                }
                resultado =
                        resultado
                                + " "
                                + token;
                adicionadas++;
            }
            return resultado;
        }
        /*
         * -----------------------------------------------------
         * CASO DE UMA PALAVRA:
         *
         * VICTORIA
         * SECRET
         *
         * -----------------------------------------------------
         */
        if (tokensAtuais.length == 1
                && pareceMarca(atual)) {
            String completa =
                    completarMarcaEmLinhas(
                            atual,
                            linhas,
                            indice
                    );
            if (!completa.equals(atual)) {
                return completa;
            }
        }
        /*
         * -----------------------------------------------------
         * CASO DE PREFIXO:
         *
         * O
         * BOTICARIO
         *
         * LA
         * ROCHE
         * POSAY
         *
         * THE
         * BODY
         * SHOP
         * -----------------------------------------------------
         */
        if (!ehPrefixoDeMarca(atual)) {
            return "";
        }
        StringBuilder resultado =
                new StringBuilder(atual);
        int palavrasAdicionadas = 0;
        for (int i = indice + 1;
             i < linhas.size()
                     && palavrasAdicionadas < 3;
             i++) {
            String proxima =
                    normalizar(
                            linhas.get(i)
                    );
            if (proxima.isEmpty()) {
                continue;
            }
            if (proxima.length() > 25) {
                break;
            }
            if (isLinhaDeRuidoForte(
                    proxima
            )) {
                break;
            }
            if (CAPACIDADE_PATTERN
                    .matcher(
                            proxima
                    )
                    .find()
                    || CAPACIDADE_OCR_PREFIX_PATTERN
                    .matcher(
                            proxima
                    )
                    .find()) {
                break;
            }
            String[] tokens =
                    proxima.split(
                            "\\s+"
                    );
            /*
             * Para um fragmento inicial queremos uma palavra
             * por linha.
             */
            if (tokens.length != 1) {
                break;
            }
            String token =
                    tokens[0];
            if (isBloqueadoComoMarca(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {
                break;
            }
            /*
             * Se a palavra não é permitida como continuação,
             * paramos imediatamente.
             */
            if (!BRAND_CONTINUATION_WORDS.contains(
                    token
            )) {
                break;
            }
            resultado.append(' ')
                    .append(token);
            palavrasAdicionadas++;
        }
        String candidata =
                resultado
                        .toString()
                        .trim();
        /*
         * Uma marca composta válida precisa ter pelo menos
         * duas palavras.
         */
        if (candidata.equals(atual)) {
            return "";
        }
        if (!marcaPodeSerMarcaComposta(
                candidata
        )) {
            return "";
        }
        return candidata;
    }

    /*
     * =========================================================
     * VALIDAÇÃO DE MARCA COMPOSTA
     * =========================================================
     */
    private static boolean marcaPodeSerMarcaComposta(
            @NonNull String texto
    ) {
        String normalizada =
                normalizar(
                        texto
                );
        if (normalizada.length() < 4
                || normalizada.length() > 40) {
            return false;
        }
        String[] tokens =
                normalizada.split(
                        "\\s+"
                );
        if (tokens.length > 4) {
            return false;
        }
        for (String token :
                tokens) {
            if (token.length() < 2
                    && !ehConectorDeMarca(
                    token
            )) {
                return false;
            }
            /*
             * Variantes de produto não fazem parte da marca.
             *
             * Ex:
             *
             * ABOVE EXTREME
             *
             * não pode virar uma marca composta.
             */
            if (NON_BRAND_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {
                return false;
            }
            if (NOISE_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {
                return false;
            }
            if (token.matches(
                    "\\d+"
            )) {
                return false;
            }
            if (token.matches(
                    "\\d{1,3}H"
            )) {
                return false;
            }
        }
        return possuiLetras(
                normalizada
        );
    }

    private static boolean todosPodemFazerParteDeMarca(
            @NonNull String[] tokens
    ) {
        if (tokens.length == 0
                || tokens.length > 4) {
            return false;
        }
        for (String token :
                tokens) {
            if (token.isEmpty()) {
                return false;
            }
            if (NON_BRAND_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {
                return false;
            }
            if (NOISE_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {
                return false;
            }
            if (token.matches(
                    "\\d+"
            )
                    || token.matches(
                    "\\d{1,3}H"
            )) {
                return false;
            }
        }
        return true;
    }

    /*
     * =========================================================
     * PREFIXOS DE MARCAS
     * =========================================================
     */
    private static boolean ehPrefixoDeMarca(
            @NonNull String token
    ) {
        return token.equals("O")
                || token.equals("THE")
                || token.equals("LA")
                || token.equals("LE")
                || token.equals("DE");
    }

    /*
     * =========================================================
     * CONECTORES DE MARCA
     * =========================================================
     */
    private static boolean ehConectorDeMarca(
            @NonNull String token
    ) {
        return token.equals("O")
                || token.equals("THE")
                || token.equals("DE")
                || token.equals("LA")
                || token.equals("LE")
                || token.equals("LES")
                || token.equals("AND")
                || token.equals("CO");
    }

    /*
     * =========================================================
     * MARCA
     * =========================================================
     */
    private static boolean pareceMarca(
            String texto
    ) {
        if (texto == null) {
            return false;
        }
        String normalizada =
                normalizar(
                        texto
                );
        if (normalizada.length() < 4
                || normalizada.length() > 30) {
            return false;
        }
        if (isSomenteNumero(
                normalizada
        )) {
            return false;
        }
        if (!possuiLetras(
                normalizada
        )) {
            return false;
        }
        String[] tokens =
                normalizada.split(
                        "\\s+"
                );
        for (String token :
                tokens) {
            if (isBloqueadoComoMarca(
                    token
            )) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBloqueadoComoMarca(
            @NonNull String token
    ) {
        if (token.matches(
                "\\d{1,3}H"
        )) {
            return true;
        }
        if (NON_BRAND_WORDS.contains(
                token
        )) {
            return true;
        }
        if (NOISE_WORDS.contains(
                token
        )) {
            return true;
        }
        if (isReferenciaGenericaCosmetica(
                token
        )) {
            return true;
        }
        if (isDescritorAntitranspirante(
                token
        )) {
            return true;
        }
        return isDescritorAntiperspirante(
                token
        );
    }

    static void adicionarMarca(
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
        if (!pareceMarca(
                normalizado
        )
                && !marcaPodeSerMarcaComposta(
                normalizado
        )) {
            return;
        }
        adicionarUnico(
                destino,
                normalizado
        );
    }

    /*
     * =========================================================
     * LIMPEZA FINAL DA MARCA
     * =========================================================
     */
    static void limparMarcas(
            @NonNull List<String> marcas
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String marca :
                marcas) {
            if (marca == null) {
                continue;
            }
            String normalizado =
                    normalizar(
                            marca
                    );
            if (pareceMarca(
                    normalizado
            )
                    || marcaPodeSerMarcaComposta(
                    normalizado
            )) {
                adicionarUnico(
                        resultado,
                        normalizado
                );
            }
        }
        marcas.clear();
        marcas.addAll(
                resultado
        );
    }
}
