package com.venussystem.venusmobile.repository;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.ProductFullResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import retrofit2.Response;

final class ProdutoCompletoCache {

    private static final long VALIDADE_MS = 120_000L;

    private static final Map<Long, Entrada> ENTRADAS = new HashMap<>();
    private static final Map<Long, Object> TRANCAS = new ConcurrentHashMap<>();

    private ProdutoCompletoCache() {
    }

    @Nullable
    static ProductFullResponse obter(VenusApi api, long produtoId) throws IOException {
        Object tranca = TRANCAS.computeIfAbsent(produtoId, id -> new Object());
        synchronized (tranca) {
            ProductFullResponse guardado = guardado(api, produtoId);
            if (guardado != null) {
                return guardado;
            }

            Response<ProductFullResponse> resposta = api.buscarProdutoCompleto(produtoId).execute();
            if (!resposta.isSuccessful() || resposta.body() == null) {
                return null;
            }

            ProductFullResponse corpo = resposta.body();
            synchronized (ENTRADAS) {
                ENTRADAS.put(produtoId, new Entrada(api, corpo, System.currentTimeMillis()));
            }
            return corpo;
        }
    }

    @Nullable
    private static ProductFullResponse guardado(VenusApi api, long produtoId) {
        synchronized (ENTRADAS) {
            Entrada entrada = ENTRADAS.get(produtoId);
            if (entrada == null
                    || entrada.api != api
                    || System.currentTimeMillis() - entrada.em > VALIDADE_MS) {
                return null;
            }
            return entrada.produto;
        }
    }

    private static final class Entrada {
        final VenusApi api;
        final ProductFullResponse produto;
        final long em;

        Entrada(VenusApi api, ProductFullResponse produto, long em) {
            this.api = api;
            this.produto = produto;
            this.em = em;
        }
    }
}
