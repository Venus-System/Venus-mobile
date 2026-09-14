package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.venussystem.venusmobile.R;

public class PesquisaInicialActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pesquisa_inicial);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        findViewById(R.id.btnAvancar).setOnClickListener(v ->
                startActivity(new Intent(this, PerguntaCabeloActivity.class)));

        findViewById(R.id.textResponderDepois).setOnClickListener(v -> irParaMenu());
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
