package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.model.CategoriaContagem;
import com.venussystem.venusmobile.model.FaixaNota;
import com.venussystem.venusmobile.model.FiltroProdutos;
import com.venussystem.venusmobile.model.MarcaFiltro;
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

import okhttp3.mockwebserver.MockWebServer;

import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarValor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class FiltroViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Locale localeOriginal;
    private MockWebServer server;
    private Application application;
    private FiltroRepository filtros;
    private final List<FiltroViewModel> criados = new ArrayList<>();

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
        for (FiltroViewModel viewModel : criados) {
            viewModel.onCleared();
        }
        server.shutdown();
        ProdutoRepository.resetEstadoParaTeste();
        FiltroRepository.resetParaTeste();
        Locale.setDefault(localeOriginal);
    }

    private FiltroViewModel criarViewModel(FakeApiDispatcher dispatcher) {
        server.setDispatcher(dispatcher);
        ProdutoRepository repository = new ProdutoRepository(CatalogoApi.criarApi(server));
        FiltroViewModel viewModel = new FiltroViewModel(application, repository, filtros);
        criados.add(viewModel);
        aguardarValor(repository.getCatalogo(), valor -> valor != null);
        aguardarValor(viewModel.getMarcas(), valor -> valor != null);
        return viewModel;
    }

    private static List<String> nomesDasCategorias(FiltroViewModel viewModel) {
        List<String> nomes = new ArrayList<>();
        for (CategoriaContagem c : viewModel.getCategorias().getValue()) {
            nomes.add(c.getNome());
        }
        return nomes;
    }

    private static List<String> nomesDasMarcas(FiltroViewModel viewModel) {
        List<String> nomes = new ArrayList<>();
        for (MarcaFiltro m : viewModel.getMarcas().getValue()) {
            nomes.add(m.getNome());
        }
        return nomes;
    }

    @Test
    public void categoria_respeitaOMinimoDeTresProdutos() {
        FiltroViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .categoria(100L, "Hidratante")
                .categoria(200L, "Shampoo")
                .produto(1L, 10L, 100L, "A", 50)
                .produto(2L, 10L, 100L, "B", 50)
                .produto(3L, 10L, 100L, "C", 50)
                .produto(4L, 10L, 200L, "D", 50)
                .produto(5L, 10L, 200L, "E", 50)
                .dispatcher());

        List<String> nomes = nomesDasCategorias(viewModel);

        assertTrue(nomes.contains("Hidratante"));
        assertFalse(nomes.contains("Shampoo"));
    }

    @Test
    public void categoria_comIdNulo_ficaFora() {
        FiltroViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .produto(1L, 10L, null, "A", null)
                .produto(2L, 10L, null, "B", null)
                .produto(3L, 10L, null, "C", null)
                .dispatcher());

        assertTrue(viewModel.getCategorias().getValue().isEmpty());
    }

    @Test
    public void categoria_comNomeEmBranco_ficaFora() {
        FiltroViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .categoria(300L, "   ")
                .produto(1L, 10L, 300L, "A", null)
                .produto(2L, 10L, 300L, "B", null)
                .produto(3L, 10L, 300L, "C", null)
                .dispatcher());

        assertTrue(viewModel.getCategorias().getValue().isEmpty());
    }

    @Test
    public void categoria_ordenaPorQuantidadeDecrescenteENomeCrescenteNoEmpate() {
        FiltroViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .categoria(1L, "Zeta")
                .categoria(2L, "Alfa")
                .categoria(3L, "Beta")
                .produto(1L, 10L, 1L, "A1", null).produto(2L, 10L, 1L, "A2", null)
                .produto(3L, 10L, 1L, "A3", null)
                .produto(4L, 10L, 2L, "B1", null).produto(5L, 10L, 2L, "B2", null)
                .produto(6L, 10L, 2L, "B3", null)
                .produto(7L, 10L, 3L, "C1", null).produto(8L, 10L, 3L, "C2", null)
                .produto(9L, 10L, 3L, "C3", null).produto(10L, 10L, 3L, "C4", null)
                .produto(11L, 10L, 3L, "C5", null)
                .dispatcher());

        assertEquals(Arrays.asList("Beta", "Alfa", "Zeta"), nomesDasCategorias(viewModel));
    }

    @Test
    public void marcas_vemDoCatalogoEmOrdemAlfabeticaSemDiferenciarCaixa() {
        FiltroViewModel viewModel = criarViewModel(CatalogoApi.novo()
                .marca(10L, "nivea", false)
                .marca(20L, "Cliniq", false)
                .marca(30L, "Avon", false)
                .marca(40L, "Sem produto nenhum", false)
                .produto(1L, 10L, 100L, "A", null)
                .produto(2L, 20L, 100L, "B", null)
                .produto(3L, 30L, 100L, "C", null)
                .dispatcher());

        assertEquals(Arrays.asList("Avon", "Cliniq", "nivea"), nomesDasMarcas(viewModel));
    }

    @Test
    public void marcaBrasileira_apareceQuandoOCatalogoTemAlguma() {
        FiltroViewModel comBrasileira = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .marca(30L, "Natura", true)
                .produto(1L, 10L, 100L, "A", null)
                .produto(2L, 30L, 100L, "B", null)
                .dispatcher());
        assertTrue(comBrasileira.getTemMarcaBrasileira().getValue());
    }

    @Test
    public void marcaBrasileira_naoAparecendoNoCatalogo_ficaEscondida() {
        FiltroViewModel semBrasileira = criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .marca(20L, "Cliniq", null)
                .produto(1L, 10L, 100L, "A", null)
                .produto(2L, 20L, 100L, "B", null)
                .dispatcher());
        assertFalse(semBrasileira.getTemMarcaBrasileira().getValue());
    }

    private FiltroViewModel comCatalogoSimples() {
        return criarViewModel(CatalogoApi.novo()
                .marca(10L, "Nivea", false)
                .produto(1L, 10L, 100L, "A", 50)
                .dispatcher());
    }

    @Test
    public void rascunho_comecaComOFiltroQueEstaValendo() {
        filtros.aplicar(FiltroProdutos.vazio().comMarca(10L, true).comSoBrasileiras(true));

        FiltroViewModel viewModel = comCatalogoSimples();

        assertEquals(filtros.atual(), viewModel.getRascunho().getValue());
    }

    @Test
    public void escolhas_mudamSoORascunhoAteAplicar() {
        FiltroViewModel viewModel = comCatalogoSimples();

        viewModel.alternarMarca(10L, true);
        viewModel.alternarCategoria(100L, true);
        viewModel.alternarFaixa(FaixaNota.BOA, true);
        viewModel.definirSoBrasileiras(true);

        FiltroProdutos rascunho = viewModel.getRascunho().getValue();
        assertTrue(rascunho.getMarcas().contains(10L));
        assertTrue(rascunho.getCategorias().contains(100L));
        assertTrue(rascunho.getFaixas().contains(FaixaNota.BOA));
        assertTrue(rascunho.isSoBrasileiras());
        assertFalse(filtros.atual().isAtivo());

        viewModel.aplicar();

        assertEquals(rascunho, filtros.atual());
    }

    @Test
    public void desmarcarUmaOpcao_tiraSoEla() {
        FiltroViewModel viewModel = comCatalogoSimples();
        viewModel.alternarMarca(10L, true);
        viewModel.alternarMarca(20L, true);

        viewModel.alternarMarca(10L, false);

        assertEquals(Collections.singleton(20L), viewModel.getRascunho().getValue().getMarcas());
    }

    @Test
    public void limpar_zeraORascunhoMasNaoMexeNoFiltroQueEstaValendo() {
        filtros.aplicar(FiltroProdutos.vazio().comMarca(10L, true));
        FiltroViewModel viewModel = comCatalogoSimples();

        viewModel.limpar();

        assertFalse(viewModel.getRascunho().getValue().isAtivo());
        assertTrue(filtros.atual().isAtivo());
    }

    @Test
    public void limparEAplicar_tiraOFiltroDoApp() {
        filtros.aplicar(FiltroProdutos.vazio().comMarca(10L, true));
        FiltroViewModel viewModel = comCatalogoSimples();

        viewModel.limpar();
        viewModel.aplicar();

        assertFalse(filtros.atual().isAtivo());
    }
}
