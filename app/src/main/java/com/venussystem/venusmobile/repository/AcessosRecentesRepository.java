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

public class AcessosRecentesRepository {

    static final String ARQUIVO = "venus_acessos";
    private static final String CHAVE = "acessos";

    @VisibleForTesting
    static final int LIMITE = 20;

    private static final Object TRAVA = new Object();

    public static final class Acesso {
        public final long produtoId;
        public final long momento;

        Acesso(long produtoId, long momento) {
            this.produtoId = produtoId;
            this.momento = momento;
        }
    }

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

    @NonNull
    public List<Acesso> listar() {
        List<Acesso> acessos = new ArrayList<>();
        for (Gravado gravado : ler()) {
            acessos.add(new Acesso(gravado.produtoId, gravado.momento));
        }
        return Collections.unmodifiableList(acessos);
    }

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
            gravados.clear();
        }
        return gravados;
    }
}
