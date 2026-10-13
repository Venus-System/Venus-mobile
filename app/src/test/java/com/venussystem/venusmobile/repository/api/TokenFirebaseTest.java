package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.testutil.ApiDeTeste;
import com.venussystem.venusmobile.testutil.SessaoFalsa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TokenFirebaseTest {

    private MockWebServer server;
    private SessaoFalsa sessao;
    private OkHttpClient http;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        sessao = new SessaoFalsa();
        TokenFirebase token = new TokenFirebase(sessao);
        http = new OkHttpClient.Builder()
                .readTimeout(ApiDeTeste.LIMITE_SEGUNDOS, TimeUnit.SECONDS)
                .addInterceptor(token)
                .authenticator(token)
                .build();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private int chamar() throws IOException {
        Request pedido = new Request.Builder().url(server.url("/api/users/search")).build();
        try (Response resposta = http.newCall(pedido).execute()) {
            return resposta.code();
        }
    }

    private RecordedRequest proximoPedido() throws InterruptedException {
        return server.takeRequest(ApiDeTeste.LIMITE_SEGUNDOS, TimeUnit.SECONDS);
    }

    @Test
    public void pessoaLogada_mandaOTokenNoCabecalho() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        assertEquals(200, chamar());

        assertEquals("Bearer token-1", proximoPedido().getHeader("Authorization"));
        assertEquals(Collections.singletonList(false), sessao.pedidosDeToken);
    }

    @Test
    public void ninguemLogado_vaiSemTokenESemPedirAoFirebase() throws Exception {
        sessao.uid = null;
        server.enqueue(new MockResponse().setResponseCode(200));

        chamar();

        assertNull(proximoPedido().getHeader("Authorization"));
        assertTrue(sessao.pedidosDeToken.isEmpty());
    }

    @Test
    public void tokenRecusado_renovaERepeteUmaVez() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(200));

        assertEquals(200, chamar());

        assertEquals("Bearer token-1", proximoPedido().getHeader("Authorization"));
        assertEquals("Bearer token-2", proximoPedido().getHeader("Authorization"));
        assertEquals(Arrays.asList(false, true), sessao.pedidosDeToken);
    }

    @Test
    public void recusadoDeNovo_desisteNoSegundo401() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(401));

        assertEquals(401, chamar());

        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void firebaseSemRede_pedidoSegueSemToken() throws Exception {
        // O catalogo nao depende do token: falhar aqui derrubaria a busca de
        // produtos so porque o Firebase nao conseguiu renovar.
        sessao.falhaDeRede = new IOException("sem rede");
        server.enqueue(new MockResponse().setResponseCode(200));

        assertEquals(200, chamar());

        assertNull(proximoPedido().getHeader("Authorization"));
    }
}
