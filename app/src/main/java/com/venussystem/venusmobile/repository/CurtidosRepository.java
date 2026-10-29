package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CurtidosRepository {

    static final String ARQUIVO = "venus_curtidos";

    private static final String CHAVE_PRODUTOS = "produtos";
    private static final String CHAVE_PARA_CURTIR = "para_curtir";
    private static final String CHAVE_PARA_DESCURTIR = "para_descurtir";

    private static final Object TRAVA = new Object();

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public CurtidosRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public CurtidosRepository(Context context, @Nullable String uid) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
    }

    public List<Long> getProdutoIds() {
        return ler(CHAVE_PRODUTOS);
    }

    public boolean contem(long produtoId) {
        return getProdutoIds().contains(produtoId);
    }

    public void curtir(long produtoId) {
        synchronized (TRAVA) {
            List<Long> produtos = ler(CHAVE_PRODUTOS);
            if (!produtos.contains(produtoId)) {
                produtos.add(0, produtoId);
            }
            List<Long> paraCurtir = ler(CHAVE_PARA_CURTIR);
            if (!paraCurtir.contains(produtoId)) {
                paraCurtir.add(produtoId);
            }
            List<Long> paraDescurtir = ler(CHAVE_PARA_DESCURTIR);
            paraDescurtir.remove(Long.valueOf(produtoId));
            gravar(produtos, paraCurtir, paraDescurtir);
        }
    }

    public void descurtir(long produtoId) {
        synchronized (TRAVA) {
            List<Long> produtos = ler(CHAVE_PRODUTOS);
            produtos.remove(Long.valueOf(produtoId));
            List<Long> paraCurtir = ler(CHAVE_PARA_CURTIR);
            paraCurtir.remove(Long.valueOf(produtoId));
            List<Long> paraDescurtir = ler(CHAVE_PARA_DESCURTIR);
            if (!paraDescurtir.contains(produtoId)) {
                paraDescurtir.add(produtoId);
            }
            gravar(produtos, paraCurtir, paraDescurtir);
        }
    }

    List<Long> paraCurtirNaApi() {
        return ler(CHAVE_PARA_CURTIR);
    }

    List<Long> paraDescurtirNaApi() {
        return ler(CHAVE_PARA_DESCURTIR);
    }

    boolean temAlgoParaEnviar() {
        return !paraCurtirNaApi().isEmpty() || !paraDescurtirNaApi().isEmpty();
    }

    void marcarCurtidaEnviada(long produtoId) {
        tirarDaFila(CHAVE_PARA_CURTIR, produtoId);
    }

    void marcarDescurtidaEnviada(long produtoId) {
        tirarDaFila(CHAVE_PARA_DESCURTIR, produtoId);
    }

    boolean trocarPelosDaApi(List<Long> daApi) {
        synchronized (TRAVA) {
            List<Long> atuais = ler(CHAVE_PRODUTOS);
            Set<Long> paraCurtir = new LinkedHashSet<>(ler(CHAVE_PARA_CURTIR));
            Set<Long> paraDescurtir = new LinkedHashSet<>(ler(CHAVE_PARA_DESCURTIR));

            Set<Long> novos = new LinkedHashSet<>();
            for (Long produtoId : atuais) {
                if (paraCurtir.contains(produtoId)) {
                    novos.add(produtoId);
                }
            }
            for (Long produtoId : daApi) {
                if (!paraDescurtir.contains(produtoId)) {
                    novos.add(produtoId);
                }
            }

            List<Long> resultado = new ArrayList<>(novos);
            if (resultado.equals(atuais)) {
                return false;
            }
            prefs.edit().putString(CHAVE_PRODUTOS, gson.toJson(resultado)).apply();
            return true;
        }
    }

    private void tirarDaFila(String fila, long produtoId) {
        synchronized (TRAVA) {
            List<Long> ids = ler(fila);
            if (ids.remove(Long.valueOf(produtoId))) {
                prefs.edit().putString(fila, gson.toJson(ids)).apply();
            }
        }
    }

    private List<Long> ler(String chave) {
        String salvo = prefs.getString(chave, null);
        if (salvo == null) {
            return new ArrayList<>();
        }
        Long[] ids = gson.fromJson(salvo, Long[].class);
        return ids == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(ids));
    }

    private void gravar(List<Long> produtos, List<Long> paraCurtir, List<Long> paraDescurtir) {
        prefs.edit()
                .putString(CHAVE_PRODUTOS, gson.toJson(produtos))
                .putString(CHAVE_PARA_CURTIR, gson.toJson(paraCurtir))
                .putString(CHAVE_PARA_DESCURTIR, gson.toJson(paraDescurtir))
                .apply();
    }
}
