package com.venussystem.venusmobile.repository;

import com.google.gson.Gson;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.BrandResponse;
import com.venussystem.venusmobile.repository.api.dto.MediaAssetResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductCategoryResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductFullResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductLabelResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;
import com.venussystem.venusmobile.repository.api.dto.ScoringModelResponse;
import com.venussystem.venusmobile.testutil.FakeApiDispatcher;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarAte;
import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarLatch;
import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarValor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ProdutoRepositoryTest {

    private static final String PATH_PRODUTOS = "/api/products";
    private static final String PATH_MARCAS = "/api/brands";
    private static final String PATH_CATEGORIAS = "/api/product-categories";
    private static final String PATH_VERSOES = "/api/product-versions";
    private static final String PATH_NOTAS = "/api/product-scores";
    private static final String PATH_MODELO_ATIVO = "/api/scoring-models/active";
    private static final String PATH_FULL_PRODUTO_1 = "/api/products/1/full";

    private static final long MODELO_ATIVO = 1L;

    /** Produtos, marcas, categorias, versoes, notas e o modelo ativo. */
    private static final int REQUISICOES_POR_CARGA = 6;

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final Gson gson = new Gson();
    private Locale localeOriginal;
    private MockWebServer server;
    private ProdutoRepository repository;

    @Before
    public void setUp() throws IOException {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("pt", "BR"));
        ProdutoRepository.resetEstadoParaTeste();
        server = new MockWebServer();
        server.start();
        repository = new ProdutoRepository(criarApi(server));
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        ProdutoRepository.resetEstadoParaTeste();
        Locale.setDefault(localeOriginal);
    }

    private static VenusApi criarApi(MockWebServer server) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build();
        return new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VenusApi.class);
    }

    private MockResponse json(Object corpo) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(gson.toJson(corpo));
    }

    private void enfileirarPadrao(FakeApiDispatcher dispatcher, List<ProductResponse> produtos,
                                   List<BrandResponse> marcas, List<ProductCategoryResponse> categorias,
                                   List<ProductVersionResponse> versoes, List<ProductScoreResponse> notas) {
        dispatcher.em(PATH_PRODUTOS, json(produtos));
        dispatcher.em(PATH_MARCAS, json(marcas));
        dispatcher.em(PATH_CATEGORIAS, json(categorias));
        dispatcher.em(PATH_VERSOES, json(versoes));
        dispatcher.em(PATH_NOTAS, json(notas));
        dispatcher.em(PATH_MODELO_ATIVO, json(modelo(MODELO_ATIVO)));
    }

    private static ScoringModelResponse modelo(long id) {
        ScoringModelResponse r = new ScoringModelResponse();
        r.id = id;
        r.name = "Recomendacao Geral";
        r.isActive = true;
        return r;
    }

    private static MediaAssetResponse foto(String url, Integer ordem, String status) {
        MediaAssetResponse r = new MediaAssetResponse();
        r.url = url;
        r.sortOrder = ordem;
        r.status = status;
        r.purpose = "PRODUCT_PHOTO";
        return r;
    }

    private static ProductFullResponse full(String textoRotulo, List<MediaAssetResponse> fotos) {
        ProductFullResponse r = new ProductFullResponse();
        if (textoRotulo != null) {
            ProductLabelResponse rotulo = new ProductLabelResponse();
            rotulo.normalizedText = textoRotulo;
            r.label = rotulo;
        }
        r.photos = fotos;
        return r;
    }

    private static ProductResponse produto(long id, long brandId, Long categoryId, String nome, Boolean ativo) {
        ProductResponse r = new ProductResponse();
        r.id = id;
        r.brandId = brandId;
        r.productCategoryId = categoryId;
        r.name = nome;
        r.isActive = ativo;
        return r;
    }

    private static BrandResponse marca(long id, String nome) {
        BrandResponse r = new BrandResponse();
        r.id = id;
        r.name = nome;
        return r;
    }

    private static ProductCategoryResponse categoria(long id, String nome) {
        ProductCategoryResponse r = new ProductCategoryResponse();
        r.id = id;
        r.name = nome;
        return r;
    }

    private static ProductVersionResponse versao(long id, long produtoId, boolean atual) {
        ProductVersionResponse r = new ProductVersionResponse();
        r.id = id;
        r.productId = produtoId;
        r.isCurrent = atual;
        return r;
    }

    private static ProductScoreResponse nota(long versaoId, long modeloId, int overall) {
        ProductScoreResponse r = new ProductScoreResponse();
        r.productVersionId = versaoId;
        r.scoringModelId = modeloId;
        r.overallScore = overall;
        return r;
    }

    /**
     * A trava EM_ANDAMENTO pode nao ter sido liberada pela carga anterior quando
     * o teste pede a proxima, e ai carregar() e no-op. Repetir ate a requisicao
     * sair tira o teste da dependencia desse tempo.
     */
    private void carregarAteRequisitar(boolean forcar) {
        int antes = server.getRequestCount();
        aguardarAte(() -> {
            repository.carregar(forcar);
            return server.getRequestCount() > antes;
        });
    }

    private List<Produto> carregarEEsperar(FakeApiDispatcher dispatcher) {
        server.setDispatcher(dispatcher);
        repository.carregar(false);
        aguardarValor(repository.getCatalogo(), valor -> valor != null);
        aguardarValor(repository.getCarregando(), Boolean.FALSE::equals);
        return repository.getCatalogo().getValue();
    }

    // ---- Montagem do catalogo ----

    @Test
    public void montarCatalogo_resolveMarcaCategoriaENotaPelaVersaoAtual() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Arrays.asList(versao(1000L, 1L, true), versao(999L, 1L, false)),
                Arrays.asList(nota(1000L, 1L, 87), nota(999L, 1L, 999)));

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertEquals(1, catalogo.size());
        Produto p = catalogo.get(0);
        assertEquals("Produto A", p.getName());
        assertEquals("Marca X", p.getBrandName());
        assertEquals((Long) 100L, p.getCategoryId());
        assertEquals("Categoria Y", p.getCategoryName());
        assertEquals((Integer) 87, p.getOverallScore());
    }

    @Test
    public void semVersaoAtual_notaFicaNull() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.singletonList(versao(999L, 1L, false)),
                Collections.singletonList(nota(999L, 1L, 50)));

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertNull(catalogo.get(0).getOverallScore());
    }

    @Test
    public void semNotaParaVersaoAtual_notaFicaNull() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.singletonList(versao(1000L, 1L, true)),
                Collections.emptyList());

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertNull(catalogo.get(0).getOverallScore());
    }

    @Test
    public void produtoInativo_ficaForaDoCatalogo() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Arrays.asList(
                        produto(1L, 10L, 100L, "Ativo", true),
                        produto(2L, 10L, 100L, "Inativo", false),
                        produto(3L, 10L, 100L, "Sem info", null)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.emptyList(), Collections.emptyList());

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        List<String> nomes = new ArrayList<>();
        for (Produto p : catalogo) {
            nomes.add(p.getName());
        }
        assertTrue(nomes.contains("Ativo"));
        assertTrue(nomes.contains("Sem info"));
        assertFalse(nomes.contains("Inativo"));
    }

    @Test
    public void marcaInexistente_ficaStringVazia() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 999L, 100L, "Produto A", true)),
                Collections.emptyList(),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.emptyList(), Collections.emptyList());

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertEquals("", catalogo.get(0).getBrandName());
    }

    @Test
    public void categoriaInexistente_ficaNull() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 999L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertNull(catalogo.get(0).getCategoryName());
    }

    // ---- Erros HTTP e falha de rede ----

    @Test
    public void erroHttpEmCadaEndpoint_publicaMensagemNaoPublicaCatalogoEZeraCarregando() {
        String[][] casos = {
                {PATH_PRODUTOS, "produtos"},
                {PATH_MARCAS, "marcas"},
                {PATH_CATEGORIAS, "categorias"},
                {PATH_VERSOES, "versoes"},
                {PATH_NOTAS, "notas"},
        };

        for (String[] caso : casos) {
            String pathComErro = caso[0];
            String recurso = caso[1];

            ProdutoRepository.resetEstadoParaTeste();
            FakeApiDispatcher dispatcher = new FakeApiDispatcher();
            enfileirarPadrao(dispatcher, Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            dispatcher.em(pathComErro, new MockResponse().setResponseCode(500));
            server.setDispatcher(dispatcher);

            carregarAteRequisitar(true);
            aguardarValor(repository.getErro(), valor -> valor != null);

            assertEquals("Falhou para: " + recurso,
                    "A API respondeu 500 ao buscar " + recurso + ".",
                    repository.getErro().getValue());
            assertNull("Catalogo nao deveria ter sido publicado para: " + recurso,
                    repository.getCatalogo().getValue());

            aguardarValor(repository.getCarregando(), Boolean.FALSE::equals);
        }
    }

    @Test
    public void falhaDeConexao_publicaMensagemGenerica() throws IOException {
        server.shutdown();

        repository.carregar(true);
        aguardarValor(repository.getErro(), valor -> valor != null);

        assertEquals("Nao foi possivel falar com o servidor. Verifique sua conexao e tente de novo.",
                repository.getErro().getValue());
    }

    @Test
    public void corpoNulo_resultaEmListaVazia() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        dispatcher.em(PATH_PRODUTOS, new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("null"));
        dispatcher.em(PATH_MARCAS, json(Collections.emptyList()));
        dispatcher.em(PATH_CATEGORIAS, json(Collections.emptyList()));
        dispatcher.em(PATH_VERSOES, json(Collections.emptyList()));
        dispatcher.em(PATH_NOTAS, json(Collections.emptyList()));

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertTrue(catalogo.isEmpty());
    }

    // ---- Cache ----

    @Test
    public void carregarFalseAposSucesso_naoFazNovaRequisicao() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        carregarEEsperar(dispatcher);
        int requisicoesAposPrimeiraCarga = server.getRequestCount();

        repository.carregar(false);

        assertEquals(requisicoesAposPrimeiraCarga, server.getRequestCount());
    }

    @Test
    public void carregarTrueAposSucesso_fazNovaRequisicao() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        carregarEEsperar(dispatcher);
        int requisicoesAposPrimeiraCarga = server.getRequestCount();

        carregarAteRequisitar(true);
        aguardarAte(() -> server.getRequestCount()
                >= requisicoesAposPrimeiraCarga + REQUISICOES_POR_CARGA);

        assertEquals(requisicoesAposPrimeiraCarga + REQUISICOES_POR_CARGA,
                server.getRequestCount());
    }

    @Test
    public void carregarFalseAposErro_tentaNovamente() {
        FakeApiDispatcher dispatcherComErro = new FakeApiDispatcher();
        enfileirarPadrao(dispatcherComErro, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        dispatcherComErro.em(PATH_PRODUTOS, new MockResponse().setResponseCode(500));
        server.setDispatcher(dispatcherComErro);
        carregarAteRequisitar(false);
        aguardarValor(repository.getErro(), valor -> valor != null);
        aguardarValor(repository.getCarregando(), Boolean.FALSE::equals);
        aguardarAte(() -> server.getRequestCount() >= REQUISICOES_POR_CARGA);
        int requisicoesAposErro = server.getRequestCount();

        FakeApiDispatcher dispatcherOk = new FakeApiDispatcher();
        enfileirarPadrao(dispatcherOk, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        server.setDispatcher(dispatcherOk);

        carregarAteRequisitar(false);
        aguardarValor(repository.getCatalogo(), valor -> valor != null);

        assertEquals(requisicoesAposErro + REQUISICOES_POR_CARGA, server.getRequestCount());
    }

    // ---- buscarNoCache ----

    @Test
    public void buscarNoCache_encontrado() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.emptyList(), Collections.emptyList());
        carregarEEsperar(dispatcher);

        Produto encontrado = repository.buscarNoCache(1L);

        assertNotNull(encontrado);
        assertEquals("Produto A", encontrado.getName());
    }

    @Test
    public void buscarNoCache_naoEncontrado() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        carregarEEsperar(dispatcher);

        assertNull(repository.buscarNoCache(999L));
    }

    @Test
    public void buscarNoCache_catalogoAindaNulo_retornaNull() {
        assertNull(repository.buscarNoCache(1L));
    }

    // ---- buscarDetalheProduto (/full) ----

    private String[] buscarDetalhe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] resultado = new String[]{"nao-nulo", "nao-nulo"};
        Thread[] threadCallback = new Thread[1];
        repository.buscarDetalheProduto(1L, (textoRotulo, urlFoto) -> {
            resultado[0] = textoRotulo;
            resultado[1] = urlFoto;
            threadCallback[0] = Thread.currentThread();
            latch.countDown();
        });
        aguardarLatch(latch, com.venussystem.venusmobile.testutil.LiveDataEspera.TIMEOUT_PADRAO_MS);
        assertEquals(android.os.Looper.getMainLooper().getThread(), threadCallback[0]);
        return resultado;
    }

    @Test
    public void buscarDetalhe_sucesso_devolveRotuloEPrimeiraFotoPorOrdem() throws InterruptedException {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        dispatcher.em(PATH_FULL_PRODUTO_1, json(full("Aqua, Glycerin", Arrays.asList(
                foto("https://cdn/segunda.jpg", 2, "ACTIVE"),
                foto("https://cdn/primeira.jpg", 1, "ACTIVE")))));
        server.setDispatcher(dispatcher);

        String[] resultado = buscarDetalhe();

        assertEquals("Aqua, Glycerin", resultado[0]);
        assertEquals("https://cdn/primeira.jpg", resultado[1]);
    }

    @Test
    public void buscarDetalhe_ignoraFotoQueNaoEstaAtiva() throws InterruptedException {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        dispatcher.em(PATH_FULL_PRODUTO_1, json(full("Aqua", Arrays.asList(
                foto("https://cdn/pendente.jpg", 1, "PENDING"),
                foto("https://cdn/ativa.jpg", 2, "ACTIVE")))));
        server.setDispatcher(dispatcher);

        String[] resultado = buscarDetalhe();

        assertEquals("https://cdn/ativa.jpg", resultado[1]);
    }

    @Test
    public void buscarDetalhe_semFoto_devolveUrlNula() throws InterruptedException {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        dispatcher.em(PATH_FULL_PRODUTO_1, json(full("Aqua", Collections.emptyList())));
        server.setDispatcher(dispatcher);

        String[] resultado = buscarDetalhe();

        assertEquals("Aqua", resultado[0]);
        assertNull(resultado[1]);
    }

    @Test
    public void buscarDetalhe_semRotulo_devolveTextoNulo() throws InterruptedException {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        dispatcher.em(PATH_FULL_PRODUTO_1, json(full(null,
                Collections.singletonList(foto("https://cdn/foto.jpg", 1, "ACTIVE")))));
        server.setDispatcher(dispatcher);

        String[] resultado = buscarDetalhe();

        assertNull(resultado[0]);
        assertEquals("https://cdn/foto.jpg", resultado[1]);
    }

    @Test
    public void buscarDetalhe_404_devolveTudoNulo() throws InterruptedException {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        dispatcher.em(PATH_FULL_PRODUTO_1, new MockResponse().setResponseCode(404));
        server.setDispatcher(dispatcher);

        String[] resultado = buscarDetalhe();

        assertNull(resultado[0]);
        assertNull(resultado[1]);
    }

    @Test
    public void buscarDetalhe_falhaDeRede_devolveTudoNulo() throws InterruptedException, IOException {
        server.shutdown();

        String[] resultado = buscarDetalhe();

        assertNull(resultado[0]);
        assertNull(resultado[1]);
    }

    // ---- Modelo de scoring ativo / cargas simultaneas ----

    @Test
    public void duasNotasParaMesmaVersao_valeADoModeloAtivo() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.singletonList(versao(1000L, 1L, true)),
                Arrays.asList(nota(1000L, MODELO_ATIVO, 60), nota(1000L, 2L, 90)));

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertEquals((Integer) 60, catalogo.get(0).getOverallScore());
    }

    @Test
    public void notaDeOutroModelo_naoEntraNoCatalogo() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.singletonList(versao(1000L, 1L, true)),
                Collections.singletonList(nota(1000L, 2L, 90)));

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertNull(catalogo.get(0).getOverallScore());
    }

    @Test
    public void semModeloAtivo_catalogoCarregaSemNota() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher,
                Collections.singletonList(produto(1L, 10L, 100L, "Produto A", true)),
                Collections.singletonList(marca(10L, "Marca X")),
                Collections.singletonList(categoria(100L, "Categoria Y")),
                Collections.singletonList(versao(1000L, 1L, true)),
                Collections.singletonList(nota(1000L, MODELO_ATIVO, 87)));
        dispatcher.em(PATH_MODELO_ATIVO, new MockResponse().setResponseCode(404));

        List<Produto> catalogo = carregarEEsperar(dispatcher);

        assertEquals(1, catalogo.size());
        assertNull(catalogo.get(0).getOverallScore());
        assertNull(repository.getErro().getValue());
    }

    @Test
    public void duasCargasSimultaneas_fazApenasUmaRodadaDeRequisicoes() {
        FakeApiDispatcher dispatcher = new FakeApiDispatcher();
        enfileirarPadrao(dispatcher, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        server.setDispatcher(dispatcher);

        repository.carregar(true);
        repository.carregar(true);
        aguardarValor(repository.getCatalogo(), valor -> valor != null);

        assertEquals(6, server.getRequestCount());
    }
}
