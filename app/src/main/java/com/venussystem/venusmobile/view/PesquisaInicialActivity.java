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

    private void responderDepois() {
        PedidoDeAvisos.pedir(this, concedida -> {
            if (concedida) {
                Lembretes.ativarQuestionario(this);
            }
            irParaMenu();
        });
    }

    private void irParaMenu() {
        NavegacaoPosLogin.irParaMenu(this);
    }
}
