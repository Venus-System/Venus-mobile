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

/**
 * Separa, dentro do aparelho, os dados de cada conta.
 *
 * Um celular pode ter mais de uma conta: a pessoa sai e entra com outro
 * e-mail. Antes, listas, itens, buscas e questionario ficavam num arquivo so
 * por aparelho, e quem entrava depois via o que a outra conta tinha criado.
 * Agora cada arquivo leva o UID do Firebase no nome.
 */
public final class DadosDaConta {

    private static final String TAG = "DadosDaConta";

    // So para ter um nome quando ninguem esta logado; nenhuma tela que grava
    // dados abre sem conta.
    private static final String SEM_CONTA = "sem_conta";

    private DadosDaConta() {
    }

    /** O arquivo de preferencias da conta logada agora. */
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
        // Sem o Firebase inicializado (testes do Robolectric) nao existe conta.
        if (FirebaseApp.getApps(context).isEmpty()) {
            return null;
        }
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return usuario == null ? null : usuario.getUid();
    }

    /**
     * Da destino aos arquivos de antes da separacao por conta. Roda a cada
     * abertura do app, mas so encontra algo na primeira depois da atualizacao.
     *
     * Nunca derruba o app: e limpeza de dado antigo, e falhar aqui impediria a
     * pessoa de abrir o app por causa de um arquivo que ela nem ve.
     */
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

        // Questionario: o unico jeito de trocar de conta ("reiniciar teste")
        // ja apagava as respostas, entao o que sobrou e de quem esta logado.
        // Sem ninguem logado nao da para saber de quem e, e vai fora.
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

        // Listas, itens e buscas nunca eram apagados ao sair, entao nao da
        // para saber de qual conta sao. Decisao do time: apagar (o app ainda
        // nao foi publicado, sao dados de teste).
        ColecaoRepository.descartarListasSemDono(app);
        app.deleteSharedPreferences(ListaItemRepository.ARQUIVO);
        app.deleteSharedPreferences(BuscaRecenteRepository.ARQUIVO);
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
        // commit, e nao apply: logo depois o arquivo antigo e apagado, e a
        // copia precisa estar gravada antes disso.
        editor.commit();
    }
}
