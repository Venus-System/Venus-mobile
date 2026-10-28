package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Map;
import java.util.Set;

public final class DadosDaConta {

    private static final String TAG = "DadosDaConta";

    private static final String ARQUIVO_BUSCAS_ANTIGAS = "venus_busca";

    private static final String SEM_CONTA = "sem_conta";

    private DadosDaConta() {
    }

    public static SharedPreferences prefs(Context context, String arquivo) {
        return prefs(context, arquivo, uidAtual(context));
    }

    public static SharedPreferences prefs(Context context, String arquivo, @Nullable String uid) {
        return context.getApplicationContext()
                .getSharedPreferences(nomeDoArquivo(arquivo, uid), Context.MODE_PRIVATE);
    }

    @VisibleForTesting
    static String nomeDoArquivo(String arquivo, @Nullable String uid) {
        return arquivo + "_" + (uid == null ? SEM_CONTA : uid);
    }

    @Nullable
    public static String uidAtual(Context context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return null;
        }
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return usuario == null ? null : usuario.getUid();
    }

    public static void migrarDadosAntigos(Context context) {
        try {
            migrarDadosAntigos(context, uidAtual(context));
        } catch (RuntimeException e) {
            Log.w(TAG, "Nao foi possivel migrar os dados antigos do aparelho.", e);
        }
    }

    @VisibleForTesting
    static void migrarDadosAntigos(Context context, @Nullable String uid) {
        Context app = context.getApplicationContext();

        SharedPreferences perfilAntigo =
                app.getSharedPreferences(PerfilRepository.ARQUIVO, Context.MODE_PRIVATE);
        if (!perfilAntigo.getAll().isEmpty()) {
            if (uid != null) {
                SharedPreferences daConta = prefs(app, PerfilRepository.ARQUIVO, uid);
                if (daConta.getAll().isEmpty()) {
                    copiar(perfilAntigo, daConta);
                }
            }
            app.deleteSharedPreferences(PerfilRepository.ARQUIVO);
        }

        ColecaoRepository.descartarListasSemDono(app);
        app.deleteSharedPreferences(ListaItemRepository.ARQUIVO);
        app.deleteSharedPreferences(ARQUIVO_BUSCAS_ANTIGAS);
    }

    private static void copiar(SharedPreferences origem, SharedPreferences destino) {
        SharedPreferences.Editor editor = destino.edit();
        for (Map.Entry<String, ?> entrada : origem.getAll().entrySet()) {
            String chave = entrada.getKey();
            Object valor = entrada.getValue();
            if (valor instanceof String) {
                editor.putString(chave, (String) valor);
            } else if (valor instanceof Long) {
                editor.putLong(chave, (Long) valor);
            } else if (valor instanceof Boolean) {
                editor.putBoolean(chave, (Boolean) valor);
            } else if (valor instanceof Integer) {
                editor.putInt(chave, (Integer) valor);
            } else if (valor instanceof Float) {
                editor.putFloat(chave, (Float) valor);
            } else if (valor instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> conjunto = (Set<String>) valor;
                editor.putStringSet(chave, conjunto);
            }
        }
        editor.commit();
    }
}
