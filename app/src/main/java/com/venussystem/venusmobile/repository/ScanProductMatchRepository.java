package com.venussystem.venusmobile.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanProductMatch;
import com.venussystem.venusmobile.view.util.ScanTextNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Identifica um produto da VENUS usando os dados extraídos
 * da frente da embalagem e o catálogo real carregado.
 *
 * Fluxo:
 *
 * OCR
 *   ↓
 * marca
 *   ↓
 * produtos da marca
 *   ↓
 * tokens/frases relevantes do produto
 *   ↓
 * produto real do catálogo
 */
public class ScanProductMatchRepository {

    private static final String TAG = "VENUS_MATCH";

    private static final long MAX_WAIT_MILLIS = 15000L;
    private static final long WAIT_STEP_MILLIS = 200L;

    private static final double MIN_BRAND_SCORE = 0.80;
    private static final double MIN_SHORT_BRAND_SCORE = 0.92;

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

    private static final double TOKEN_MATCH_THRESHOLD = 0.84;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final ProdutoRepository produtoRepository =
            new ProdutoRepository();

    public interface Callback {

        void onSuccess(
                @NonNull ScanProductMatch resultado
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public void identificar(
            @NonNull ScanFrontData frontData,
            @NonNull Callback callback
    ) {

        Log.d(
                TAG,
                "IDENTIFICAR() CHAMADO"
        );

        executor.execute(() -> {

            Log.d(
                    TAG,
                    "THREAD DO MATCH INICIADA"
            );

            try {

                Log.d(
                        TAG,
                        "BUSCANDO CATALOGO..."
                );

                List<Produto> catalogo =
                        obterCatalogo();

                Log.d(
                        TAG,
                        "CATALOGO RECEBIDO: "
                                + (
                                catalogo == null
                                        ? "NULL"
                                        : catalogo.size()
                        )
                );

                if (catalogo == null
                        || catalogo.isEmpty()) {

                    postarResultado(
                            callback,
                            ScanProductMatch.semMatch()
                    );

                    return;
                }

                ScanProductMatch resultado =
                        procurarMelhorProduto(
                                frontData,
                                catalogo
                        );

                Log.d(
                        TAG,
                        criarLogResultado(resultado)
                );

                postarResultado(
                        callback,
                        resultado
                );

            } catch (Exception exception) {

                Log.e(
                        TAG,
                        "ERRO AO IDENTIFICAR PRODUTO",
                        exception
                );

                mainHandler.post(
                        () ->
                                callback.onError(
                                        exception
                                )
                );
            }
        });
    }

    @NonNull
    private List<Produto> obterCatalogo()
            throws InterruptedException {

        List<Produto> catalogo =
                produtoRepository
                        .getCatalogo()
                        .getValue();

        if (catalogo != null
                && !catalogo.isEmpty()) {

            return catalogo;
        }

        produtoRepository.carregar(
                false
        );

        long inicio =
                System.currentTimeMillis();

        while (
                System.currentTimeMillis()
                        - inicio
                        < MAX_WAIT_MILLIS
        ) {

            catalogo =
                    produtoRepository
                            .getCatalogo()
                            .getValue();

            if (catalogo != null
                    && !catalogo.isEmpty()) {

                return catalogo;
            }

            Thread.sleep(
                    WAIT_STEP_MILLIS
            );
        }

        catalogo =
                produtoRepository
                        .getCatalogo()
                        .getValue();

        return catalogo != null
                ? catalogo
                : new ArrayList<>();
    }

    @NonNull
    private ScanProductMatch procurarMelhorProduto(
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

        Log.d(
                TAG,
                "CANDIDATOS MARCA LIMPOS: "
                        + marcasOCR
        );

        Log.d(
                TAG,
                "CANDIDATOS PRODUTO LIMPOS: "
                        + produtosOCR
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

            Log.d(
                    TAG,
                    "MARCA ENCONTRADA NO CATALOGO: "
                            + marcaEncontrada.nome
            );

            Log.d(
                    TAG,
                    "OCR MARCA: "
                            + marcaEncontrada.candidatoOCR
            );

            Log.d(
                    TAG,
                    "SCORE MARCA: "
                            + marcaEncontrada.score
            );

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

            Log.d(
                    TAG,
                    "PRODUTOS DA MARCA "
                            + marcaEncontrada.nome
                            + ": "
                            + produtosDaMarca.size()
            );

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

            Log.d(
                    TAG,
                    "MELHOR PRODUTO: "
                            + produtoEncontrado
                            .produto
                            .getName()
            );

            Log.d(
                    TAG,
                    "SCORE PRODUTO: "
                            + produtoEncontrado.score
            );

            Log.d(
                    TAG,
                    "SEGUNDO SCORE: "
                            + produtoEncontrado
                            .segundoScore
            );

            Log.d(
                    TAG,
                    "TERMOS FORTES CASADOS: "
                            + produtoEncontrado
                            .termosFortesCasados
            );

            boolean produtoConfiavel =
                    produtoEncontrado.score
                            >= MIN_PRODUCT_SCORE_WITH_BRAND
                            && produtoEncontrado
                            .termosFortesCasados >= 2;

            boolean ambiguo =
                    produtoEncontrado.segundoScore > 0.0
                            && (
                            produtoEncontrado.score
                                    - produtoEncontrado
                                    .segundoScore
                    ) < MIN_SCORE_GAP_WITH_BRAND;

            if (marcaEncontrada.score
                    < MIN_BRAND_SCORE) {

                return ScanProductMatch
                        .semMatch();
            }

            if (!produtoConfiavel
                    || ambiguo) {

                Log.d(
                        TAG,
                        "MATCH REJEITADO: "
                                + "confiavel="
                                + produtoConfiavel
                                + " ambiguo="
                                + ambiguo
                );

                return ScanProductMatch
                        .semMatch();
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
                    produtoEncontrado.candidatoOCR
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

            return ScanProductMatch
                    .semMatch();
        }

        return ScanProductMatch.encontrado(
                fallback.produto,
                fallback.score,
                null,
                fallback.candidatoOCR
        );
    }

    /**
     * Encontra a marca mais provável no catálogo.
     */
    private BrandMatch encontrarMarca(
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

        Log.d(
                TAG,
                "MARCAS NO CATALOGO: "
                        + marcasCatalogo
        );

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

                if (melhor == null
                        || score > melhor.score) {

                    melhor =
                            new BrandMatch(
                                    marcaBanco,
                                    candidatoOCR,
                                    score
                            );
                }
            }
        }

        return melhor;
    }

    /**
     * Procura o melhor produto dentro do catálogo recebido.
     */
    private ProductMatch encontrarProduto(
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
                                evidencia.termosFortesCasados
                        );

                continue;
            }

            if (evidencia.score
                    > melhor.score) {

                melhor =
                        new ProductMatch(
                                produto,
                                evidencia.score,
                                melhor.score,
                                evidencia.candidatoOCR,
                                evidencia.termosFortesCasados
                        );

            } else if (
                    evidencia.score
                            > melhor.segundoScore
            ) {

                melhor =
                        new ProductMatch(
                                melhor.produto,
                                melhor.score,
                                evidencia.score,
                                melhor.candidatoOCR,
                                melhor.termosFortesCasados
                        );
            }
        }

        return melhor;
    }

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
    private ProductEvidence pontuarProduto(
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

        Log.d(
                TAG,
                "EVIDENCIA PRODUTO: "
                        + nomeProduto
                        + " | alvo="
                        + tokensAlvo
                        + " | alvoCasados="
                        + tokensAlvoCasados
                        + " | alvoExatos="
                        + tokensAlvoExatos
                        + " | cobertura="
                        + targetCoverage
                        + " | media="
                        + targetSimilarityAverage
                        + " | frase="
                        + melhorFrase
                        + " | fraseScore="
                        + melhorFraseScore
                        + " | termosFortes="
                        + termosFortesCasados
                        + " | scoreFinal="
                        + score
        );

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
    private List<String> extrairTermosFortes(
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
     * Termos realmente genéricos.
     *
     * Importante:
     * MEN / WOMEN / UNISEX / 72H etc. NÃO são mais
     * descartados aqui, pois podem diferenciar produtos
     * da mesma marca.
     */
    private boolean isTermoFraco(
            @NonNull String token
    ) {

        switch (token) {

            /*
             * Tipos genéricos de produto.
             */
            case "GEL":
            case "LOCAO":
            case "LOÇÃO":
            case "LIQUIDO":
            case "LIQUID":
            case "CREME":
            case "CREMA":
            case "CREAM":
            case "SPRAY":
            case "AEROSOL":

                /*
                 * Desodorante / antitranspirante.
                 */
            case "DESODORANTE":
            case "DEODORANTE":
            case "ANTITRANSPIRANTE":
            case "ANTIPERSPIRANTE":
            case "ANTITRANSPIRANT":
            case "ANTIPERSPIRANT":

                /*
                 * Categorias / descrições genéricas.
                 */
            case "SHAMPOO":
            case "CONDICIONADOR":
            case "CONDITIONER":
            case "SABONETE":
            case "SOAP":

                /*
                 * Termos genéricos de proteção/descrição.
                 */
            case "PROTECAO":
            case "PROTECCION":
            case "PROTECTION":
            case "PERFUME":
            case "PERFUMACAO":
            case "FRAGRANCE":
            case "BODY":
            case "SKIN":
            case "HAIR":

                /*
                 * Termos muito genéricos de descrição.
                 */
            case "DRY":
            case "LONG":
            case "LASTING":
            case "INVISIBLE":
            case "INVISIVEL":

                return true;

            default:
                return false;
        }
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
    private List<String> gerarFrasesProduto(
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
    private double scoreFraseContidaNoProduto(
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

    @NonNull
    private List<String> limparCandidatosMarca(
            @NonNull List<String> entrada
    ) {

        List<String> resultado =
                new ArrayList<>();

        for (String item :
                entrada) {

            if (item == null) {
                continue;
            }

            String normalizado =
                    normalizarCampo(
                            item
                    );

            if (normalizado.length() < 4) {
                continue;
            }

            if (!resultado.contains(
                    normalizado
            )) {

                resultado.add(
                        normalizado
                );
            }
        }

        return resultado;
    }

    @NonNull
    private List<String> limparCandidatosProduto(
            @NonNull List<String> entrada
    ) {

        List<String> resultado =
                new ArrayList<>();

        for (String item :
                entrada) {

            if (item == null) {
                continue;
            }

            String normalizado =
                    normalizarCampo(
                            item
                    );

            if (normalizado.length() < 3) {
                continue;
            }

            if (!resultado.contains(
                    normalizado
            )) {

                resultado.add(
                        normalizado
                );
            }
        }

        return resultado;
    }

    /**
     * Remove a marca de uma string.
     *
     * Também funciona para marcas compostas:
     *
     * O BOTICARIO
     * LA ROCHE POSAY
     * VICTORIA SECRET
     */
    @NonNull
    private String removerMarcaDoTexto(
            @NonNull String texto,
            String marca
    ) {

        String resultado =
                normalizarCampo(
                        texto
                );

        String marcaNormalizada =
                normalizarCampo(
                        marca
                );

        if (marcaNormalizada.isEmpty()) {
            return resultado;
        }

        /*
         * Remove a expressão inteira.
         */
        resultado =
                resultado
                        .replace(
                                marcaNormalizada,
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        /*
         * Exemplo:
         *
         * OCR = OBOTICARIO
         * Banco = O BOTICARIO
         *
         * O matcher de marca pode reconhecer os dois.
         * Aqui apenas evitamos que um texto composto
         * só da marca entre como nome de produto.
         */
        String marcaSemEspacos =
                marcaNormalizada
                        .replace(
                                " ",
                                ""
                        );

        String textoSemEspacos =
                resultado
                        .replace(
                                " ",
                                ""
                        );

        if (!marcaSemEspacos.isEmpty()
                && textoSemEspacos.equals(
                marcaSemEspacos
        )) {

            return "";
        }

        return resultado;
    }

    @NonNull
    private double melhorSimilaridadeOCR(
            @NonNull String consulta,
            @NonNull String alvo,
            boolean campoMarca
    ) {

        double melhor =
                0.0;

        for (String variante :
                gerarVariantesOCR(
                        consulta
                )) {

            if (variante == null
                    || variante.isEmpty()) {

                continue;
            }

            double score =
                    similaridade(
                            variante,
                            alvo
                    );

            /*
             * Marcas curtas exigem mais precisão.
             */
            if (campoMarca
                    && variante.length() <= 4
                    && score
                    < MIN_SHORT_BRAND_SCORE) {

                continue;
            }

            if (score > melhor) {
                melhor = score;
            }
        }

        return melhor;
    }

    @NonNull
    private List<String> gerarVariantesOCR(
            @NonNull String entrada
    ) {

        String base =
                normalizarCampo(
                        entrada
                );

        Set<String> variantes =
                new LinkedHashSet<>();

        variantes.add(
                base
        );

        /*
         * Substituições comuns de OCR.
         */
        variantes.add(
                base.replace(
                        '0',
                        'O'
                )
        );

        variantes.add(
                base.replace(
                        '1',
                        'I'
                )
        );

        variantes.add(
                base.replace(
                        '1',
                        'L'
                )
        );

        variantes.add(
                base.replace(
                        '5',
                        'S'
                )
        );

        variantes.add(
                base.replace(
                        '8',
                        'B'
                )
        );

        variantes.add(
                base.replace(
                        '2',
                        'Z'
                )
        );

        variantes.add(
                base.replace(
                        '4',
                        'A'
                )
        );

        variantes.add(
                base.replace(
                        "11",
                        "H"
                )
        );

        variantes.add(
                base.replace(
                        "RN",
                        "M"
                )
        );

        variantes.add(
                base.replace(
                        "VV",
                        "W"
                )
        );

        return new ArrayList<>(
                variantes
        );
    }

    private double similaridade(
            @NonNull String consulta,
            @NonNull String alvo
    ) {

        if (consulta.equals(
                alvo
        )) {

            return 1.0;
        }

        if (alvo.contains(
                consulta
        )
                || consulta.contains(
                alvo
        )) {

            int menor =
                    Math.min(
                            consulta.length(),
                            alvo.length()
                    );

            int maior =
                    Math.max(
                            consulta.length(),
                            alvo.length()
                    );

            if (menor >= 4
                    && maior > 0) {

                double cobertura =
                        (double) menor
                                / maior;

                return Math.min(
                        0.97,
                        0.84
                                + cobertura * 0.13
                );
            }
        }

        return Math.max(
                similaridadePorTokens(
                        consulta,
                        alvo
                ),
                similaridadePorCaracteres(
                        consulta,
                        alvo
                )
        );
    }

    private double similaridadePorTokens(
            @NonNull String consulta,
            @NonNull String alvo
    ) {

        Set<String> consultaTokens =
                tokensSignificativos(
                        consulta
                );

        Set<String> alvoTokens =
                tokensSignificativos(
                        alvo
                );

        if (consultaTokens.isEmpty()
                || alvoTokens.isEmpty()) {

            return 0.0;
        }

        int encontrados =
                0;

        for (String a :
                consultaTokens) {

            for (String b :
                    alvoTokens) {

                if (a.equals(
                        b
                )) {

                    encontrados++;

                    break;
                }

                if (a.length() >= 4
                        && b.length() >= 4
                        && similaridadePorCaracteres(
                        a,
                        b
                ) >= 0.80) {

                    encontrados++;

                    break;
                }
            }
        }

        return (double) encontrados
                / consultaTokens.size();
    }

    private double similaridadePorCaracteres(
            @NonNull String a,
            @NonNull String b
    ) {

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
                (double) distancia
                        / maior
        );
    }

    private int levenshtein(
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

            anterior[j] = j;
        }

        for (int i = 1;
             i <= a.length();
             i++) {

            atual[0] = i;

            for (int j = 1;
                 j <= b.length();
                 j++) {

                int custo =
                        a.charAt(i - 1)
                                == b.charAt(j - 1)
                                ? 0
                                : 1;

                atual[j] =
                        Math.min(
                                Math.min(
                                        atual[j - 1]
                                                + 1,
                                        anterior[j]
                                                + 1
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

    /**
     * Retorna somente os tokens que realmente ajudam
     * a identificar o produto.
     *
     * Palavras como CREME, LOCAO, PARA, DE etc. não servem
     * como identidade.
     *
     * Já:
     *
     * CLINICAL
     * CLEAN
     * MEN
     * NATIVA
     * SPA
     * QUINOA
     *
     * continuam.
     */
    @NonNull
    private Set<String> tokensProdutoIdentidade(
            @NonNull String texto
    ) {

        Set<String> resultado =
                new LinkedHashSet<>();

        for (String token :
                tokensSignificativos(
                        texto
                )) {

            if (token.length() < 3) {
                continue;
            }

            if (isStopwordProduto(
                    token
            )) {

                continue;
            }

            if (isTermoFraco(
                    token
            )) {

                continue;
            }

            /*
             * Capacidades não devem pesar como identidade.
             */
            if (token.matches(
                    "\\d+(?:[.,]\\d+)?(?:ML|G|GR|KG|L)"
            )) {

                continue;
            }

            if (token.matches(
                    "\\d+(?:[.,]\\d+)?FLOZ"
            )) {

                continue;
            }

            resultado.add(
                    token
            );
        }

        return resultado;
    }

    /**
     * Palavras estruturais sem poder de identificação.
     */
    private boolean isStopwordProduto(
            @NonNull String token
    ) {

        switch (token) {

            case "A":
            case "AS":
            case "O":
            case "OS":
            case "UM":
            case "UMA":
            case "DE":
            case "DO":
            case "DA":
            case "DOS":
            case "DAS":
            case "EM":
            case "NO":
            case "NA":
            case "NOS":
            case "NAS":
            case "E":
            case "PARA":
            case "COM":
                return true;

            default:
                return false;
        }
    }

    @NonNull
    private Set<String> tokensSignificativos(
            @NonNull String texto
    ) {

        Set<String> resultado =
                new LinkedHashSet<>();

        String[] partes =
                texto.split(
                        "\\s+"
                );

        for (String parte :
                partes) {

            String token =
                    parte.trim();

            if (token.length() >= 3) {

                resultado.add(
                        token
                );
            }
        }

        return resultado;
    }

    @NonNull
    private String normalizarCampo(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        String normalizado =
                ScanTextNormalizer.normalize(
                        texto
                );

        return normalizado
                .replaceAll(
                        "[^A-Z0-9 ]",
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

    private void adicionarUnico(
            @NonNull List<String> lista,
            String valor
    ) {

        if (valor == null) {
            return;
        }

        String normalizado =
                normalizarCampo(
                        valor
                );

        if (!normalizado.isEmpty()
                && !lista.contains(
                normalizado
        )) {

            lista.add(
                    normalizado
            );
        }
    }

    private void postarResultado(
            @NonNull Callback callback,
            @NonNull ScanProductMatch resultado
    ) {

        mainHandler.post(
                () ->
                        callback.onSuccess(
                                resultado
                        )
        );
    }

    @NonNull
    private String criarLogResultado(
            @NonNull ScanProductMatch resultado
    ) {

        if (!resultado.isFound()) {

            if (resultado.hasError()) {

                return
                        "MATCH ERROR: "
                                + resultado
                                .getErrorMessage();
            }

            return
                    "MATCH: nenhum produto encontrado";
        }

        Produto produto =
                resultado.getProduto();

        return
                "==============================\n"
                        + "MATCH ENCONTRADO\n"
                        + "ID: "
                        + (
                        produto == null
                                ? "NULL"
                                : produto.getId()
                )
                        + "\nNOME: "
                        + (
                        produto == null
                                ? "NULL"
                                : produto.getName()
                )
                        + "\nMARCA: "
                        + (
                        produto == null
                                ? "NULL"
                                : produto.getBrandName()
                )
                        + "\nSCORE: "
                        + resultado.getScore()
                        + "\nOCR MARCA: "
                        + resultado.getMatchedBrand()
                        + "\nOCR PRODUTO: "
                        + resultado.getMatchedProduct()
                        + "\n==============================";
    }

    public void close() {

        executor.shutdownNow();
    }

    private static class BrandMatch {

        private final String nome;
        private final String candidatoOCR;
        private final double score;

        BrandMatch(
                String nome,
                String candidatoOCR,
                double score
        ) {

            this.nome =
                    nome;

            this.candidatoOCR =
                    candidatoOCR;

            this.score =
                    score;
        }
    }

    private static class ProductMatch {

        private final Produto produto;
        private final double score;
        private final double segundoScore;
        private final String candidatoOCR;
        private final int termosFortesCasados;

        ProductMatch(
                Produto produto,
                double score,
                double segundoScore,
                String candidatoOCR,
                int termosFortesCasados
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
        }
    }

    private static class ProductEvidence {

        private final double score;
        private final String candidatoOCR;
        private final int termosFortesCasados;
        private final double melhorTermoScore;

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