package com.venussystem.venusmobile.repository;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.venussystem.venusmobile.repository.api.VenusIaApi;
import com.venussystem.venusmobile.testutil.ApiDeTeste;
import com.venussystem.venusmobile.testutil.SessaoFalsa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import static com.venussystem.venusmobile.testutil.LiveDataEspera.TIMEOUT_PADRAO_MS;
import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarLatch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ConversaRepositoryTest {

    private static final String CONVERSA = "conversa-1";

    private MockWebServer server;
    private SessaoFalsa sessao;
    private Long idUsuario;

    /** Guarda o que o callback recebeu, para o teste conferir depois. */
    private static class Retorno implements ConversaRepository.AoResponder {
        final CountDownLatch chegou = new CountDownLatch(1);
        String resposta;
        ConversaRepository.Falha falha;

        @Override
        public void aoResponder(String resposta) {
            this.resposta = resposta;
            chegou.countDown();
        }

        @Override
        public void aoFalhar(ConversaRepository.Falha falha) {
            this.falha = falha;
            chegou.countDown();
        }
    }

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        sessao = new SessaoFalsa();
        idUsuario = 42L;
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private ConversaRepository repositorio() {
        return repositorio(ApiDeTeste.criar(server, VenusIaApi.class));
    }

    private ConversaRepository repositorio(VenusIaApi api) {
        return new ConversaRepository(api, sessao, () -> idUsuario);
    }

    private Retorno enviar(ConversaRepository repositorio, String mensagem) {
        Retorno retorno = new Retorno();
        repositorio.enviar(mensagem, CONVERSA, retorno);
        aguardarLatch(retorno.chegou, TIMEOUT_PADRAO_MS);
        return retorno;
    }

    private static MockResponse resposta(int codigo, String corpo) {
        return new MockResponse()
                .setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo);
    }

    private static MockResponse respostaDaVenus(String texto) {
        return resposta(200, "{\"resposta\":\"" + texto + "\",\"conversation_id\":\"" + CONVERSA + "\"}");
    }

    private static JsonObject corpo(RecordedRequest pedido) {
        return JsonParser.parseString(pedido.getBody().readUtf8()).getAsJsonObject();
    }

    // ---- Sucesso ----

    @Test
    public void mensagem_chegaComTokenConversaEIdDoUsuario() throws InterruptedException {
        server.enqueue(respostaDaVenus("Oi! Posso ajudar."));

        Retorno retorno = enviar(repositorio(), "oi");

        assertEquals("Oi! Posso ajudar.", retorno.resposta);
        assertNull(retorno.falha);

        RecordedRequest pedido = server.takeRequest();
        assertEquals("POST", pedido.getMethod());
        assertEquals("/v1/chat", pedido.getPath());
        assertEquals("Bearer token-1", pedido.getHeader("Authorization"));

        JsonObject json = corpo(pedido);
        assertEquals("oi", json.get("mensagem").getAsString());
        assertEquals(CONVERSA, json.get("conversation_id").getAsString());
        assertEquals(42L, json.get("usuario_id_postgres").getAsLong());
    }

    @Test
    public void semIdDoUsuarioAinda_mandaSoAMensagem() throws InterruptedException {
        idUsuario = null;
        server.enqueue(respostaDaVenus("Oi!"));

        enviar(repositorio(), "oi");

        JsonObject json = corpo(server.takeRequest());
        assertFalse(json.has("usuario_id_postgres"));
        assertEquals(CONVERSA, json.get("conversation_id").getAsString());
    }

    // ---- Token ----

    @Test
    public void tokenRecusado_renovaETentaUmaVez() throws InterruptedException {
        server.enqueue(resposta(401, "{\"detail\":\"token inválido ou expirado\"}"));
        server.enqueue(respostaDaVenus("Agora foi."));

        Retorno retorno = enviar(repositorio(), "oi");

        assertEquals("Agora foi.", retorno.resposta);
        assertEquals(Arrays.asList(false, true), sessao.pedidosDeToken);
        server.takeRequest();
        assertEquals("Bearer token-2", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void tokenRecusadoDuasVezes_eSessaoExpirada() {
        server.enqueue(resposta(401, "{\"detail\":\"token inválido ou expirado\"}"));
        server.enqueue(resposta(401, "{\"detail\":\"token inválido ou expirado\"}"));

        Retorno retorno = enviar(repositorio(), "oi");

        assertEquals(ConversaRepository.Falha.SESSAO_EXPIRADA, retorno.falha);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void semSessaoValida_naoChegaAChamarAApi() {
        sessao.token = null;

        Retorno retorno = enviar(repositorio(), "oi");

        assertEquals(ConversaRepository.Falha.SESSAO_EXPIRADA, retorno.falha);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void semRedeParaPegarOToken_eSemConexao() {
        sessao.falhaDeRede = new IOException("sem rede");

        Retorno retorno = enviar(repositorio(), "oi");

        assertEquals(ConversaRepository.Falha.SEM_CONEXAO, retorno.falha);
    }

    // ---- Falhas da API ----

    @Test
    public void erroDoServidor_eFalhaDoServidor() {
        server.enqueue(resposta(502, "{\"detail\":\"Falha ao processar a mensagem\"}"));

        Retorno retorno = enviar(repositorio(), "oi");

        assertEquals(ConversaRepository.Falha.FALHA_SERVIDOR, retorno.falha);
    }

    @Test
    public void respostaVazia_eFalhaDoServidor() {
        server.enqueue(resposta(200, "{\"resposta\":\"  \",\"conversation_id\":\"x\"}"));

        Retorno retorno = enviar(repositorio(), "oi");

        assertEquals(ConversaRepository.Falha.FALHA_SERVIDOR, retorno.falha);
    }

    @Test
    public void demorouDemais_naoRepeteAPergunta() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        VenusIaApi apiImpaciente = ApiDeTeste.criar(server, VenusIaApi.class, 1L);
        Retorno retorno = enviar(repositorio(apiImpaciente), "oi");

        assertEquals(ConversaRepository.Falha.DEMOROU, retorno.falha);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void servidorForaDoAr_eSemConexao() throws IOException {
        VenusIaApi api = ApiDeTeste.criar(server, VenusIaApi.class);
        server.shutdown();

        Retorno retorno = enviar(repositorio(api), "oi");

        assertEquals(ConversaRepository.Falha.SEM_CONEXAO, retorno.falha);
    }

    @Test
    public void semUrlConfigurada_avisaSemIrNaRede() {
        Retorno retorno = enviar(repositorio(null), "oi");

        assertEquals(ConversaRepository.Falha.NAO_CONFIGURADO, retorno.falha);
        assertEquals(0, server.getRequestCount());
        assertEquals(0, sessao.pedidosDeToken.size());
    }
}
