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

final class PedidoDeAvisos {

    private static final String ARQUIVO = "venus_avisos";
    private static final String CHAVE_PEDIU = "pediu_permissao";
    private static final String CHAVE_PERMISSAO = "venus_permissao_avisos";

    private PedidoDeAvisos() {
    }

    static boolean deveOferecer(Activity origem) {
        return origem instanceof ComponentActivity
                && !Notificador.temPermissao(origem)
                && !jaPerguntou(origem);
    }

    static boolean jaPerguntou(Context context) {
        return DadosDaConta.prefs(context, ARQUIVO).getBoolean(CHAVE_PEDIU, false);
    }

    static void marcarPerguntou(Context context) {
        DadosDaConta.prefs(context, ARQUIVO).edit().putBoolean(CHAVE_PEDIU, true).apply();
    }

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
