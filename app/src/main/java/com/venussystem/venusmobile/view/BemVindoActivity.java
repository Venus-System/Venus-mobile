package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;

public class BemVindoActivity extends AppCompatActivity {
    private final AutenticacaoRepository repository = new AutenticacaoRepository();

    Button btnLogin;
    Button btnCadastrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnLogin = findViewById(R.id.ButtonEntrar);
        btnCadastrar = findViewById(R.id.buttonCriarConta);

        btnLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        btnCadastrar.setOnClickListener(v ->
                startActivity(new Intent(this, CadastrarActivity.class)));

        // A API dorme no plano gratuito do Render e a primeira chamada acorda o
        // servidor. Comecar aqui faz essa espera acontecer enquanto a pessoa
        // ainda esta logando, e nao depois, olhando a tela de busca vazia.
        new ProdutoRepository().carregar(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (repository.temSessaoAtiva()) {
            NavegacaoPosLogin.seguir(this);
        }
    }
}
