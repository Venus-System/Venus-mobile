package com.venussystem.venusmobile.view;

import android.Manifest;
import android.app.Activity;
import android.content.Context;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.venussystem.venusmobile.repository.DadosDaConta;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A permissao de notificacao (POST_NOTIFICATIONS). Duas situacoes:
 *
 * - O app oferece por conta propria (depois de enviar um produto, ou ao sair de um
 *   envio que nao terminou): so uma vez por conta, para nao encher a pessoa de
 *   perguntas. {@link #deveOferecer}.
 * - "Responder depois" no questionario: pede na hora, so com o dialogo do sistema,
 *   e o lembrete so liga se a pessoa permitir.
 *
 * Negar nao quebra nada: as decisoes continuam na tela Produtos em analise.
 */
final class PedidoDeAvisos {

    private static final String ARQUIVO = "venus_avisos";
    private static final String CHAVE_PEDIU = "pediu_permissao";
    private static final String CHAVE_PERMISSAO = "venus_permissao_avisos";

    private PedidoDeAvisos() {
    }

    /** Ainda nao tem a permissao e esta conta ainda nao foi perguntada. */
    static boolean deveOferecer(Activity origem) {
        return origem instanceof ComponentActivity
                && !Notificador.temPermissao(origem)
                && !jaPerguntou(origem);
    }

    static boolean jaPerguntou(Context context) {
        return DadosDaConta.prefs(context, ARQUIVO).getBoolean(CHAVE_PEDIU, false);
    }

    /**
     * Marca antes de mostrar a pergunta: se o app for fechado com ela aberta, nao
     * volta a perguntar toda hora.
     */
    static void marcarPerguntou(Context context) {
        DadosDaConta.prefs(context, ARQUIVO).edit().putBoolean(CHAVE_PEDIU, true).apply();
    }

    /** Abre o dialogo do sistema; {@code depois} recebe se a permissao foi concedida. */
    static void pedir(Activity origem, Consumer<Boolean> depois) {
        if (Notificador.temPermissao(origem)) {
            depois.accept(true);
            return;
        }
        if (!(origem instanceof ComponentActivity)) {
            depois.accept(false);
            return;
        }
        ComponentActivity activity = (ComponentActivity) origem;
        // registry.register sem LifecycleOwner pode ser chamado a qualquer momento
        // (aqui e depois do onCreate, de dentro do clique de um dialogo).
        AtomicReference<ActivityResultLauncher<String>> launcher = new AtomicReference<>();
        launcher.set(activity.getActivityResultRegistry().register(
                CHAVE_PERMISSAO,
                new ActivityResultContracts.RequestPermission(),
                concedida -> {
                    launcher.get().unregister();
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        depois.accept(concedida);
                    }
                }));
        launcher.get().launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
