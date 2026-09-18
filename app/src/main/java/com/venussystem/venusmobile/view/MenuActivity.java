package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Usuario;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class MenuActivity extends AppCompatActivity {
    private final AutenticacaoRepository autenticacao = new AutenticacaoRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_menu);

        mostrarUsuario();

        findViewById(R.id.btnSair).setOnClickListener(v -> sair());

        findViewById(R.id.btnRefazerQuestionario).setOnClickListener(v -> reiniciarTeste());
    }

    private void reiniciarTeste() {
        new PerfilRepository(this).limpar();
        autenticacao.sair();
        Toast.makeText(this, R.string.questionario_reiniciado, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void mostrarUsuario() {
        Usuario usuario = autenticacao.usuarioLogado();
        TextView texto = findViewById(R.id.textUsuario);
        if (usuario == null) {
            texto.setText(R.string.menu_sem_usuario);
            return;
        }
        String nome = usuario.getNome() == null ? "" : usuario.getNome();
        texto.setText(getString(R.string.menu_logado_como, nome, usuario.getEmail()));
    }

    private void sair() {
        autenticacao.sair();

        Intent intent = new Intent(this, BemVindoActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
