package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListaItemRepository {

    static final String ARQUIVO = "venus_lista_itens";

    private static final String PREFIXO_VERSAO = "versao_";
    private static final String PREFIXO_ENVIADA = "enviada_";

    private static final Object TRAVA = new Object();

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ListaItemRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public ListaItemRepository(Context context, @Nullable String uid) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
    }

    public List<Long> getProdutoIds(long listaId) {
        String salvo = prefs.getString(String.valueOf(listaId), null);
        if (salvo == null) {
            return new ArrayList<>();
        }
        Long[] ids = gson.fromJson(salvo, Long[].class);
        return ids == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(ids));
    }

    public boolean contem(long listaId, long produtoId) {
        return getProdutoIds(listaId).contains(produtoId);
    }

    public void adicionar(long listaId, long produtoId) {
        synchronized (TRAVA) {
            List<Long> atuais = getProdutoIds(listaId);
            if (atuais.contains(produtoId)) {
                return;
            }
            atuais.add(0, produtoId);
            salvar(listaId, atuais);
        }
    }

    public void remover(long listaId, long produtoId) {
        synchronized (TRAVA) {
            List<Long> atuais = getProdutoIds(listaId);
            if (atuais.remove(Long.valueOf(produtoId))) {
                salvar(listaId, atuais);
            }
        }
    }

    public void excluirTodos(long listaId) {
        synchronized (TRAVA) {
            prefs.edit()
                    .remove(String.valueOf(listaId))
                    .remove(PREFIXO_VERSAO + listaId)
                    .remove(PREFIXO_ENVIADA + listaId)
                    .apply();
        }
    }

    long versao(long listaId) {
        return prefs.getLong(PREFIXO_VERSAO + listaId, 0L);
    }

    boolean temItensParaEnviar(long listaId) {
        return versao(listaId) != prefs.getLong(PREFIXO_ENVIADA + listaId, 0L);
    }

    void marcarItensEnviados(long listaId, long versao) {
        synchronized (TRAVA) {
            prefs.edit().putLong(PREFIXO_ENVIADA + listaId, versao).apply();
        }
    }

    boolean trocarPelosDaApi(long listaId, long versaoLida, List<Long> daApi) {
        synchronized (TRAVA) {
            if (versao(listaId) != versaoLida || temItensParaEnviar(listaId)
                    || getProdutoIds(listaId).equals(daApi)) {
                return false;
            }
            long nova = versaoLida + 1;
            prefs.edit()
                    .putString(String.valueOf(listaId), gson.toJson(daApi))
                    .putLong(PREFIXO_VERSAO + listaId, nova)
                    .putLong(PREFIXO_ENVIADA + listaId, nova)
                    .apply();
            return true;
        }
    }

    boolean juntarComOsDaApi(long listaId, List<Long> daApi) {
        synchronized (TRAVA) {
            List<Long> atuais = getProdutoIds(listaId);
            boolean mudou = false;
            for (Long produtoId : daApi) {
                if (!atuais.contains(produtoId)) {
                    atuais.add(produtoId);
                    mudou = true;
                }
            }
            if (mudou) {
                salvar(listaId, atuais);
            }
            return mudou;
        }
    }

    private void salvar(long listaId, List<Long> ids) {
        prefs.edit()
                .putString(String.valueOf(listaId), gson.toJson(ids))
                .putLong(PREFIXO_VERSAO + listaId, versao(listaId) + 1)
                .apply();
    }
}
