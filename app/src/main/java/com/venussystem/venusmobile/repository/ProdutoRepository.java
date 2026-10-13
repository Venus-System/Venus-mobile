package com.venussystem.venusmobile.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.BuildConfig;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.BrandResponse;
import com.venussystem.venusmobile.repository.api.dto.IngredientResponse;
import com.venussystem.venusmobile.repository.api.dto.MediaAssetResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductCategoryResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductFullResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductIngredientResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;
import com.venussystem.venusmobile.repository.api.dto.ScoringModelResponse;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

public class ProdutoRepository {

    private static final String TAG = "VENUS_PRODUCT_DETAIL";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    // As chamadas do catalogo sao independentes, entao vao juntas neste pool em
    // vez de uma esperando a outra.
    private static final ExecutorService REDE = Executors.newFixedThreadPool(6);

    // A composição traz ids e cada id precisa ser resolvido para o nome INCI.
    // Este pool separado não bloqueia o pool principal quando a tela de
    // detalhe busca vários ingredientes ao mesmo tempo.
    private static final ExecutorService REDE_INGREDIENTES =
            Executors.newFixedThreadPool(6);

    private static final Handler PRINCIPAL =
            new Handler(Looper.getMainLooper());

    private static final MutableLiveData<List<Produto>> CATALOGO =
            new MutableLiveData<>();

    private static final AtomicBoolean CARREGADO =
            new AtomicBoolean(false);

    private static final MutableLiveData<Boolean> CARREGANDO =
            new MutableLiveData<>(false);

    private static final MutableLiveData<String> ERRO =
            new MutableLiveData<>();

    /*
     * As listas do catálogo não expõem media_assets. A imagem é resolvida
     * sob demanda pela rota leve de fotos da versão atual e fica em cache
     * para que cada produto seja consultado uma única vez por processo.
     */
    private static final Object CACHE_LOCK = new Object();
    private static final Map<Long, String> IMAGENS_CACHE = new HashMap<>();
    private static final Map<Long, Long> VERSOES_CACHE = new HashMap<>();
    private static final Map<Long, String> COMPOSICOES_CACHE = new HashMap<>();
    private static final Map<Long, List<AoObterImagem>> IMAGENS_EM_ANDAMENTO =
            new HashMap<>();

    private static final AtomicBoolean EM_ANDAMENTO =
            new AtomicBoolean(false);

    private final VenusApi api;

    public ProdutoRepository() {
        this(ClienteApi.get());
    }

    @VisibleForTesting
    public ProdutoRepository(VenusApi api) {
        this.api = api;
    }

    @VisibleForTesting
    public static void resetEstadoParaTeste() {
        aguardarExecutorOcioso();
        CATALOGO.postValue(null);
        CARREGADO.set(false);
        CARREGANDO.postValue(false);
        ERRO.postValue(null);
        EM_ANDAMENTO.set(false);
        synchronized (CACHE_LOCK) {
            IMAGENS_CACHE.clear();
            VERSOES_CACHE.clear();
            COMPOSICOES_CACHE.clear();
            IMAGENS_EM_ANDAMENTO.clear();
        }
    }

    /**
     * EXECUTOR e REDE sao estaticos e vivem pro processo de teste inteiro, nao
     * so reiniciados junto com os campos acima. Sem esta barreira, a tarefa em
     * segundo plano de um teste podia ainda estar rodando (ou na fila) quando
     * o proximo teste comecava - invisivel numa maquina rapida, mas em uma
     * mais lenta/carregada (CI) o atraso se acumula teste apos teste ate
     * estourar o timeout de aguardarValor. Submeter um no-op no EXECUTOR e
     * esperar ele rodar garante que a fila esvaziou - e como o EXECUTOR so
     * libera depois que as 6 chamadas do REDE respondem, isso arrasta o REDE
     * junto.
     */
    private static void aguardarExecutorOcioso() {
        try {
            EXECUTOR.submit(() -> null).get(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignorada) {
            // Se nem o no-op responder, o proprio teste que chamou isto vai
            // estourar seu timeout e relatar o problema real.
        }
    }

    public interface AoObterDetalhe {
        void aoConcluir(@Nullable String textoRotulo, @Nullable String urlFoto);
    }

    public interface AoObterImagem {
        void aoConcluir(@Nullable String urlFoto);
    }

    public LiveData<List<Produto>> getCatalogo() {
        return CATALOGO;
    }

    public LiveData<Boolean> getCarregando() {
        return CARREGANDO;
    }

    public LiveData<String> getErro() {
        return ERRO;
    }

    public void carregar(boolean forcar) {

        if (CARREGADO.get() && !forcar) {
            return;
        }

        if (!EM_ANDAMENTO.compareAndSet(false, true)) {
            return;
        }

        CARREGANDO.postValue(true);
        ERRO.postValue(null);

        EXECUTOR.execute(() -> {

            try {

                List<Produto> lista =
                        montarCatalogo();

                if (lista == null) {
                    lista = new ArrayList<>();
                }

                CARREGADO.set(true);
                CATALOGO.postValue(lista);

            } catch (FalhaApi falha) {

                CARREGADO.set(false);
                ERRO.postValue(
                        falha.getMessage()
                );

            } catch (IOException e) {

                CARREGADO.set(false);
                ERRO.postValue(
                        "Nao foi possivel falar com o servidor. "
                                + "Verifique sua conexao e tente de novo."
                );

            } finally {

                CARREGANDO.postValue(false);
                EM_ANDAMENTO.set(false);
            }
        });
    }

    @Nullable
    public Produto buscarNoCache(
            long produtoId
    ) {

        List<Produto> lista =
                CATALOGO.getValue();

        if (lista == null) {
            return null;
        }

        for (Produto produto : lista) {

            if (produto == null
                    || produto.getId() == null) {
                continue;
            }

            if (produto.getId().longValue()
                    == produtoId) {

                return produto;
            }
        }

        return null;
    }

    /**
     * Busca o agregado /full do produto (rotulo e fotos). E uma tela isolada,
     * nao o catalogo compartilhado, entao roda em uma chamada avulsa e devolve
     * pelo callback - null quando a API nao tiver o dado ou estiver fora do ar.
     */
    public void buscarDetalheProduto(long produtoId, AoObterDetalhe callback) {
        REDE.execute(() -> {
            DetalheProduto detalhe = obterDetalheCompleto(produtoId);
            guardarImagem(produtoId, detalhe.urlFoto);
            PRINCIPAL.post(() -> callback.aoConcluir(detalhe.textoRotulo, detalhe.urlFoto));
        });
    }

    /**
     * Resolve a primeira foto ativa de media_assets para cards e listas.
     * Chamadas simultâneas do mesmo produto compartilham a mesma consulta.
     */
    public void buscarImagemProduto(long produtoId, @NonNull AoObterImagem callback) {
        if (produtoId <= 0) {
            PRINCIPAL.post(() -> callback.aoConcluir(null));
            return;
        }

        String imagemPronta = null;
        boolean responderDoCache = false;
        synchronized (CACHE_LOCK) {
            if (IMAGENS_CACHE.containsKey(produtoId)) {
                imagemPronta = IMAGENS_CACHE.get(produtoId);
                responderDoCache = true;
            } else {
                List<AoObterImagem> callbacks = IMAGENS_EM_ANDAMENTO.get(produtoId);
                if (callbacks != null) {
                    callbacks.add(callback);
                    return;
                }
                callbacks = new ArrayList<>();
                callbacks.add(callback);
                IMAGENS_EM_ANDAMENTO.put(produtoId, callbacks);
            }
        }

        if (responderDoCache) {
            String url = imagemPronta;
            PRINCIPAL.post(() -> callback.aoConcluir(url));
            return;
        }

        REDE.execute(() -> {
            String url = obterImagemProduto(produtoId);
            List<AoObterImagem> callbacks;
            synchronized (CACHE_LOCK) {
                IMAGENS_CACHE.put(produtoId, url);
                callbacks = IMAGENS_EM_ANDAMENTO.remove(produtoId);
            }
            if (callbacks == null) return;
            PRINCIPAL.post(() -> {
                for (AoObterImagem listener : callbacks) {
                    listener.aoConcluir(url);
                }
            });
        });
    }

    private DetalheProduto obterDetalheCompleto(long produtoId) {
        try {
            Response<ProductFullResponse> resposta =
                    api.buscarProdutoCompleto(produtoId).execute();
            if (!resposta.isSuccessful() || resposta.body() == null) {
                return new DetalheProduto(null, null);
            }

            ProductFullResponse corpo = resposta.body();
            String textoRotulo = corpo.label == null
                    ? null
                    : rotuloParaExibicao(corpo.label.normalizedText);
            Long versaoId = corpo.currentVersion == null ? null : corpo.currentVersion.id;
            if (versaoId == null) {
                versaoId = buscarVersaoAtual(produtoId);
            }
            guardarVersaoAtual(produtoId, versaoId);
            String textoIngredientes = obterTextoIngredientes(versaoId);
            registrarDiagnostico("COMPOSITION_RESULT product=" + produtoId
                    + " version=" + versaoId
                    + " source=" + (textoIngredientes == null ? "LABEL_FALLBACK" : "CATALOG"));
            String texto = textoIngredientes == null ? textoRotulo : textoIngredientes;
            String urlFoto = primeiraFotoAtiva(corpo.photos);
            return new DetalheProduto(texto, urlFoto);
        } catch (IOException | RuntimeException e) {
            return new DetalheProduto(null, null);
        }
    }

    /**
     * Busca a composição persistida. O endpoint de product-ingredients só
     * retorna ids; os nomes INCI são resolvidos em paralelo no catálogo de
     * ingredientes e remontados na ordem do rótulo.
     */
    @Nullable
    private String obterTextoIngredientes(@Nullable Long versaoId) {
        if (versaoId == null || versaoId <= 0) {
            return null;
        }

        synchronized (CACHE_LOCK) {
            if (COMPOSICOES_CACHE.containsKey(versaoId)) {
                return COMPOSICOES_CACHE.get(versaoId);
            }
        }

        List<ProductIngredientResponse> vinculos;
        try {
            Response<List<ProductIngredientResponse>> resposta =
                    api.listarIngredientesDaVersao(versaoId).execute();
            if (!resposta.isSuccessful() || resposta.body() == null) {
                registrarDiagnostico("COMPOSITION_LINKS version=" + versaoId
                        + " count=0 http=" + resposta.code());
                return null;
            }
            vinculos = new ArrayList<>();
            for (ProductIngredientResponse vinculo : resposta.body()) {
                if (vinculo != null && vinculo.ingredientId != null
                        && vinculo.ingredientId > 0) {
                    vinculos.add(vinculo);
                }
            }
        } catch (IOException | RuntimeException e) {
            registrarDiagnostico("COMPOSITION_LINKS version=" + versaoId
                    + " count=0 reason=" + e.getClass().getSimpleName());
            return null;
        }

        registrarDiagnostico("COMPOSITION_LINKS version=" + versaoId
                + " count=" + vinculos.size());

        if (vinculos.isEmpty()) {
            return null;
        }

        vinculos.sort((esquerda, direita) -> Integer.compare(
                posicaoDoIngrediente(esquerda), posicaoDoIngrediente(direita)));

        List<Future<IngredientResponse>> consultas = new ArrayList<>();
        for (ProductIngredientResponse vinculo : vinculos) {
            consultas.add(REDE_INGREDIENTES.submit(
                    () -> obterIngrediente(vinculo.ingredientId)));
        }

        StringBuilder texto = new StringBuilder();
        int ingredientesResolvidos = 0;
        for (Future<IngredientResponse> consulta : consultas) {
            try {
                IngredientResponse ingrediente = consulta.get(30, TimeUnit.SECONDS);
                String nome = nomeDoIngrediente(ingrediente);
                if (nome.isEmpty()) {
                    continue;
                }
                if (texto.length() > 0) {
                    texto.append(", ");
                }
                texto.append(nome);
                ingredientesResolvidos++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException ignored) {
                // Um ingrediente indisponível não deve esconder os demais.
            }
        }

        registrarDiagnostico("COMPOSITION_RESOLVED version=" + versaoId
                + " count=" + ingredientesResolvidos);
        if (texto.length() == 0) {
            return null;
        }

        String composicao = texto.toString();
        synchronized (CACHE_LOCK) {
            COMPOSICOES_CACHE.put(versaoId, composicao);
        }
        return composicao;
    }

    @Nullable
    private IngredientResponse obterIngrediente(long ingredienteId) {
        try {
            Response<IngredientResponse> resposta =
                    api.buscarIngrediente(ingredienteId).execute();
            return resposta.isSuccessful() ? resposta.body() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private Long buscarVersaoAtual(long produtoId) {
        try {
            Response<ProductVersionResponse> resposta = api.versaoAtual(produtoId).execute();
            if (!resposta.isSuccessful() || resposta.body() == null) {
                return null;
            }
            return resposta.body().id;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void registrarDiagnostico(String mensagem) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, mensagem);
        }
    }

    private int posicaoDoIngrediente(ProductIngredientResponse vinculo) {
        return vinculo.position == null ? Integer.MAX_VALUE : vinculo.position;
    }

    private String nomeDoIngrediente(@Nullable IngredientResponse ingrediente) {
        if (ingrediente == null) {
            return "";
        }
        if (ingrediente.inciName != null && !ingrediente.inciName.trim().isEmpty()) {
            return ingrediente.inciName.trim();
        }
        return ingrediente.commonName == null ? "" : ingrediente.commonName.trim();
    }

    /**
     * Esse texto é um marcador interno salvo em cargas antigas, não uma
     * composição. Ele nunca deve aparecer para a pessoa usuária como se fosse
     * a lista de ingredientes.
     */
    @Nullable
    private String rotuloParaExibicao(@Nullable String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        if (semAcentos.contains("catalogo oficial")
                && semAcentos.contains("composicao integral")) {
            return null;
        }
        return texto.trim();
    }

    @Nullable
    private String obterImagemProduto(long produtoId) {
        try {
            Long versaoId = versaoAtualEmCache(produtoId);
            if (versaoId != null) {
                Response<List<MediaAssetResponse>> fotos =
                        api.listarFotosDaVersao(versaoId).execute();
                if (fotos.isSuccessful() && fotos.body() != null) {
                    String url = primeiraFotoAtiva(fotos.body());
                    if (url != null) {
                        return url;
                    }
                }
            }

            // Histórico e produtos abertos antes do catálogo não têm a versão
            // no cache; nesses casos o agregado completo continua sendo o
            // caminho compatível.
            Response<ProductFullResponse> resposta =
                    api.buscarProdutoCompleto(produtoId).execute();
            if (!resposta.isSuccessful() || resposta.body() == null) {
                return null;
            }
            return primeiraFotoAtiva(resposta.body().photos);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private Long versaoAtualEmCache(long produtoId) {
        synchronized (CACHE_LOCK) {
            return VERSOES_CACHE.get(produtoId);
        }
    }

    private void guardarImagem(long produtoId, @Nullable String urlFoto) {
        if (produtoId <= 0) return;
        synchronized (CACHE_LOCK) {
            // Uma falha transitória no carregamento do detalhe não deve
            // apagar uma URL válida já resolvida por outra tela.
            if (urlFoto != null || !IMAGENS_CACHE.containsKey(produtoId)) {
                IMAGENS_CACHE.put(produtoId, urlFoto);
            }
        }
    }

    /**
     * A primeira foto ACTIVE do tipo PRODUCT_PHOTO, na ordem de sortOrder.
     */
    @Nullable
    private String primeiraFotoAtiva(List<MediaAssetResponse> fotos) {
        if (fotos == null) {
            return null;
        }
        MediaAssetResponse escolhida = null;
        for (MediaAssetResponse foto : fotos) {
            if (foto == null) {
                continue;
            }
            boolean valida = "ACTIVE".equals(foto.status) && "PRODUCT_PHOTO".equals(foto.purpose);
            if (!valida) {
                continue;
            }
            if (escolhida == null || ordemDaFoto(foto) < ordemDaFoto(escolhida)) {
                escolhida = foto;
            }
        }
        return escolhida == null ? null : escolhida.url;
    }

    private int ordemDaFoto(MediaAssetResponse foto) {
        return foto.sortOrder == null ? Integer.MAX_VALUE : foto.sortOrder;
    }

    /**
     * A lista de produtos da API traz so o brandId/categoryId e nao traz nota
     * nenhuma. Para montar o card do jeito que a tela precisa (nome, marca,
     * categoria e nota) juntamos seis chamadas: produtos, marcas, categorias,
     * versoes, notas e o modelo de scoring ativo.
     *
     * A nota mora na versao atual do produto, nao no produto, e cada versao
     * pode ter uma nota por modelo de scoring: por isso o caminho e produto ->
     * versao atual -> nota do modelo ativo.
     *
     * Nenhuma depende do resultado da outra, entao as seis sao disparadas de
     * uma vez: o custo passa a ser o da chamada mais lenta, e nao a soma delas.
     */
    private List<Produto> montarCatalogo() throws IOException, FalhaApi {
        Future<List<ProductResponse>> pedidoProdutos =
                REDE.submit(
                        () -> exigir(
                                api.listarProdutos().execute(),
                                "produtos"
                        )
                );

        Future<List<BrandResponse>> pedidoMarcas =
                REDE.submit(
                        () -> exigir(
                                api.listarMarcas().execute(),
                                "marcas"
                        )
                );

        Future<List<ProductCategoryResponse>> pedidoCategorias =
                REDE.submit(
                        () -> exigir(
                                api.listarCategorias().execute(),
                                "categorias"
                        )
                );

        Future<List<ProductVersionResponse>> pedidoVersoes =
                REDE.submit(
                        () -> exigir(
                                api.listarVersoes().execute(),
                                "versoes"
                        )
                );

        Future<List<ProductScoreResponse>> pedidoNotas =
                REDE.submit(() -> exigir(api.listarNotas().execute(), "notas"));
        Future<Long> pedidoModeloAtivo = REDE.submit(() -> buscarModeloAtivoId(api));

        List<ProductResponse> produtos = esperar(pedidoProdutos);
        List<BrandResponse> marcas = esperar(pedidoMarcas);
        List<ProductCategoryResponse> categorias = esperar(pedidoCategorias);
        List<ProductVersionResponse> versoes = esperar(pedidoVersoes);
        List<ProductScoreResponse> notas = esperar(pedidoNotas);
        Long modeloAtivoId = esperarModeloAtivo(pedidoModeloAtivo);

        Map<Long, String> nomeDaMarca = new HashMap<>();
        for (BrandResponse marca : marcas) {

            if (marca != null
                    && marca.id != null) {

                nomeDaMarca.put(
                        marca.id,
                        marca.name
                );
            }
        }

        Map<Long, String> nomeDaCategoria =
                new HashMap<>();

        for (ProductCategoryResponse categoria : categorias) {

            if (categoria != null
                    && categoria.id != null) {

                nomeDaCategoria.put(
                        categoria.id,
                        categoria.name
                );
            }
        }

        Map<Long, Long> versaoAtualDoProduto =
                new HashMap<>();

        for (ProductVersionResponse versao : versoes) {

            if (versao != null
                    && Boolean.TRUE.equals(
                    versao.isCurrent
            )
                    && versao.productId != null
                    && versao.id != null) {

                versaoAtualDoProduto.put(
                        versao.productId,
                        versao.id
                );
            }
        }

        Map<Long, Integer> notaDaVersao =
                new HashMap<>();

        for (ProductScoreResponse nota : notas) {

            if (nota != null
                    && nota.productVersionId != null
                    && modeloAtivoId != null
                    && modeloAtivoId.equals(nota.scoringModelId)) {

                notaDaVersao.put(
                        nota.productVersionId,
                        nota.overallScore
                );
            }
        }

        List<Produto> catalogo =
                new ArrayList<>();

        for (ProductResponse produto : produtos) {

            if (produto == null
                    || produto.id == null) {
                continue;
            }

            if (Boolean.FALSE.equals(
                    produto.isActive
            )) {
                continue;
            }

            Long versaoAtual =
                    versaoAtualDoProduto.get(
                            produto.id
                    );

            Integer nota =
                    versaoAtual == null
                            ? null
                            : notaDaVersao.get(
                            versaoAtual
                    );

            String marca =
                    nomeDaMarca.get(
                            produto.brandId
                    );

            if (marca == null) {
                marca = "";
            }

            // A listagem nao traz media_assets. Os adapters resolvem a primeira
            // foto ativa sob demanda pelo endpoint leve de fotos da versao.
            guardarVersaoAtual(produto.id, versaoAtual);
            catalogo.add(new Produto(produto.id, produto.name, marca, nota, null,
                    produto.productCategoryId, nomeDaCategoria.get(produto.productCategoryId)));
        }

        return catalogo;
    }

    private void guardarVersaoAtual(long produtoId, @Nullable Long versaoId) {
        if (produtoId <= 0 || versaoId == null || versaoId <= 0) {
            return;
        }
        synchronized (CACHE_LOCK) {
            VERSOES_CACHE.put(produtoId, versaoId);
        }
    }

    /**
     * Sem modelo ativo (404) ou com a chamada fora do ar, o catalogo carrega
     * mesmo assim: melhor mostrar os produtos sem nota do que derrubar a busca
     * inteira por causa de uma configuracao de scoring.
     */
    @Nullable
    private Long buscarModeloAtivoId(VenusApi api) {
        try {
            Response<ScoringModelResponse> resposta = api.modeloAtivo().execute();
            if (!resposta.isSuccessful() || resposta.body() == null) {
                return null;
            }
            return resposta.body().id;
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private Long esperarModeloAtivo(Future<Long> pedido) {
        try {
            return pedido.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    /**
     * Devolve o resultado da chamada preservando o motivo da falha: quem chama
     * precisa distinguir "a API respondeu erro" de "nao deu para chegar nela".
     */
    private <T> List<T> esperar(Future<List<T>> pedido) throws IOException, FalhaApi {
        try {
            return pedido.get();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IOException(
                    "A busca foi interrompida.",
                    e
            );

        } catch (ExecutionException e) {

            Throwable causa =
                    e.getCause();

            if (causa instanceof FalhaApi) {
                throw (FalhaApi) causa;
            }

            if (causa instanceof IOException) {
                throw (IOException) causa;
            }

            throw new IOException(causa);
        }
    }

    private <T> List<T> exigir(
            Response<List<T>> resposta,
            String oQue
    ) throws FalhaApi {

        if (!resposta.isSuccessful()) {

            throw new FalhaApi(
                    "A API respondeu "
                            + resposta.code()
                            + " ao buscar "
                            + oQue
                            + "."
            );
        }

        List<T> corpo =
                resposta.body();

        return corpo == null
                ? Collections.emptyList()
                : corpo;
    }

    private static class FalhaApi
            extends Exception {

        FalhaApi(String mensagem) {
            super(mensagem);
        }
    }

    private static class DetalheProduto {
        final String textoRotulo;
        final String urlFoto;

        DetalheProduto(@Nullable String textoRotulo, @Nullable String urlFoto) {
            this.textoRotulo = textoRotulo;
            this.urlFoto = urlFoto;
        }
    }
}
