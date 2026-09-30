package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Quais produtos do catalogo entraram em cada lista, guardado no aparelho e
 * separado por conta, como o ColecaoRepository: o /api/user-list-items ainda
 * nao foi ligado ao app.
 */
public class ListaItemRepository {

    static final String ARQUIVO = "venus_lista_itens";

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

    public void adicionar(long listaId, long produtoId) {
        List<Long> atuais = getProdutoIds(listaId);
        if (atuais.contains(produtoId)) {
            return;
        }
        atuais.add(0, produtoId);
        salvar(listaId, atuais);
    }

    public void remover(long listaId, long produtoId) {
        List<Long> atuais = getProdutoIds(listaId);
        atuais.remove(Long.valueOf(produtoId));
        salvar(listaId, atuais);
    }

    /** Chamado quando a propria lista e excluida - nao faz sentido guardar os itens dela. */
    public void excluirTodos(long listaId) {
        prefs.edit().remove(String.valueOf(listaId)).apply();
    }

    private void salvar(long listaId, List<Long> ids) {
        prefs.edit().putString(String.valueOf(listaId), gson.toJson(ids)).apply();
    }
}
