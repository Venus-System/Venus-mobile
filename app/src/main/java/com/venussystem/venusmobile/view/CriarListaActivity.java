package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.venussystem.venusmobile.R;

public class CriarListaActivity extends AppCompatActivity {

    private EditText editNome;
    private EditText editDescricao;
    private View btnPublica;
    private View btnPrivada;

    private boolean publica = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_criar_lista);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editNome = findViewById(R.id.editNomeLista);
        editDescricao = findViewById(R.id.editDescricaoLista);
        btnPublica = findViewById(R.id.btnPublica);
        btnPrivada = findViewById(R.id.btnPrivada);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarLista).setOnClickListener(v -> finish());

        findViewById(R.id.btnEscolherCapa).setOnClickListener(v ->
                Toast.makeText(this, R.string.em_breve, Toast.LENGTH_SHORT).show());

        btnPublica.setOnClickListener(v -> definirPrivacidade(true));
        btnPrivada.setOnClickListener(v -> definirPrivacidade(false));
        definirPrivacidade(true);

        findViewById(R.id.btnSalvarLista).setOnClickListener(v -> salvar());
    }

    private void definirPrivacidade(boolean ehPublica) {
        publica = ehPublica;
        btnPublica.setSelected(ehPublica);
        btnPrivada.setSelected(!ehPublica);
    }

    private void salvar() {
        String nome = editNome.getText().toString().trim();
        if (nome.isEmpty()) {
            editNome.setError(getString(R.string.lista_erro_nome));
            return;
        }

        Intent intent = new Intent(this, DetalheListaActivity.class);
        intent.putExtra(DetalheListaActivity.EXTRA_NOME, nome);
        intent.putExtra(DetalheListaActivity.EXTRA_DESCRICAO,
                editDescricao.getText().toString().trim());
        intent.putExtra(DetalheListaActivity.EXTRA_PUBLICA, publica);
        startActivity(intent);

        // Sai da tela de criacao: voltar do detalhe tem que cair nas listas,
        // nao no formulario que o usuario acabou de preencher.
        finish();
    }
}
