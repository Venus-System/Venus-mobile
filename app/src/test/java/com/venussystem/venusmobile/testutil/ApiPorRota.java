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

public final class ApiPorRota extends Dispatcher {

    private final Map<String, MockResponse> respostas = new ConcurrentHashMap<>();
    private final Map<String, Runnable> acoes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> corpos = new ConcurrentHashMap<>();

    public final List<String> pedidos = new CopyOnWriteArrayList<>();

    public ApiPorRota em(String rota, int codigo, String corpo) {
        respostas.put(rota, new MockResponse()
                .setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo));
        return this;
    }

    public void aoReceber(String rota, Runnable acao) {
        acoes.put(rota, acao);
    }

    public JsonObject corpo(String rota) {
        List<String> daRota = corposEm(rota);
        return JsonParser.parseString(daRota.get(daRota.size() - 1)).getAsJsonObject();
    }

    public List<String> corposEm(String rota) {
        List<String> daRota = corpos.get(rota);
        return daRota == null ? Collections.emptyList() : daRota;
    }

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
