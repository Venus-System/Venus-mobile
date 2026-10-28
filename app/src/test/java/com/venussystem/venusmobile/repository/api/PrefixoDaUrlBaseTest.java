package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.ChatRequest;
import com.venussystem.venusmobile.repository.api.dto.ClassificationRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Na AWS a IA e a Classificacao ficam atras de um prefixo (/ai e
 * /classificacao). Se alguma rota for escrita com "/" na frente, o Retrofit
 * descarta o prefixo ("/v1/chat" vira http://host/v1/chat) e o servidor
 * responde 404, sem nenhum erro de compilacao. Estes testes seguram as rotas
 * relativas.
 */
public class PrefixoDaUrlBaseTest {

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /** Monta a base como o app faz: a URL do build passa pelo ClienteIa.normalizar. */
    private <T> T api(String prefixo, Class<T> tipo) {
        String base = ClienteIa.normalizar(server.url(prefixo).toString());
        return new Retrofit.Builder()
                .baseUrl(base)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(tipo);
    }

    private String caminhoDaChamada() throws InterruptedException {
        RecordedRequest chamada = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull("o servidor nao recebeu a chamada", chamada);
        return chamada.getPath();
    }

    @Test
    public void rotaDeHealthDaIa_mantemOPrefixoAi() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        api("/ai", VenusIaApi.class).acordar().execute();

        assertEquals("/ai/v1/health", caminhoDaChamada());
    }

    @Test
    public void rotaDeChatDaIa_mantemOPrefixoAi() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        ChatRequest corpo = new ChatRequest();
        corpo.mensagem = "oi";

        api("/ai", VenusIaApi.class).conversar("Bearer token", corpo).execute();

        assertEquals("/ai/v1/chat", caminhoDaChamada());
    }

    @Test
    public void rotaDaUltimaAnalise_mantemOPrefixoClassificacao() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        api("/classificacao", VenusClassificacaoApi.class).ultimaClassificacao(7L, 9L).execute();

        assertEquals("/classificacao/api/classifications/user/7/product/9", caminhoDaChamada());
    }

    @Test
    public void rotaDeCalcular_mantemOPrefixoClassificacao() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        api("/classificacao", VenusClassificacaoApi.class)
                .classificar(new ClassificationRequest(7L, 9L)).execute();

        assertEquals("/classificacao/api/classifications", caminhoDaChamada());
    }
}
