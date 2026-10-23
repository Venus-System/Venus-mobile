package com.venussystem.venusmobile.repository;

import androidx.annotation.NonNull;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanProductMatch;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static com.venussystem.venusmobile.repository.ScanCatalogScoring.ProductEvidence;
import static com.venussystem.venusmobile.repository.ScanCatalogScoring.extrairTermosFortes;
import static com.venussystem.venusmobile.repository.ScanCatalogScoring.gerarFrasesProduto;
import static com.venussystem.venusmobile.repository.ScanCatalogScoring.pontuarProduto;
import static com.venussystem.venusmobile.repository.ScanCatalogText.adicionarUnico;
import static com.venussystem.venusmobile.repository.ScanCatalogText.limparCandidatosMarca;
import static com.venussystem.venusmobile.repository.ScanCatalogText.limparCandidatosProduto;
import static com.venussystem.venusmobile.repository.ScanCatalogText.melhorSimilaridadeOCR;
import static com.venussystem.venusmobile.repository.ScanCatalogText.normalizarCampo;
import static com.venussystem.venusmobile.repository.ScanCatalogText.removerMarcaDoTexto;

/** Internal collaborator for ScanProductMatchRepository; not a second scan entry point. */
final class ScanCatalogMatcher {
    private ScanCatalogMatcher() { }

    private static final double MIN_BRAND_SCORE = 0.80;
    private static final double MIN_BRAND_SCORE_GAP = 0.05;

    /*
     * Com marca encontrada, usamos um threshold de produto
     * suficientemente seguro, mas sem exigir similaridade
     * entre o texto inteiro do OCR e o nome inteiro do produto.
     */
    private static final double MIN_PRODUCT_SCORE_WITH_BRAND = 0.72;

    /*
     * Sem marca, continuamos muito mais rigorosos.
     */
    private static final double MIN_PRODUCT_SCORE_WITHOUT_BRAND = 0.90;

    private static final double MIN_SCORE_GAP_WITH_BRAND = 0.05;

    private static final double MIN_SCORE_GAP_WITHOUT_BRAND = 0.08;

    @NonNull
    static ScanProductMatch procurarMelhorProduto(
            @NonNull ScanFrontData frontData,
            @NonNull List<Produto> catalogo
    ) {
        List<String> marcasOCR =
                limparCandidatosMarca(
                        frontData.getBrandCandidates()
                );
        List<String> produtosOCR =
                limparCandidatosProduto(
                        frontData.getProductCandidates()
                );
        if (marcasOCR.isEmpty()
                && produtosOCR.isEmpty()) {
            return ScanProductMatch.semMatch();
        }
        /*
         * =========================================================
         * 1. MARCA PRIMEIRO
         * =========================================================
         */
        BrandMatch marcaEncontrada =
                encontrarMarca(
                        marcasOCR,
                        catalogo
                );
        /*
         * Fallback controlado:
         * se o extractor não colocou a marca em brandCandidates,
         * também procuramos nos candidatos de produto.
         */
        if (marcaEncontrada == null) {
            marcaEncontrada =
                    encontrarMarca(
                            produtosOCR,
                            catalogo
                    );
        }
        /*
         * =========================================================
         * 2. MARCA ENCONTRADA
         * =========================================================
         */
        if (marcaEncontrada != null) {
            if (marcaEncontrada.segundoScore > 0.0
                    && marcaEncontrada.score - marcaEncontrada.segundoScore
                    < MIN_BRAND_SCORE_GAP) {
                return ScanProductMatch.ambiguo(
                        marcaEncontrada.score,
                        marcaEncontrada.score - marcaEncontrada.segundoScore,
                        marcaEncontrada.candidatoOCR,
                        null
                );
            }
            /*
             * Restringe o catálogo somente aos produtos
             * pertencentes à marca identificada.
             */
            List<Produto> produtosDaMarca =
                    new ArrayList<>();
            for (Produto produto :
                    catalogo) {
                if (produto == null) {
                    continue;
                }
                String marcaBanco =
                        normalizarCampo(
                                produto.getBrandName()
                        );
                if (marcaBanco.equals(
                        marcaEncontrada.nome
                )) {
                    produtosDaMarca.add(
                            produto
                    );
                }
            }
            if (produtosDaMarca.isEmpty()) {
                return ScanProductMatch
                        .semMatch();
            }
            /*
             * Agora procura o produto somente dentro
             * dos produtos daquela marca.
             */
            ProductMatch produtoEncontrado =
                    encontrarProduto(
                            produtosOCR,
                            produtosDaMarca,
                            marcaEncontrada.nome
                    );
            if (produtoEncontrado == null) {
                return ScanProductMatch
                        .semMatch();
            }
            boolean produtoConfiavel =
                    produtoEncontrado.score
                            >= MIN_PRODUCT_SCORE_WITH_BRAND
                            && (produtoEncontrado.termosFortesCasados >= 2
                            || (produtoEncontrado.tokensAlvoExatos >= 1
                            && produtoEncontrado.segundoScore <= 0.0));
            boolean ambiguo =
                    produtoEncontrado.segundoScore > 0.0
                            && (
                            produtoEncontrado.score
                                    - produtoEncontrado
                                    .segundoScore
                    ) < MIN_SCORE_GAP_WITH_BRAND;
            /*
             * Um nome mais específico pode saturar o mesmo score do nome
             * curto. Ex.: "CLINICAL CLEAN MEN" e "CLINICAL CLEAN" recebem
             * as mesmas evidências de uma leitura que contém os três termos,
             * embora o primeiro explique mais texto identificado. O matcher
             * guarda essa evidência como desempate; não é uma autorização
             * para aceitar dois candidatos com a mesma especificidade.
             */
            if (ambiguo
                    && produtoEncontrado.tokensAlvoExatos
                    > produtoEncontrado.segundoTokensAlvoExatos
                    && produtoEncontrado.tokensAlvoExatos >= 2) {
                ambiguo = false;
            }
            if (marcaEncontrada.score
                    < MIN_BRAND_SCORE) {
                return ScanProductMatch
                        .semMatch();
            }
            if (!produtoConfiavel
                    || ambiguo) {
                double gap = produtoEncontrado.segundoScore > 0.0
                        ? produtoEncontrado.score - produtoEncontrado.segundoScore
                        : produtoEncontrado.score;
                return ScanProductMatch.ambiguo(
                        produtoEncontrado.score,
                        gap,
                        marcaEncontrada.candidatoOCR,
                        produtoEncontrado.candidatoOCR
                );
            }
            /*
             * =====================================================
             * PRODUTO ENCONTRADO
             * =====================================================
             *
             * Aqui devolvemos o OBJETO REAL DO CATÁLOGO.
             *
             * O ScanSuccessFrontActivity pega:
             *
             * match.getProduto().getId()
             *
             * e abre a DetalheProdutoActivity.
             */
            return ScanProductMatch.encontrado(
                    produtoEncontrado.produto,
                    produtoEncontrado.score,
                    marcaEncontrada.candidatoOCR,
                    produtoEncontrado.candidatoOCR,
                    produtoEncontrado.score - produtoEncontrado.segundoScore
            );
        }
        /*
         * =========================================================
         * 3. SEM MARCA
         * =========================================================
         */
        ProductMatch fallback =
                encontrarProduto(
                        produtosOCR,
                        catalogo,
                        null
                );
        if (fallback == null) {
            return ScanProductMatch
                    .semMatch();
        }
        boolean confiavel =
                fallback.score
                        >= MIN_PRODUCT_SCORE_WITHOUT_BRAND
                        && fallback
                        .termosFortesCasados >= 2;
        boolean ambiguo =
                fallback.segundoScore > 0.0
                        && (
                        fallback.score
                                - fallback.segundoScore
                ) < MIN_SCORE_GAP_WITHOUT_BRAND;
        if (!confiavel
                || ambiguo) {
            double gap = fallback.segundoScore > 0.0
                    ? fallback.score - fallback.segundoScore
                    : fallback.score;
            return ScanProductMatch.ambiguo(
                    fallback.score,
                    gap,
                    null,
                    fallback.candidatoOCR
            );
        }
        return ScanProductMatch.encontrado(
                fallback.produto,
                fallback.score,
                null,
                fallback.candidatoOCR,
                fallback.score - fallback.segundoScore
        );
    }

    /**
     * Encontra a marca mais provável no catálogo.
     */
    private static BrandMatch encontrarMarca(
            @NonNull List<String> candidatosOCR,
            @NonNull List<Produto> catalogo
    ) {
        if (candidatosOCR.isEmpty()) {
            return null;
        }
        Set<String> marcasCatalogo =
                new LinkedHashSet<>();
        for (Produto produto :
                catalogo) {
            if (produto == null) {
                continue;
            }
            String marca =
                    normalizarCampo(
                            produto.getBrandName()
                    );
            if (!marca.isEmpty()) {
                marcasCatalogo.add(
                        marca
                );
            }
        }
        BrandMatch melhor =
                null;
        for (String candidatoOCR :
                candidatosOCR) {
            for (String marcaBanco :
                    marcasCatalogo) {
                double score =
                        melhorSimilaridadeOCR(
                                candidatoOCR,
                                marcaBanco,
                                true
                        );
                if (score < MIN_BRAND_SCORE) {
                    continue;
                }
                if (melhor == null) {
                    melhor = new BrandMatch(marcaBanco, candidatoOCR, score, 0.0);
                } else if (score > melhor.score) {
                    melhor = new BrandMatch(marcaBanco, candidatoOCR, score, melhor.score);
                } else if (!marcaBanco.equals(melhor.nome)
                        && score > melhor.segundoScore) {
                    melhor.segundoScore = score;
                }
            }
        }
        return melhor;
    }

    /**
     * Procura o melhor produto dentro do catálogo recebido.
     */
    private static ProductMatch encontrarProduto(
            @NonNull List<String> candidatosOCR,
            @NonNull List<Produto> catalogo,
            String marcaNormalizada
    ) {
        List<String> candidatos =
                new ArrayList<>();
        /*
         * Remove a marca das strings de produto para que,
         * por exemplo:
         *
         * REXONA CLINICAL
         *
         * vire:
         *
         * CLINICAL
         */
        for (String candidato :
                candidatosOCR) {
            if (candidato == null) {
                continue;
            }
            String limpo =
                    removerMarcaDoTexto(
                            normalizarCampo(
                                    candidato
                            ),
                            marcaNormalizada
                    );
            if (limpo.isEmpty()) {
                continue;
            }
            adicionarUnico(
                    candidatos,
                    limpo
            );
        }
        if (candidatos.isEmpty()) {
            return null;
        }
        /*
         * Termos fortes continuam sendo úteis como evidência,
         * mas NÃO serão mais usados como denominador principal
         * do score.
         */
        List<String> termosFortes =
                extrairTermosFortes(
                        candidatos
                );
        if (termosFortes.isEmpty()) {
            return null;
        }
        /*
         * Gera frases a partir das próprias linhas/candidatos.
         *
         * Isso permite reconhecer:
         *
         * NATIVA SPA
         * CREMA INVISIBLE
         * CONTROL DEL OLOR
         *
         * quando essas frases aparecem dentro do nome do produto.
         */
        List<String> frases =
                gerarFrasesProduto(
                        candidatos
                );
        ProductMatch melhor =
                null;
        for (Produto produto :
                catalogo) {
            if (produto == null) {
                continue;
            }
            String nomeProduto =
                    normalizarCampo(
                            produto.getName()
                    );
            if (nomeProduto.isEmpty()) {
                continue;
            }
            String nomeSemMarca =
                    removerMarcaDoTexto(
                            nomeProduto,
                            marcaNormalizada
                    );
            ProductEvidence evidencia =
                    pontuarProduto(
                            candidatos,
                            termosFortes,
                            frases,
                            nomeSemMarca
                    );
            if (evidencia == null
                    || evidencia.score <= 0.0) {
                continue;
            }
            if (melhor == null) {
                melhor =
                        new ProductMatch(
                                produto,
                                evidencia.score,
                                0.0,
                                evidencia.candidatoOCR,
                                evidencia.termosFortesCasados,
                                evidencia.tokensAlvoExatos,
                                0
                        );
                continue;
            }
            boolean scoreMaior = evidencia.score > melhor.score;
            boolean mesmoScoreMaisEspecifico =
                    Math.abs(evidencia.score - melhor.score) < 0.0001
                            && evidencia.tokensAlvoExatos
                            > melhor.tokensAlvoExatos;
            if (scoreMaior || mesmoScoreMaisEspecifico) {
                melhor =
                        new ProductMatch(
                                produto,
                                evidencia.score,
                                melhor.score,
                                evidencia.candidatoOCR,
                                evidencia.termosFortesCasados,
                                evidencia.tokensAlvoExatos,
                                melhor.tokensAlvoExatos
                        );
            } else if (
                    evidencia.score
                            > melhor.segundoScore
                            || (Math.abs(evidencia.score - melhor.segundoScore)
                            < 0.0001
                            && evidencia.tokensAlvoExatos
                            > melhor.segundoTokensAlvoExatos)
            ) {
                melhor =
                        new ProductMatch(
                                melhor.produto,
                                melhor.score,
                                evidencia.score,
                                melhor.candidatoOCR,
                                melhor.termosFortesCasados,
                                melhor.tokensAlvoExatos,
                                evidencia.tokensAlvoExatos
                        );
            }
        }
        return melhor;
    }

    private static class BrandMatch {
        private final String nome;
        private final String candidatoOCR;
        private final double score;
        private double segundoScore;
        BrandMatch(
                String nome,
                String candidatoOCR,
                double score,
                double segundoScore
        ) {
            this.nome =
                    nome;
            this.candidatoOCR =
                    candidatoOCR;
            this.score =
                    score;
            this.segundoScore = segundoScore;
        }
    }

    private static class ProductMatch {
        private final Produto produto;
        private final double score;
        private final double segundoScore;
        private final String candidatoOCR;
        private final int termosFortesCasados;
        private final int tokensAlvoExatos;
        private final int segundoTokensAlvoExatos;
        ProductMatch(
                Produto produto,
                double score,
                double segundoScore,
                String candidatoOCR,
                int termosFortesCasados,
                int tokensAlvoExatos,
                int segundoTokensAlvoExatos
        ) {
            this.produto =
                    produto;
            this.score =
                    score;
            this.segundoScore =
                    segundoScore;
            this.candidatoOCR =
                    candidatoOCR;
            this.termosFortesCasados =
                    termosFortesCasados;
            this.tokensAlvoExatos =
                    tokensAlvoExatos;
            this.segundoTokensAlvoExatos =
                    segundoTokensAlvoExatos;
        }
    }
}
