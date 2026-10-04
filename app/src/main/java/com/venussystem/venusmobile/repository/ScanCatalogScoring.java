package com.venussystem.venusmobile.repository;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static com.venussystem.venusmobile.repository.ScanCatalogText.adicionarUnico;
import static com.venussystem.venusmobile.repository.ScanCatalogText.isTermoFraco;
import static com.venussystem.venusmobile.repository.ScanCatalogText.melhorSimilaridadeOCR;
import static com.venussystem.venusmobile.repository.ScanCatalogText.normalizarCampo;
import static com.venussystem.venusmobile.repository.ScanCatalogText.tokensProdutoIdentidade;
import static com.venussystem.venusmobile.repository.ScanCatalogText.tokensSignificativos;

/** Internal collaborator for ScanProductMatchRepository; not a second scan entry point. */
final class ScanCatalogScoring {
    private ScanCatalogScoring() { }

    private static final double TOKEN_MATCH_THRESHOLD = 0.84;

    /**
     * Pontua um produto.
     *
     * A principal mudança é:
     *
     * ANTES:
     *
     *     muitos termos do OCR
     *     ---------------------
     *     score diluído
     *
     * AGORA:
     *
     *     quantos tokens RELEVANTES DO PRODUTO
     *     foram encontrados no OCR?
     *
     * Exemplo:
     *
     * OCR:
     *     CLINICAL
     *     CLEAN
     *     MEN
     *
     * Produto:
     *     REXONA CLINICAL CLEAN MEN
     *
     * Após remover a marca:
     *
     *     CLINICAL CLEAN MEN
     *
     * Os 3 tokens são encontrados.
     */
    static ProductEvidence pontuarProduto(
            @NonNull List<String> candidatos,
            @NonNull List<String> termosFortes,
            @NonNull List<String> frases,
            @NonNull String nomeProduto
    ) {
        Set<String> tokensAlvo =
                tokensProdutoIdentidade(
                        nomeProduto
                );
        if (tokensAlvo.isEmpty()) {
            return null;
        }
        /*
         * =========================================================
         * CASAMENTO DOS TOKENS DO PRODUTO
         * =========================================================
         *
         * Exemplo:
         *
         * Produto:
         * REXONA CLINICAL CLEAN MEN
         *
         * Após remover a marca:
         *
         * CLINICAL CLEAN MEN
         *
         * OCR:
         * CLINICAL
         * CLEAN
         * CREMAMEN
         *
         * Resultado:
         * CLINICAL -> exato
         * CLEAN    -> exato
         * MEN      -> não encontrado
         *
         * Isso representa 2 de 3 tokens identificadores.
         */
        int tokensAlvoCasados = 0;
        int tokensAlvoExatos = 0;
        double somaMelhoresTokensAlvo = 0.0;
        String melhorTermo = null;
        double melhorTermoScore = 0.0;
        /*
         * Tokens OCR disponíveis.
         */
        Set<String> tokensOCR =
                new LinkedHashSet<>();
        for (String candidato :
                candidatos) {
            tokensOCR.addAll(
                    tokensSignificativos(
                            candidato
                    )
            );
        }
        /*
         * Evita que o mesmo token OCR seja reutilizado
         * para casar vários tokens do produto.
         */
        Set<String> tokensOCRUsados =
                new LinkedHashSet<>();
        for (String tokenAlvo :
                tokensAlvo) {
            double melhorDoAlvo = 0.0;
            String melhorOCRDoAlvo = null;
            boolean melhorFoiExato = false;
            /*
             * PRIMEIRO: procura casamento EXATO.
             */
            for (String tokenOCR :
                    tokensOCR) {
                if (tokensOCRUsados.contains(
                        tokenOCR
                )) {
                    continue;
                }
                if (tokenOCR.equals(
                        tokenAlvo
                )) {
                    melhorDoAlvo = 1.0;
                    melhorOCRDoAlvo = tokenOCR;
                    melhorFoiExato = true;
                    break;
                }
            }
            /*
             * SEGUNDO: se não achou exato,
             * tenta similaridade/falha de OCR.
             */
            if (!melhorFoiExato) {
                for (String tokenOCR :
                        tokensOCR) {
                    if (tokensOCRUsados.contains(
                            tokenOCR
                    )) {
                        continue;
                    }
                    double score =
                            melhorSimilaridadeOCR(
                                    tokenOCR,
                                    tokenAlvo,
                                    false
                            );
                    if (score > melhorDoAlvo) {
                        melhorDoAlvo =
                                score;
                        melhorOCRDoAlvo =
                                tokenOCR;
                    }
                }
            }
            /*
             * Aceita o token quando o match é suficientemente forte.
             */
            if (melhorDoAlvo
                    >= TOKEN_MATCH_THRESHOLD) {
                tokensAlvoCasados++;
                somaMelhoresTokensAlvo +=
                        melhorDoAlvo;
                if (melhorFoiExato) {
                    tokensAlvoExatos++;
                }
                if (melhorOCRDoAlvo != null) {
                    tokensOCRUsados.add(
                            melhorOCRDoAlvo
                    );
                }
                if (melhorDoAlvo
                        > melhorTermoScore) {
                    melhorTermoScore =
                            melhorDoAlvo;
                    melhorTermo =
                            melhorOCRDoAlvo;
                }
            }
        }
        if (tokensAlvoCasados == 0) {
            return null;
        }
        /*
         * =========================================================
         * COBERTURAS
         * =========================================================
         */
        double targetCoverage =
                (double) tokensAlvoCasados
                        / tokensAlvo.size();
        double targetSimilarityAverage =
                somaMelhoresTokensAlvo
                        / tokensAlvo.size();
        double exactTargetCoverage =
                (double) tokensAlvoExatos
                        / tokensAlvo.size();
        /*
         * =========================================================
         * TERMOS FORTES DO OCR
         * =========================================================
         *
         * Depois de tornar palavras genéricas como DESODORANTE,
         * AEROSOL e ANTITRANSPIRANT fracas, elas deixam de
         * contaminar esta evidência.
         */
        int termosFortesCasados = 0;
        for (String termoOCR :
                termosFortes) {
            double melhorDoTermo = 0.0;
            for (String tokenAlvo :
                    tokensAlvo) {
                double score =
                        melhorSimilaridadeOCR(
                                termoOCR,
                                tokenAlvo,
                                false
                        );
                if (score > melhorDoTermo) {
                    melhorDoTermo = score;
                }
            }
            if (melhorDoTermo
                    >= TOKEN_MATCH_THRESHOLD) {
                termosFortesCasados++;
            }
        }
        /*
         * =========================================================
         * MELHOR FRASE
         * =========================================================
         */
        double melhorFraseScore = 0.0;
        String melhorFrase = null;
        for (String frase :
                frases) {
            if (frase == null
                    || frase.trim().isEmpty()) {
                continue;
            }
            int quantidadePalavras =
                    frase
                            .trim()
                            .split("\\s+")
                            .length;
            if (quantidadePalavras < 2) {
                continue;
            }
            double score =
                    scoreFraseContidaNoProduto(
                            frase,
                            nomeProduto
                    );
            if (score > melhorFraseScore) {
                melhorFraseScore =
                        score;
                melhorFrase =
                        frase;
            }
        }
        /*
         * =========================================================
         * SCORE BASE
         * =========================================================
         *
         * O foco agora é:
         *
         * 1. cobertura dos tokens do produto;
         * 2. similaridade;
         * 3. quantidade de tokens exatos;
         * 4. frase comercial.
         */
        double score =
                targetCoverage * 0.35
                        + targetSimilarityAverage * 0.15
                        + exactTargetCoverage * 0.25
                        + melhorFraseScore * 0.25;
        /*
         * =========================================================
         * BÔNUS DE EVIDÊNCIA FORTE
         * =========================================================
         *
         * Esta é a correção principal para o seu Rexona.
         *
         * CLINICAL + CLEAN
         *
         * são dois tokens exatos em um nome com três tokens.
         *
         * Isso deve ser considerado uma evidência forte,
         * mesmo que MEN não tenha sido lido perfeitamente.
         */
        if (tokensAlvoExatos >= 2) {
            score += 0.20;
        }
        /*
         * Dois ou mais tokens exatos ocupando pelo menos
         * aproximadamente 2/3 do nome identificador.
         */
        if (tokensAlvoExatos >= 2
                && targetCoverage >= 0.66) {
            score += 0.10;
        }
        /*
         * Três ou mais tokens exatos praticamente determinam
         * o produto.
         */
        if (tokensAlvoExatos >= 3) {
            score += 0.10;
        }
        if (score > 1.0) {
            score = 1.0;
        }
        /*
         * =========================================================
         * SEGURANÇA
         * =========================================================
         *
         * Ainda exigimos pelo menos duas evidências fortes.
         *
         * Assim:
         *
         * "Clinical"
         * sozinho
         *
         * não basta.
         *
         * Mas:
         *
         * "Clinical" + "Clean"
         *
         * basta quando há diferença suficiente para o
         * segundo produto.
         */
        if (termosFortesCasados < 2) {
            /*
             * Uma exceção segura:
             * dois tokens EXATOS do nome do produto
             * também são uma evidência forte.
             */
            if (tokensAlvoExatos < 2) {
                return null;
            }
        }
        String candidatoFinal =
                melhorFrase != null
                        ? melhorFrase
                        : melhorTermo;
        return new ProductEvidence(
                score,
                candidatoFinal,
                Math.max(
                        termosFortesCasados,
                        tokensAlvoExatos
                ),
                melhorTermoScore
        );
    }

    /**
     * Extrai termos fortes dos candidatos OCR.
     */
    @NonNull
    static List<String> extrairTermosFortes(
            @NonNull List<String> candidatos
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String candidato :
                candidatos) {
            if (candidato == null) {
                continue;
            }
            for (String token :
                    tokensSignificativos(
                            candidato
                    )) {
                if (isTermoFraco(token)) {
                    continue;
                }
                if (token.length() < 3) {
                    continue;
                }
                if (!resultado.contains(token)) {
                    resultado.add(
                            token
                    );
                }
            }
        }
        return resultado;
    }

    /**
     * Cria frases a partir das próprias strings
     * produzidas pelo extractor.
     *
     * Exemplo:
     *
     * NATIVA SPA
     *
     * vira:
     *
     * NATIVA SPA
     */
    @NonNull
    static List<String> gerarFrasesProduto(
            @NonNull List<String> candidatos
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String candidato :
                candidatos) {
            if (candidato == null) {
                continue;
            }
            String normalizado =
                    normalizarCampo(
                            candidato
                    );
            String[] tokens =
                    normalizado.split(
                            "\\s+"
                    );
            if (tokens.length < 2) {
                continue;
            }
            adicionarUnico(
                    resultado,
                    normalizado
            );
            /*
             * Frases contíguas de 2 tokens.
             */
            for (int i = 0;
                 i < tokens.length - 1;
                 i++) {
                adicionarUnico(
                        resultado,
                        tokens[i]
                                + " "
                                + tokens[i + 1]
                );
                /*
                 * Frases contíguas de 3 tokens.
                 */
                if (i < tokens.length - 2) {
                    adicionarUnico(
                            resultado,
                            tokens[i]
                                    + " "
                                    + tokens[i + 1]
                                    + " "
                                    + tokens[i + 2]
                    );
                }
            }
        }
        return resultado;
    }

    /**
     * Compara uma frase comercial contra o nome do produto.
     */
    private static double scoreFraseContidaNoProduto(
            @NonNull String frase,
            @NonNull String nomeProduto
    ) {
        String consulta =
                normalizarCampo(
                        frase
                );
        String alvo =
                normalizarCampo(
                        nomeProduto
                );
        String[] tokensConsulta =
                consulta.split("\\s+");
        if (tokensConsulta.length < 2) {
            return 0.0;
        }
        /*
         * Caso ideal:
         *
         * NATIVA SPA
         *
         * existe literalmente em:
         *
         * CREME PARA MAOS NATIVA SPA QUINOA
         */
        if (alvo.contains(
                consulta
        )) {
            if (tokensConsulta.length >= 3) {
                return 1.0;
            }
            return 0.97;
        }
        /*
         * Fallback fuzzy por tokens.
         */
        String[] tokensAlvo =
                alvo.split("\\s+");
        int casados =
                0;
        double soma =
                0.0;
        for (String tokenConsulta :
                tokensConsulta) {
            if (tokenConsulta.length() < 3) {
                continue;
            }
            double melhor =
                    0.0;
            for (String tokenAlvo :
                    tokensAlvo) {
                double score =
                        melhorSimilaridadeOCR(
                                tokenConsulta,
                                tokenAlvo,
                                false
                        );
                if (score > melhor) {
                    melhor = score;
                }
            }
            if (melhor
                    >= TOKEN_MATCH_THRESHOLD) {
                casados++;
                soma += melhor;
            }
        }
        if (casados < 2) {
            return 0.0;
        }
        double cobertura =
                (double) casados
                        / tokensConsulta.length;
        double media =
                soma
                        / tokensConsulta.length;
        return Math.min(
                0.95,
                cobertura * 0.70
                        + media * 0.30
        );
    }

    static class ProductEvidence {
        /* Package-private: ScanCatalogMatcher consumes this immutable value. */
        final double score;
        final String candidatoOCR;
        final int termosFortesCasados;
        final double melhorTermoScore;
        ProductEvidence(
                double score,
                String candidatoOCR,
                int termosFortesCasados,
                double melhorTermoScore
        ) {
            this.score =
                    score;
            this.candidatoOCR =
                    candidatoOCR;
            this.termosFortesCasados =
                    termosFortesCasados;
            this.melhorTermoScore =
                    melhorTermoScore;
        }
    }
}
