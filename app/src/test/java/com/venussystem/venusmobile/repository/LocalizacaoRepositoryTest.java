package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.testutil.ApiDeTeste;
import com.venussystem.venusmobile.testutil.SessaoFalsa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class LocalizacaoRepositoryTest {

    private MockWebServer server;
    private SharedPreferences prefs;
    private final AtomicBoolean permissao = new AtomicBoolean(true);
    private final AtomicLong agora = new AtomicLong(1_000_000L);
    private final AtomicReference<LeitorLocalizacao.Posicao> posicao = new AtomicReference<>(
            new LeitorLocalizacao.Posicao(-23.55, -46.63, "São Paulo", "São Paulo"));
    private LocalizacaoRepository repository;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        Context context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences("teste_localizacao", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        VenusApi api = ApiDeTeste.criar(server, VenusApi.class);
        UsuarioApiRepository usuarios = new UsuarioApiRepository(context, api, new SessaoFalsa());
        // O id ja esta guardado: o teste e so do envio da localizacao.
        context.getSharedPreferences("venus_usuario_api", Context.MODE_PRIVATE)
                .edit().putLong("id_uid-ana", 42L).commit();

        repository = new LocalizacaoRepository(prefs, usuarios, api, posicao::get,
                permissao::get, agora::get);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void comPermissao_enviaPosicaoParaOUsuario() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(204));

        assertTrue(repository.enviarAgora());

        RecordedRequest req = server.takeRequest();
        assertEquals("PATCH", req.getMethod());
        assertEquals("/api/users/42", req.getPath());
        JsonObject corpo = JsonParser.parseString(req.getBody().readUtf8()).getAsJsonObject();
        assertEquals(-23.55, corpo.get("latitude").getAsDouble(), 0.0);
        assertEquals(-46.63, corpo.get("longitude").getAsDouble(), 0.0);
        assertEquals("São Paulo", corpo.get("city").getAsString());
        assertEquals("São Paulo", corpo.get("state").getAsString());
    }

    @Test
    public void semPermissao_naoVaiNaRede() {
        permissao.set(false);

        assertFalse(repository.enviarAgora());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void depoisDeEnviar_soReenviaQuandoPassaAValidade() {
        server.enqueue(new MockResponse().setResponseCode(204));
        assertTrue(repository.enviarAgora());

        agora.addAndGet(LocalizacaoRepository.VALIDADE_MS - 1);
        assertFalse(repository.enviarAgora());
        assertEquals(1, server.getRequestCount());

        agora.addAndGet(1);
        server.enqueue(new MockResponse().setResponseCode(204));
        assertTrue(repository.enviarAgora());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void falhaDaApi_mantemPendenteParaProximaVez() {
        server.enqueue(new MockResponse().setResponseCode(503));
        assertFalse(repository.enviarAgora());

        server.enqueue(new MockResponse().setResponseCode(204));
        assertTrue(repository.enviarAgora());
    }

    @Test
    public void semPosicao_naoChamaAApi() {
        posicao.set(null);

        assertFalse(repository.enviarAgora());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void pedidoDePermissao_ficaLembrado() {
        assertFalse(repository.jaPediuPermissao());

        repository.marcarPediuPermissao();

        assertTrue(repository.jaPediuPermissao());
    }

    @Test
    public void arredondaParaDuasCasas() {
        assertEquals(-23.55, LocalizacaoRepository.arredondar(-23.5487), 0.0);
        assertEquals(-46.64, LocalizacaoRepository.arredondar(-46.6388), 0.0);
    }
}
