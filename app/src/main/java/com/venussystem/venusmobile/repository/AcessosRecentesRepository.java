package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Os produtos que a pessoa abriu no detalhe, guardados no aparelho e separados
 * por conta (ver DadosDaConta). Alimentam o historico da tela de busca, junto
 * com os scans.
 *
 * Guarda so o id do produto e a hora da abertura: nome, marca e nota vem do
 * catalogo em memoria na hora de mostrar, entao nunca ficam velhos aqui.
 *
 * Reabrir um produto nao cria outra linha, so move a que ja existe para o
 * topo. A lista e curta de proposito (LIMITE), como o historico da tela.
 */
public class AcessosRecentesRepository {

    static final String ARQUIVO = "venus_acessos";
    private static final String CHAVE = "acessos";

    // O historico da tela mostra 20 itens, entao guardar mais que isso so
    // ocuparia espaco.
    @VisibleForTesting
    static final int LIMITE = 20;

    // A tela grava e o historico le, cada um na sua thread.
    private static final Object TRAVA = new Object();

    /** Um produto aberto: o id e quando foi a ultima vez, em milissegundos. */
    public static final class Acesso {
        public final long produtoId;
        public final long momento;

        Acesso(long produtoId, long momento) {
            this.produtoId = produtoId;
            this.momento = momento;
        }
    }

    // Formato gravado. Separado do Acesso para mudar o modelo da tela sem
    // perder o que ja esta salvo nos aparelhos.
    private static final class Gravado {
        long produtoId;
        long momento;
    }

    private final SharedPreferences prefs;
    private final LongSupplier relogio;
    private final Gson gson = new Gson();

    public AcessosRecentesRepository(Context context) {
        this(context, DadosDaConta.uidAtual(context));
    }

    @VisibleForTesting
    public AcessosRecentesRepository(Context context, @Nullable String uid) {
        this(context, uid, System::currentTimeMillis);
    }

    @VisibleForTesting
    AcessosRecentesRepository(Context context, @Nullable String uid, LongSupplier relogio) {
        this.prefs = DadosDaConta.prefs(context, ARQUIVO, uid);
        this.relogio = relogio;
    }

    /** Os acessos da conta, do mais recente para o mais antigo. */
    @NonNull
    public List<Acesso> listar() {
        List<Acesso> acessos = new ArrayList<>();
        for (Gravado gravado : ler()) {
            acessos.add(new Acesso(gravado.produtoId, gravado.momento));
        }
        return Collections.unmodifiableList(acessos);
    }

    /** Anota que o produto foi aberto agora; se ja estava na lista, sobe para o topo. */
    public void registrar(long produtoId) {
        if (produtoId <= 0) {
            return;
        }
        synchronized (TRAVA) {
            List<Gravado> gravados = ler();
            for (int i = gravados.size() - 1; i >= 0; i--) {
                if (gravados.get(i).produtoId == produtoId) {
                    gravados.remove(i);
                }
            }

            Gravado novo = new Gravado();
            novo.produtoId = produtoId;
            novo.momento = relogio.getAsLong();
            gravados.add(0, novo);

            while (gravados.size() > LIMITE) {
                gravados.remove(gravados.size() - 1);
            }
            prefs.edit().putString(CHAVE, gson.toJson(gravados)).apply();
        }
    }

    // Chamado sempre de dentro da TRAVA, ou so para ler.
    private List<Gravado> ler() {
        List<Gravado> gravados = new ArrayList<>();
        String json = prefs.getString(CHAVE, null);
        if (json == null) {
            return gravados;
        }
        try {
            Gravado[] lidos = gson.fromJson(json, Gravado[].class);
            if (lidos != null) {
                for (Gravado gravado : lidos) {
                    if (gravado != null && gravado.produtoId > 0) {
                        gravados.add(gravado);
                    }
                }
            }
        } catch (JsonParseException e) {
            // Arquivo estragado nao pode travar a busca: comeca do zero.
            gravados.clear();
        }
        return gravados;
    }
}
