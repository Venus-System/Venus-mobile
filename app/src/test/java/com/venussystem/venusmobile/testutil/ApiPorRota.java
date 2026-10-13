package com.venussystem.venusmobile.testutil;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Venus-CRUD de mentira para o MockWebServer: responde por "METODO caminho"
 * (com a query) e guarda cada pedido com o corpo, para o teste conferir o que
 * foi enviado e em que ordem. Rota sem resposta configurada devolve 404.
 */
public final class ApiPorRota extends Dispatcher {

    private final Map<String, MockResponse> respostas = new ConcurrentHashMap<>();
    private final Map<String, Runnable> acoes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> corpos = new ConcurrentHashMap<>();

    /** Cada pedido recebido, como "METODO caminho", na ordem. */
    public final List<String> pedidos = new CopyOnWriteArrayList<>();

    public ApiPorRota em(String rota, int codigo, String corpo) {
        respostas.put(rota, new MockResponse()
                .setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo));
        return this;
    }

    /** Roda a acao quando o pedido chega, antes de responder - para simular o usuario mexendo no meio do envio. */
    public void aoReceber(String rota, Runnable acao) {
        acoes.put(rota, acao);
    }

    /** O ultimo corpo mandado nessa rota. */
    public JsonObject corpo(String rota) {
        List<String> daRota = corposEm(rota);
        return JsonParser.parseString(daRota.get(daRota.size() - 1)).getAsJsonObject();
    }

    public List<String> corposEm(String rota) {
        List<String> daRota = corpos.get(rota);
        return daRota == null ? Collections.emptyList() : daRota;
    }

    /** So os pedidos para essas rotas, na ordem em que chegaram. */
    public List<String> pedidosEm(String... rotas) {
        List<String> procuradas = Arrays.asList(rotas);
        List<String> filtrados = new ArrayList<>();
        for (String pedido : pedidos) {
            if (procuradas.contains(pedido)) {
                filtrados.add(pedido);
            }
        }
        return filtrados;
    }

    @NonNull
    @Override
    public MockResponse dispatch(@NonNull RecordedRequest request) {
        String rota = request.getMethod() + " " + request.getPath();
        pedidos.add(rota);
        corpos.computeIfAbsent(rota, r -> new CopyOnWriteArrayList<>())
                .add(request.getBody().readUtf8());

        Runnable acao = acoes.get(rota);
        if (acao != null) {
            acao.run();
        }
        MockResponse resposta = respostas.get(rota);
        return resposta == null ? new MockResponse().setResponseCode(404) : resposta;
    }
}
