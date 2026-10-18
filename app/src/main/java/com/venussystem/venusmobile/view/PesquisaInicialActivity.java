package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.lembrete.Lembretes;

public class PesquisaInicialActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_pesquisa_inicial);

        findViewById(R.id.btnAvancar).setOnClickListener(v ->
                startActivity(new Intent(this, PerguntaGeneroActivity.class)));

        findViewById(R.id.textResponderDepois).setOnClickListener(v -> responderDepois());
    }

    /**
     * "Responder depois" so adia o questionario, e sem ele o Match com voce nao
     * aparece. Sem pergunta propria: a pessoa so ve o pedido de permissao de
     * notificacao do sistema. Permitindo (ou ja tendo permitido), o app lembra do
     * questionario nos proximos dias (ate 3 vezes); negando, segue sem lembrete.
     * O cartao "Seu perfil Venus" da Home continua chamando para o questionario.
     */
    private void responderDepois() {
        PedidoDeAvisos.pedir(this, concedida -> {
            if (concedida) {
                Lembretes.ativarQuestionario(this);
            }
            irParaMenu();
        });
    }

    private void irParaMenu() {
        // NavegacaoPosLogin.seguir() ja deu finish() na tela de login antes de
        // abrir esta aqui, entao um simples finish() nao volta para o app - cai
        // fora da pilha de telas. Precisa abrir a Principal de verdade, do
        // mesmo jeito que o fim do questionario faz.
        //
        // De propósito NAO chama marcarQuestionarioRespondido(): "mais tarde" e
        // so um adiamento, entao da proxima vez que a sessao for retomada o
        // BemVindoActivity vai cair aqui de novo ate o usuario responder.
        NavegacaoPosLogin.irParaMenu(this);
    }
}
