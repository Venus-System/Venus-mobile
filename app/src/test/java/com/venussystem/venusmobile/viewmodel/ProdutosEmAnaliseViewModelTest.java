package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.repository.ProdutosEmAnaliseRepository;
import com.venussystem.venusmobile.repository.UsuarioApiRepository;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.testutil.ApiDeTeste;
import com.venussystem.venusmobile.testutil.ApiPorRota;
import com.venussystem.venusmobile.testutil.SessaoFalsa;
import com.venussystem.venusmobile.viewmodel.ProdutosEmAnaliseViewModel.Estado;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

import okhttp3.mockwebserver.MockWebServer;

import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarValor;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ProdutosEmAnaliseViewModelTest {

    private static final String BUSCA_USUARIO = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String SCANS = "GET /api/scan-sessions/user/42?page=0&size=50";
    private static final String UM_SCAN =
            "{\"content\":[{\"id\":\"1\",\"status\":\"PENDING_REVIEW\"}],\"last\":true}";

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private MockWebServer server;
    private ApiPorRota api;
    private ProdutosEmAnaliseRepository repositorio;
    private Application application;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        application = ApplicationProvider.getApplicationContext();
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        UsuarioApiRepository usuarios =
                new UsuarioApiRepository(application, venusApi, new SessaoFalsa());
        repositorio = new ProdutosEmAnaliseRepository(usuarios, venusApi, Runnable::run);

        api.em(BUSCA_USUARIO, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void aoAbrir_buscaOsProdutosEFicaPronto() {
        api.em(SCANS, 200, UM_SCAN);

        ProdutosEmAnaliseViewModel viewModel =
                new ProdutosEmAnaliseViewModel(application, repositorio);

        aguardarValor(viewModel.getEstado(), estado -> estado == Estado.PRONTO);
        assertEquals(1, viewModel.getProdutos().getValue().size());
    }

    @Test
    public void apiComErro_ficaEmErro() {
        api.em(SCANS, 500, "{}");

        ProdutosEmAnaliseViewModel viewModel =
                new ProdutosEmAnaliseViewModel(application, repositorio);

        aguardarValor(viewModel.getEstado(), estado -> estado == Estado.ERRO);
    }

    @Test
    public void tentarDeNovo_depoisDoErro_buscaOutraVez() {
        api.em(SCANS, 500, "{}");
        ProdutosEmAnaliseViewModel viewModel =
                new ProdutosEmAnaliseViewModel(application, repositorio);
        aguardarValor(viewModel.getEstado(), estado -> estado == Estado.ERRO);
        api.em(SCANS, 200, UM_SCAN);

        viewModel.carregar();

        aguardarValor(viewModel.getEstado(), estado -> estado == Estado.PRONTO);
        assertEquals(1, viewModel.getProdutos().getValue().size());
    }
}
