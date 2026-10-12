package com.venussystem.venusmobile.view;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.LocalizacaoRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.concurrent.atomic.AtomicReference;

public class NavegacaoPosLogin {
    private static final String CHAVE_PERMISSAO_LOCALIZACAO = "venus_permissao_localizacao";

    private NavegacaoPosLogin() {
    }

    /**
     * Ponto unico de "a pessoa entrou": antes de seguir, pede a permissao de
     * localizacao (uma vez por conta) e manda a localizacao para a API. Negar
     * ou falhar nunca impede de entrar.
     */
    public static void seguir(Activity origem) {
        LocalizacaoRepository localizacao = new LocalizacaoRepository(origem);

        if (localizacao.temPermissao()) {
            localizacao.enviarEmSegundoPlano();
        } else if (!localizacao.jaPediuPermissao() && origem instanceof ComponentActivity) {
            pedirPermissao((ComponentActivity) origem, localizacao);
            return;
        }

        continuar(origem);
    }

    private static void pedirPermissao(ComponentActivity origem, LocalizacaoRepository localizacao) {
        // Marca antes de mostrar: se o app for fechado com o dialogo aberto, nao
        // volta a perguntar toda hora.
        localizacao.marcarPediuPermissao();

        // registry.register sem LifecycleOwner pode ser chamado a qualquer
        // momento (o seguir roda depois do onCreate, de um observer ou do onStart).
        AtomicReference<ActivityResultLauncher<String>> launcher = new AtomicReference<>();
        launcher.set(origem.getActivityResultRegistry().register(
                CHAVE_PERMISSAO_LOCALIZACAO,
                new ActivityResultContracts.RequestPermission(),
                concedida -> {
                    launcher.get().unregister();
                    if (concedida) {
                        localizacao.enviarEmSegundoPlano();
                    }
                    if (!origem.isFinishing() && !origem.isDestroyed()) {
                        continuar(origem);
                    }
                }));

        new AlertDialog.Builder(origem)
                .setTitle(R.string.localizacao_titulo)
                .setMessage(R.string.localizacao_mensagem)
                .setCancelable(false)
                .setPositiveButton(R.string.localizacao_permitir, (d, w) ->
                        launcher.get().launch(Manifest.permission.ACCESS_COARSE_LOCATION))
                .setNegativeButton(R.string.localizacao_agora_nao, (d, w) -> {
                    launcher.get().unregister();
                    continuar(origem);
                })
                .show();
    }

    private static void continuar(Activity origem) {
        PerfilRepository perfil = new PerfilRepository(origem);

        if (perfil.jaRespondeuQuestionario()) {
            irParaMenu(origem);
            return;
        }

        origem.startActivity(new Intent(origem, PesquisaInicialActivity.class));
        origem.finish();
    }

    public static void irParaMenu(Activity origem) {
        Intent intent = new Intent(origem, PrincipalActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        origem.startActivity(intent);
    }
}
