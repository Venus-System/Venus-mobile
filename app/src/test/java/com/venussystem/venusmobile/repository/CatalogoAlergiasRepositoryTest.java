package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.testutil.ApiDeTeste;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class CatalogoAlergiasRepositoryTest {

    private MockWebServer server;
    private Context context;
    private VenusApi api;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        context = ApplicationProvider.getApplicationContext();
        api = ApiDeTeste.criar(server, VenusApi.class);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private CatalogoAlergiasRepository novoRepositorio() {
        // Executor direto: o aviso chega na propria thread do repositorio.
        return new CatalogoAlergiasRepository(context, api, Runnable::run);
    }

    private void apiResponde(int codigo, String corpo) {
        server.enqueue(new MockResponse()
                .setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo));
    }

    @Test
    public void semRespostaDaApi_mostraAsDezDaListaReserva() {
        List<String> nomes = novoRepositorio().nomes();

        assertEquals(10, nomes.size());
        assertTrue(nomes.contains("Lanolina"));
        assertTrue(nomes.contains("Álcool"));
        assertFalse("saiu da lista", nomes.contains("Níquel"));
        assertFalse("saiu da lista", nomes.contains("Fragrância"));
    }

    @Test
    public void catalogoDaApi_substituiAReservaEFicaGuardado() {
        apiResponde(200, "[{\"id\":1,\"allergyName\":\"Lanolina\"},"
                + "{\"id\":2,\"allergyName\":\" Soja \"},{\"id\":3,\"allergyName\":\"Soja\"}]");

        assertEquals(Arrays.asList("Lanolina", "Soja"), novoRepositorio().buscarAgora());

        // Outra tela, outra instancia: ja abre com o catalogo, sem esperar a API.
        assertEquals(Arrays.asList("Lanolina", "Soja"), novoRepositorio().nomes());
    }

    @Test
    public void apiComErro_mantemOQueJaMostrava() {
        apiResponde(503, "{\"status\":503}");

        CatalogoAlergiasRepository catalogo = novoRepositorio();
        assertNull(catalogo.buscarAgora());
        assertEquals(10, catalogo.nomes().size());
    }

    @Test
    public void catalogoVazio_naoApagaAsOpcoes() {
        apiResponde(200, "[]");

        CatalogoAlergiasRepository catalogo = novoRepositorio();
        assertNull(catalogo.buscarAgora());
        assertEquals(10, catalogo.nomes().size());
    }

    @Test
    public void atualizarEmSegundoPlano_avisaComOsNomesNovos() throws InterruptedException {
        apiResponde(200, "[{\"id\":9,\"allergyName\":\"Látex\"}]");
        CountDownLatch avisou = new CountDownLatch(1);
        AtomicReference<List<String>> recebidos = new AtomicReference<>();

        novoRepositorio().atualizarEmSegundoPlano(nomes -> {
            recebidos.set(nomes);
            avisou.countDown();
        });

        assertTrue(avisou.await(ApiDeTeste.LIMITE_SEGUNDOS * 2, TimeUnit.SECONDS));
        assertEquals(Arrays.asList("Látex"), recebidos.get());
    }
}
