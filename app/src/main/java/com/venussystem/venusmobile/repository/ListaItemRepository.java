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
 * separado por conta, como o ColecaoRepository. A copia em
 * /api/user-list-items e mantida pela SincronizacaoListas.
 *
 * A ordem guardada e a da tela: o mais recente primeiro.
 */
public class ListaItemRepository {

    static final String ARQUIVO = "venus_lista_itens";

    // Um contador por lista, como a versao do PerfilRepository: se a pessoa
    // mexer na lista enquanto um envio esta no meio do caminho, a versao sobe
    // e o envio antigo nao consegue marcar como enviado o que nem leu.
    private static final String PREFIXO_VERSAO = "versao_";
    private static final String PREFIXO_ENVIADA = "enviada_";

    // A tela grava e a sincronizacao marca o envio, cada uma na sua thread.
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

    /**
     * Chamado quando a propria lista e excluida - nao faz sentido guardar os
     * itens dela. Na API, apagar a lista ja apaga os itens junto.
     */
    public void excluirTodos(long listaId) {
        synchronized (TRAVA) {
            prefs.edit()
                    .remove(String.valueOf(listaId))
                    .remove(PREFIXO_VERSAO + listaId)
                    .remove(PREFIXO_ENVIADA + listaId)
                    .apply();
        }
    }

    // ---- O que a SincronizacaoListas usa ----

    long versao(long listaId) {
        return prefs.getLong(PREFIXO_VERSAO + listaId, 0L);
    }

    boolean temItensParaEnviar(long listaId) {
        return versao(listaId) != prefs.getLong(PREFIXO_ENVIADA + listaId, 0L);
    }

    /**
     * Registra que os produtos ate essa versao ja estao na API. Quem chama
     * passa a versao lida ANTES de montar o envio, nunca a do momento.
     */
    void marcarItensEnviados(long listaId, long versao) {
        synchronized (TRAVA) {
            prefs.edit().putLong(PREFIXO_ENVIADA + listaId, versao).apply();
        }
    }

    /**
     * Troca os produtos pelos que estao na API, que ja ficam como enviados.
     * Nao troca se a lista mudou aqui desde a versao lida antes de perguntar
     * a API, nem se tem produto esperando envio: o proximo envio manda.
     *
     * @param daApi na ordem da tela, o mais recente primeiro.
     * @return true se os produtos mudaram.
     */
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

    /**
     * Junta os produtos da API aos daqui, que ainda nao tinham subido: os da
     * API entram depois, como os mais antigos, e os daqui sobem no proximo envio.
     *
     * @return true se os produtos mudaram.
     */
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

    // Chamado sempre de dentro da TRAVA.
    private void salvar(long listaId, List<Long> ids) {
        prefs.edit()
                .putString(String.valueOf(listaId), gson.toJson(ids))
                .putLong(PREFIXO_VERSAO + listaId, versao(listaId) + 1)
                .apply();
    }
}
