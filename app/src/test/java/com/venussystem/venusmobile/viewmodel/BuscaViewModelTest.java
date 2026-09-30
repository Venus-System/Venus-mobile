package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;
import com.venussystem.venusmobile.model.CategoriaContagem;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.BuscaRecenteRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.BrandResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductCategoryResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;
import com.venussystem.venusmobile.testutil.FakeApiDispatcher;

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
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarValor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class BuscaViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final Gson gson = new Gson();
    private Locale localeOriginal;
    private MockWebServer server;
    private Application application;

    @Before
    public void setUp() throws IOException {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("pt", "BR"));
        ProdutoRepository.resetEstadoParaTeste();
        server = new MockWebServer();
        server.start();
        application = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        ProdutoRepository.resetEstadoParaTeste();
        Locale.setDefault(localeOriginal);
    }

    private MockResponse json(Object corpo) {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(gson.toJson(corpo));
    }

    private static ProductResponse produto(long id, long brandId, Long categoryId, String nome) {
        ProductResponse r = new ProductResponse();
        r.id = id;
        r.brandId = brandId;
        r.productCategoryId = categoryId;
        r.name = nome;
        r.isActive = true;
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

    private VenusApi criarApi() {
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

    private BuscaViewModel criarViewModel(FakeApiDispatcher dispatcher) {
        server.setDispatcher(dispatcher);
        ProdutoRepository repository = new ProdutoRepository(criarApi());
        BuscaRecenteRepository recentes = new BuscaRecenteRepository(application);
        BuscaViewModel viewModel = new BuscaViewModel(application, repository, recentes);
        aguardarValor(repository.getCatalogo(), valor -> valor != null);
        return viewModel;
    }

    private FakeApiDispatcher dispatcherPadrao() {
        FakeApiDispatcher d = new FakeApiDispatcher();
        List<ProductResponse> produtos = Arrays.asList(
                produto(1L, 10L, 100L, "Creme Hidratante Nivea"),
                produto(2L, 10L, 100L, "Locao Corporal"),
                produto(3L, 10L, 100L, "Balm Labial"),
                produto(4L, 20L, 200L, "Shampoo Anticaspa"),
                produto(5L, 20L, 200L, "Condicionador"));
        List<BrandResponse> marcas = Arrays.asList(marca(10L, "Nivea"), marca(20L, "Cliniq"));
        List<ProductCategoryResponse> categorias = Arrays.asList(
                categoria(100L, "Hidratante"), categoria(200L, "Shampoo"));
        d.em("/api/products", json(produtos));
        d.em("/api/brands", json(marcas));
        d.em("/api/product-categories", json(categorias));
        d.em("/api/product-versions", json(Collections.<ProductVersionResponse>emptyList()));
        d.em("/api/product-scores", json(Collections.<ProductScoreResponse>emptyList()));
        return d;
    }

    private List<String> nomes(List<Produto> produtos) {
        List<String> resultado = new ArrayList<>();
        for (Produto p : produtos) {
            resultado.add(p.getName());
        }
        return resultado;
    }

    private List<CategoriaContagem> categorias(BuscaViewModel viewModel) {
        aguardarValor(viewModel.getCategorias(), valor -> valor != null);
        return viewModel.getCategorias().getValue();
    }

    // ---- Landing ----

    @Test
    public void semTermoESemCategoria_ehLandingEListaVazia() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        assertTrue(viewModel.isLanding());
        assertTrue(viewModel.getProdutos().getValue() == null
                || viewModel.getProdutos().getValue().isEmpty());
    }

    @Test
    public void termoSoComEspacos_continuaLanding() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.buscar("    ");

        assertTrue(viewModel.isLanding());
        assertTrue(viewModel.getProdutos().getValue().isEmpty());
    }

    // ---- Texto ----

    @Test
    public void buscaPorMarca_casoInsensitivoComEspacos() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.buscar("  NIVEA  ");

        assertFalse(viewModel.isLanding());
        List<String> resultado = nomes(viewModel.getProdutos().getValue());
        assertEquals(3, resultado.size());
        assertTrue(resultado.containsAll(Arrays.asList(
                "Creme Hidratante Nivea", "Locao Corporal", "Balm Labial")));
    }

    @Test
    public void buscaPorNome_encontraSoOProdutoCujoNomeBate() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.buscar("Corporal");

        assertEquals(Collections.singletonList("Locao Corporal"),
                nomes(viewModel.getProdutos().getValue()));
    }

    @Test
    public void buscaSemResultado_listaVaziaENaoEhLanding() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.buscar("xyz-nao-existe");

        assertFalse(viewModel.isLanding());
        assertTrue(viewModel.getProdutos().getValue().isEmpty());
    }

    @Test
    public void termoVazioComLimparCategoria_voltaAoLanding() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());
        viewModel.selecionarCategoria(100L);
        viewModel.buscar("Balm");

        viewModel.buscar("");
        viewModel.limparCategoria();

        assertTrue(viewModel.isLanding());
        assertTrue(viewModel.getProdutos().getValue().isEmpty());
    }

    // ---- Categoria ----

    @Test
    public void categoriaSozinha_saiDoLandingEFiltraPorCategoria() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.selecionarCategoria(100L);

        assertFalse(viewModel.isLanding());
        List<String> resultado = nomes(viewModel.getProdutos().getValue());
        assertEquals(3, resultado.size());
        assertTrue(resultado.containsAll(Arrays.asList(
                "Creme Hidratante Nivea", "Locao Corporal", "Balm Labial")));
    }

    @Test
    public void textoECategoriaJuntos_filtramComE() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.selecionarCategoria(100L);
        viewModel.buscar("Balm");

        assertEquals(Collections.singletonList("Balm Labial"),
                nomes(viewModel.getProdutos().getValue()));
    }

    // ---- Filtrar antes do catalogo chegar ----

    @Test
    public void filtrarAntesDoCatalogoChegar_naoQuebra() {
        FakeApiDispatcher dispatcher = dispatcherPadrao();
        dispatcher.em("/api/products", json(Collections.singletonList(
                        produto(1L, 10L, 100L, "Creme Hidratante Nivea")))
                .setBodyDelay(500, TimeUnit.MILLISECONDS));
        server.setDispatcher(dispatcher);

        ProdutoRepository repository = new ProdutoRepository(criarApi());
        BuscaRecenteRepository recentes = new BuscaRecenteRepository(application);
        BuscaViewModel viewModel = new BuscaViewModel(application, repository, recentes);

        viewModel.buscar("nivea");
        viewModel.selecionarCategoria(100L);

        aguardarValor(viewModel.getProdutos(), valor -> valor != null && !valor.isEmpty());
        assertEquals(1, viewModel.getProdutos().getValue().size());
    }

    // ---- Chips ----

    @Test
    public void chip_respeitaMinimoDeProdutosPorCategoria() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        List<CategoriaContagem> chips = categorias(viewModel);

        List<String> nomesChips = new ArrayList<>();
        for (CategoriaContagem c : chips) {
            nomesChips.add(c.getNome());
        }
        assertTrue(nomesChips.contains("Hidratante"));
        assertFalse(nomesChips.contains("Shampoo"));
    }

    @Test
    public void chip_categoriaComIdNulo_ficaFora() {
        FakeApiDispatcher d = new FakeApiDispatcher();
        List<ProductResponse> produtos = Arrays.asList(
                produto(1L, 10L, null, "A"), produto(2L, 10L, null, "B"), produto(3L, 10L, null, "C"));
        d.em("/api/products", json(produtos));
        d.em("/api/brands", json(Collections.singletonList(marca(10L, "Nivea"))));
        d.em("/api/product-categories", json(Collections.<ProductCategoryResponse>emptyList()));
        d.em("/api/product-versions", json(Collections.<ProductVersionResponse>emptyList()));
        d.em("/api/product-scores", json(Collections.<ProductScoreResponse>emptyList()));

        BuscaViewModel viewModel = criarViewModel(d);

        assertTrue(categorias(viewModel).isEmpty());
    }

    @Test
    public void chip_categoriaComNomeEmBranco_ficaFora() {
        FakeApiDispatcher d = new FakeApiDispatcher();
        List<ProductResponse> produtos = Arrays.asList(
                produto(1L, 10L, 300L, "A"), produto(2L, 10L, 300L, "B"), produto(3L, 10L, 300L, "C"));
        d.em("/api/products", json(produtos));
        d.em("/api/brands", json(Collections.singletonList(marca(10L, "Nivea"))));
        d.em("/api/product-categories", json(Collections.singletonList(categoria(300L, "   "))));
        d.em("/api/product-versions", json(Collections.<ProductVersionResponse>emptyList()));
        d.em("/api/product-scores", json(Collections.<ProductScoreResponse>emptyList()));

        BuscaViewModel viewModel = criarViewModel(d);

        assertTrue(categorias(viewModel).isEmpty());
    }

    @Test
    public void chip_ordenaPorQuantidadeDecrescenteENomeCrescenteNoEmpate() {
        FakeApiDispatcher d = new FakeApiDispatcher();
        List<ProductResponse> produtos = Arrays.asList(
                produto(1L, 10L, 1L, "A1"), produto(2L, 10L, 1L, "A2"), produto(3L, 10L, 1L, "A3"),
                produto(4L, 10L, 2L, "B1"), produto(5L, 10L, 2L, "B2"), produto(6L, 10L, 2L, "B3"),
                produto(7L, 10L, 3L, "C1"), produto(8L, 10L, 3L, "C2"), produto(9L, 10L, 3L, "C3"),
                produto(10L, 10L, 3L, "C4"), produto(11L, 10L, 3L, "C5"));
        d.em("/api/products", json(produtos));
        d.em("/api/brands", json(Collections.singletonList(marca(10L, "Nivea"))));
        d.em("/api/product-categories", json(Arrays.asList(
                categoria(1L, "Zeta"), categoria(2L, "Alfa"), categoria(3L, "Beta"))));
        d.em("/api/product-versions", json(Collections.<ProductVersionResponse>emptyList()));
        d.em("/api/product-scores", json(Collections.<ProductScoreResponse>emptyList()));

        BuscaViewModel viewModel = criarViewModel(d);
        List<CategoriaContagem> chips = categorias(viewModel);

        assertEquals(3, chips.size());
        assertEquals("Beta", chips.get(0).getNome());
        assertEquals("Alfa", chips.get(1).getNome());
        assertEquals("Zeta", chips.get(2).getNome());
    }

    // ---- Recentes ----

    @Test
    public void buscar_naoGravaNoHistorico() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.buscar("creme hidratante");

        assertTrue(viewModel.getTermosRecentes().getValue().isEmpty());
    }

    @Test
    public void confirmarBusca_gravaNoHistorico() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.confirmarBusca("creme hidratante");

        assertEquals(Collections.singletonList("creme hidratante"),
                viewModel.getTermosRecentes().getValue());
    }

    @Test
    public void registrarTermoAtual_comTermoVazio_naoGrava() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());
        viewModel.buscar("");

        viewModel.registrarTermoAtual();

        assertTrue(viewModel.getTermosRecentes().getValue().isEmpty());
    }

    @Test
    public void removerRecente_atualizaLista() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());
        viewModel.confirmarBusca("abc");
        viewModel.confirmarBusca("def");

        viewModel.removerRecente("abc");

        assertEquals(Collections.singletonList("def"), viewModel.getTermosRecentes().getValue());
    }

    @Test
    public void termoComUmCaractere_naoEhGravado() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        viewModel.confirmarBusca("a");

        assertTrue(viewModel.getTermosRecentes().getValue().isEmpty());
    }

    @Test
    public void limiteDeOitoRecentes_removeOMaisAntigo() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());

        for (int i = 1; i <= 9; i++) {
            viewModel.confirmarBusca("termo" + i);
        }

        List<String> recentes = viewModel.getTermosRecentes().getValue();
        assertEquals(8, recentes.size());
        assertEquals("termo9", recentes.get(0));
        assertFalse(recentes.contains("termo1"));
    }

    @Test
    public void termoRepetido_voltaAoTopoSemDuplicar() {
        BuscaViewModel viewModel = criarViewModel(dispatcherPadrao());
        viewModel.confirmarBusca("abc");
        viewModel.confirmarBusca("def");

        viewModel.confirmarBusca("ABC");

        assertEquals(Arrays.asList("ABC", "def"), viewModel.getTermosRecentes().getValue());
    }
}
