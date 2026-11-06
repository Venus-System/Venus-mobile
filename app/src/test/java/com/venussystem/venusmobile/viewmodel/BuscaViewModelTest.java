package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.model.FaixaNota;
import com.venussystem.venusmobile.model.FiltroProdutos;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ProdutoSecao;
import com.venussystem.venusmobile.repository.FiltroRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.testutil.CatalogoApi;
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

import okhttp3.mockwebserver.MockWebServer;

import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarValor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class BuscaViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Locale localeOriginal;
    private MockWebServer server;
    private Application application;
    private FiltroRepository filtros;
    private final List<BuscaViewModel> criados = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("pt", "BR"));
        ProdutoRepository.resetEstadoParaTeste();
        FiltroRepository.resetParaTeste();
        server = new MockWebServer();
        server.start();
        application = ApplicationProvider.getApplicationContext();
        filtros = new FiltroRepository();
    }

    @After
    public void tearDown() throws IOException {
        for (BuscaViewModel viewModel : criados) {
            viewModel.onCleared();
        }
        server.shutdown();
        ProdutoRepository.resetEstadoParaTeste();
        FiltroRepository.resetParaTeste();
        Locale.setDefault(localeOriginal);
    }

    private FakeApiDispatcher catalogoPadrao() {
        return CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .marca(20L, "Cliniq", null)
                .marca(30L, "Natura", true)
                .categoria(100L, "Hidratante")
                .categoria(200L, "Shampoo")
                .produto(1L, 10L, 100L, "Creme Hidratante Nivea", 85)
                .produto(2L, 10L, 100L, "Locao Corporal", 45)
                .produto(3L, 10L, 100L, "Balm Labial", null)
                .produto(4L, 20L, 200L, "Shampoo Anticaspa", 70)
                .produto(5L, 20L, 200L, "Condicionador", 20)
                .produto(6L, 30L, 100L, "Sabonete Ekos", 90)
                .dispatcher();
    }

    private BuscaViewModel criarViewModel(FakeApiDispatcher dispatcher) {
        server.setDispatcher(dispatcher);
        ProdutoRepository repository = new ProdutoRepository(CatalogoApi.criarApi(server));
        BuscaViewModel viewModel = new BuscaViewModel(application, repository, filtros);
        criados.add(viewModel);
        aguardarValor(repository.getCatalogo(), valor -> valor != null);
        aguardarValor(viewModel.getResultados(), valor -> valor != null);
        return viewModel;
    }

    private static List<Long> ids(List<Produto> produtos) {
        List<Long> resultado = new ArrayList<>();
        for (Produto p : produtos) {
            resultado.add(p.getId());
        }
        return resultado;
    }

    private static List<String> nomes(List<Produto> produtos) {
        List<String> resultado = new ArrayList<>();
        for (Produto p : produtos) {
            resultado.add(p.getName());
        }
        return resultado;
    }

    private static FiltroProdutos filtro() {
        return FiltroProdutos.vazio();
    }

    @Test
    public void semTermo_mostraOHistoricoEOsResultadosFicamVazios() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        assertTrue(viewModel.isMostrandoHistorico());
        assertTrue(viewModel.getResultados().getValue().isEmpty());
    }

    @Test
    public void termoSoComEspacos_continuaMostrandoOHistorico() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        viewModel.buscar("    ");

        assertTrue(viewModel.isMostrandoHistorico());
        assertTrue(viewModel.getResultados().getValue().isEmpty());
    }

    @Test
    public void apagarOTexto_voltaAoHistorico() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());
        viewModel.buscar("Balm");
        assertFalse(viewModel.isMostrandoHistorico());

        viewModel.buscar("");

        assertTrue(viewModel.isMostrandoHistorico());
        assertTrue(viewModel.getResultados().getValue().isEmpty());
    }

    @Test
    public void buscaPorMarca_casoInsensitivoComEspacos() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        viewModel.buscar("  NIVEA  ");

        assertFalse(viewModel.isMostrandoHistorico());
        List<String> resultado = nomes(viewModel.getResultados().getValue());
        assertEquals(3, resultado.size());
        assertTrue(resultado.containsAll(Arrays.asList(
                "Creme Hidratante Nivea", "Locao Corporal", "Balm Labial")));
    }

    @Test
    public void buscaPorNome_encontraSoOProdutoCujoNomeBate() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        viewModel.buscar("Corporal");

        assertEquals(Collections.singletonList("Locao Corporal"),
                nomes(viewModel.getResultados().getValue()));
    }

    @Test
    public void buscaSemResultado_listaVaziaENaoVoltaAoHistorico() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        viewModel.buscar("xyz-nao-existe");

        assertFalse(viewModel.isMostrandoHistorico());
        assertTrue(viewModel.getResultados().getValue().isEmpty());
    }

    @Test
    public void sugestoes_vemDaMaiorNotaParaAMenor_eSemNotaPorUltimo() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        assertEquals(Arrays.asList(6L, 1L, 4L, 2L, 5L, 3L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void sugestoes_comNotasIguais_mantemAOrdemDoCatalogo() {
        BuscaViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .produto(1L, 10L, 100L, "A", 50)
                .produto(2L, 10L, 100L, "B", 50)
                .produto(3L, 10L, 100L, "C", 50)
                .produto(4L, 10L, 100L, "D", null)
                .produto(5L, 10L, 100L, "E", null)
                .dispatcher());

        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void sugestoes_naoDependemDoTextoDigitado() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        viewModel.buscar("Balm");

        assertEquals(6, viewModel.getSugestoes().getValue().size());
    }

    @Test
    public void sugestoesDaHome_agrupamPorCategoriaEOrdenamPelaQuantidade() {
        BuscaViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Marca", false)
                .categoria(100L, "Hidratante")
                .categoria(200L, "Shampoo")
                .produto(1L, 10L, 100L, "Zeta", 90)
                .produto(2L, 10L, 100L, "Alfa", 90)
                .produto(3L, 10L, 100L, "Beta", 20)
                .produto(4L, 10L, 200L, "Shampoo", 80)
                .produto(5L, 10L, null, "Sem categoria", 100)
                .dispatcher());

        List<ProdutoSecao> secoes = viewModel.getSugestoesPorCategoria().getValue();

        assertEquals(Arrays.asList("Hidratante", "Shampoo", "Sem categoria"), nomesSecoes(secoes));
        assertEquals(Arrays.asList("Alfa", "Zeta", "Beta"),
                nomes(secoes.get(0).getProdutos()));
        assertEquals(Collections.singletonList("Sem categoria"),
                nomes(secoes.get(2).getProdutos()));
    }

    @Test
    public void sugestoesDaHome_naoDuplicamIdsEOrdenamSecoes() {
        List<Produto> produtos = Arrays.asList(
                new Produto(1L, "A", "M", 50, null, 100L, "A"),
                new Produto(1L, "A repetido", "M", 100, null, 200L, "B"),
                new Produto(2L, "B", "M", 80, null, 200L, "B"),
                new Produto(3L, "C", "M", 70, null, 200L, "B"));

        List<ProdutoSecao> secoes = BuscaViewModel.agruparPorCategoria(produtos);

        assertEquals(Arrays.asList("B", "A"), nomesSecoes(secoes));
        assertEquals(Arrays.asList("B", "C"), nomes(secoes.get(0).getProdutos()));
    }

    @Test
    public void sugestoesDaHome_preservamNomeLongoDaCategoriaSemAlterarAgrupamento() {
        String categoria = "Gel de limpeza facial para pele sensível";
        List<ProdutoSecao> secoes = BuscaViewModel.agruparPorCategoria(Collections.singletonList(
                new Produto(8L, "Produto", "Marca", 80, null, 300L, categoria)));

        assertEquals(Collections.singletonList(categoria), nomesSecoes(secoes));
        assertEquals(Collections.singletonList("Produto"), nomes(secoes.get(0).getProdutos()));
    }

    @Test
    public void sugestoesDaHome_aplicamFiltroAntesDeAgrupar() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comCategoria(200L, true));

        List<ProdutoSecao> secoes = viewModel.getSugestoesPorCategoria().getValue();
        assertEquals(1, secoes.size());
        assertEquals("Shampoo", secoes.get(0).getNome());
        assertEquals(Arrays.asList(4L, 5L), ids(secoes.get(0).getProdutos()));
    }

    private static List<String> nomesSecoes(List<ProdutoSecao> secoes) {
        List<String> nomes = new ArrayList<>();
        for (ProdutoSecao secao : secoes) {
            nomes.add(secao.getNome());
        }
        return nomes;
    }

    @Test
    public void filtroPorMarca_valeNasSugestoes() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comMarca(20L, true));

        assertEquals(Arrays.asList(4L, 5L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void filtroPorCategoria_valeNasSugestoes() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comCategoria(200L, true));

        assertEquals(Arrays.asList(4L, 5L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void filtroPorFaixa_boaEMedia() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comFaixa(FaixaNota.BOA, true));
        assertEquals(Arrays.asList(6L, 1L, 4L), ids(viewModel.getSugestoes().getValue()));

        filtros.aplicar(filtro().comFaixa(FaixaNota.MEDIA, true));
        assertEquals(Collections.singletonList(2L), ids(viewModel.getSugestoes().getValue()));

        filtros.aplicar(filtro().comFaixa(FaixaNota.RUIM, true));
        assertEquals(Collections.singletonList(5L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void filtroPorFaixa_limitesSao70e40_eSemNotaFicaFora() {
        BuscaViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .produto(1L, 10L, 100L, "setenta", 70)
                .produto(2L, 10L, 100L, "sessenta e nove", 69)
                .produto(3L, 10L, 100L, "quarenta", 40)
                .produto(4L, 10L, 100L, "trinta e nove", 39)
                .produto(5L, 10L, 100L, "sem nota", null)
                .dispatcher());

        filtros.aplicar(filtro().comFaixa(FaixaNota.BOA, true));
        assertEquals(Collections.singletonList(1L), ids(viewModel.getSugestoes().getValue()));

        filtros.aplicar(filtro().comFaixa(FaixaNota.MEDIA, true));
        assertEquals(Arrays.asList(2L, 3L), ids(viewModel.getSugestoes().getValue()));

        filtros.aplicar(filtro().comFaixa(FaixaNota.RUIM, true));
        assertEquals(Collections.singletonList(4L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void filtroSoBrasileiras_deixaSoAsMarcasQueAApiDizSerBrasileiras() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comSoBrasileiras(true));

        assertEquals(Collections.singletonList(6L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void dentroDeUmGrupoValeQualquerOpcao() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comMarca(10L, true).comMarca(30L, true));

        assertEquals(Arrays.asList(6L, 1L, 2L, 3L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void entreGruposTodosPrecisamBater() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro()
                .comCategoria(100L, true)
                .comFaixa(FaixaNota.BOA, true));

        assertEquals(Arrays.asList(6L, 1L), ids(viewModel.getSugestoes().getValue()));
    }

    @Test
    public void filtroEOTextoJuntos_filtramComE() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comCategoria(100L, true));
        viewModel.buscar("Balm");

        assertEquals(Collections.singletonList("Balm Labial"),
                nomes(viewModel.getResultados().getValue()));

        viewModel.buscar("Shampoo");
        assertTrue(viewModel.getResultados().getValue().isEmpty());
    }

    @Test
    public void filtroNaoMexeNoEstadoDeHistorico() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comMarca(10L, true));

        assertTrue(viewModel.isMostrandoHistorico());
        assertTrue(viewModel.getResultados().getValue().isEmpty());
    }

    @Test
    public void filtroAplicadoNaHome_continuaValendoNosResultadosDaBusca() {
        FakeApiDispatcher dispatcher = catalogoPadrao();
        BuscaViewModel home = criarViewModel(dispatcher);
        filtros.aplicar(filtro().comCategoria(200L, true));

        BuscaViewModel busca = criarViewModel(dispatcher);
        busca.buscar("o");

        assertEquals(Arrays.asList(4L, 5L), ids(busca.getResultados().getValue()));
        assertEquals(Arrays.asList(4L, 5L), ids(home.getSugestoes().getValue()));
        assertTrue(busca.isFiltroAtivo());
    }

    @Test
    public void limparOFiltro_devolveOCatalogoInteiro() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());
        filtros.aplicar(filtro().comMarca(10L, true));
        assertTrue(viewModel.isFiltroAtivo());

        filtros.limpar();

        assertFalse(viewModel.isFiltroAtivo());
        assertEquals(6, viewModel.getSugestoes().getValue().size());
    }

    @Test
    public void filtroSemProdutoNenhum_deixaAListaVazia() {
        BuscaViewModel viewModel = criarViewModel(catalogoPadrao());

        filtros.aplicar(filtro().comMarca(999L, true));

        assertTrue(viewModel.getSugestoes().getValue().isEmpty());
        assertTrue(viewModel.temCatalogo());
    }

    @Test
    public void filtrarAntesDoCatalogoChegar_naoQuebra() {
        CatalogoApi catalogo = CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .produto(1L, 10L, 100L, "Creme Hidratante Nivea", 85);
        FakeApiDispatcher dispatcher = catalogo.dispatcher();
        dispatcher.em("/api/products",
                catalogo.respostaProdutos().setBodyDelay(500, TimeUnit.MILLISECONDS));
        server.setDispatcher(dispatcher);

        ProdutoRepository repository = new ProdutoRepository(CatalogoApi.criarApi(server));
        BuscaViewModel viewModel = new BuscaViewModel(application, repository, filtros);
        criados.add(viewModel);

        viewModel.buscar("nivea");
        filtros.aplicar(filtro().comCategoria(100L, true));

        aguardarValor(viewModel.getResultados(), valor -> valor != null && !valor.isEmpty());
        assertEquals(1, viewModel.getResultados().getValue().size());
    }
}
