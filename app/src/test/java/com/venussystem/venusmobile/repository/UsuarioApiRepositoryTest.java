package com.venussystem.venusmobile.repository;

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

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class UsuarioApiRepositoryTest {

    private MockWebServer server;
    private SessaoFalsa sessao;
    private UsuarioApiRepository repository;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        sessao = new SessaoFalsa();
        repository = new UsuarioApiRepository(ApplicationProvider.getApplicationContext(),
                ApiDeTeste.criar(server, VenusApi.class), sessao);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(int codigo, String corpo) {
        return new MockResponse()
                .setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo);
    }

    private static MockResponse fatia(String... usuarios) {
        return json(200, "{\"content\":[" + String.join(",", usuarios) + "],\"empty\":"
                + (usuarios.length == 0) + "}");
    }

    private static String usuario(long id, String uid) {
        return "{\"id\":" + id + ",\"firebaseUid\":\"" + uid + "\",\"name\":\"Ana\",\"status\":\"ACTIVE\"}";
    }

    // ---- Buscar ----

    @Test
    public void usuarioJaCadastrado_devolveOIdDaBusca() throws InterruptedException {
        server.enqueue(fatia(usuario(42, "uid-ana")));

        assertEquals(Long.valueOf(42), repository.obterIdSincrono());

        RecordedRequest busca = server.takeRequest();
        assertEquals("GET", busca.getMethod());
        assertEquals("/api/users/search?firebaseUid=uid-ana", busca.getPath());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void segundaChamada_usaOIdGuardadoSemIrNaRede() {
        server.enqueue(fatia(usuario(42, "uid-ana")));
        repository.obterIdSincrono();

        assertEquals(Long.valueOf(42), repository.obterIdSincrono());
        assertEquals(Long.valueOf(42), repository.idEmCache());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void buscaTrazOutraPessoa_naoUsaOIdDela() throws InterruptedException {
        server.enqueue(fatia(usuario(5, "uid-de-outra-pessoa")));
        server.enqueue(json(201, usuario(77, "uid-ana")));

        assertEquals(Long.valueOf(77), repository.obterIdSincrono());

        server.takeRequest();
        assertEquals("POST", server.takeRequest().getMethod());
    }

    // ---- Criar ----

    @Test
    public void usuarioNovo_criaComNomeEmailEStatusAtivo() throws InterruptedException {
        server.enqueue(fatia());
        server.enqueue(json(201, usuario(7, "uid-ana")));

        assertEquals(Long.valueOf(7), repository.obterIdSincrono());

        server.takeRequest();
        RecordedRequest criacao = server.takeRequest();
        assertEquals("POST", criacao.getMethod());
        assertEquals("/api/users", criacao.getPath());

        JsonObject corpo = JsonParser.parseString(criacao.getBody().readUtf8()).getAsJsonObject();
        assertEquals("uid-ana", corpo.get("firebaseUid").getAsString());
        assertEquals("Ana Souza", corpo.get("name").getAsString());
        assertEquals("ana@venus.com", corpo.get("email").getAsString());
        assertEquals("ACTIVE", corpo.get("status").getAsString());
        assertFalse("o app nunca manda senha", corpo.has("password"));
    }

    @Test
    public void semNomeNoFirebase_usaOComecoDoEmail() throws InterruptedException {
        sessao.nome = "  ";
        server.enqueue(fatia());
        server.enqueue(json(201, usuario(7, "uid-ana")));

        repository.obterIdSincrono();

        server.takeRequest();
        JsonObject corpo = JsonParser.parseString(server.takeRequest().getBody().readUtf8())
                .getAsJsonObject();
        assertEquals("ana", corpo.get("name").getAsString());
    }

    @Test
    public void criacaoDa409_buscaDeNovoEUsaOQueJaExiste() {
        server.enqueue(fatia());
        server.enqueue(json(409, "{\"status\":409,\"message\":\"firebaseUid ja cadastrado\"}"));
        server.enqueue(fatia(usuario(9, "uid-ana")));

        assertEquals(Long.valueOf(9), repository.obterIdSincrono());
        assertEquals(3, server.getRequestCount());
    }

    // ---- Falhas ----

    @Test
    public void buscaComErro_naoCriaParaNaoDuplicar() {
        server.enqueue(json(503, "{\"status\":503}"));

        assertNull(repository.obterIdSincrono());
        assertEquals(1, server.getRequestCount());
        assertNull(repository.idEmCache());
    }

    @Test
    public void semRede_devolveNullENaoGuardaNada() throws IOException {
        server.shutdown();

        assertNull(repository.obterIdSincrono());
        assertNull(repository.idEmCache());
    }

    @Test
    public void ninguemLogado_naoVaiNaRede() {
        sessao.uid = null;

        assertNull(repository.obterIdSincrono());
        assertNull(repository.idEmCache());
        assertEquals(0, server.getRequestCount());
    }

    // ---- Cache por UID ----

    @Test
    public void trocarDeConta_naoReaproveitaOIdDaOutraPessoa() {
        server.enqueue(fatia(usuario(42, "uid-ana")));
        repository.obterIdSincrono();

        sessao.uid = "uid-bia";
        assertNull(repository.idEmCache());

        server.enqueue(fatia(usuario(43, "uid-bia")));
        assertEquals(Long.valueOf(43), repository.obterIdSincrono());
    }

    @Test
    public void esquecerId_fazAProximaChamadaBuscarDeNovo() {
        server.enqueue(fatia(usuario(42, "uid-ana")));
        repository.obterIdSincrono();

        repository.esquecerId();
        server.enqueue(fatia(usuario(50, "uid-ana")));

        assertEquals(Long.valueOf(50), repository.obterIdSincrono());
        assertEquals(2, server.getRequestCount());
    }
}
