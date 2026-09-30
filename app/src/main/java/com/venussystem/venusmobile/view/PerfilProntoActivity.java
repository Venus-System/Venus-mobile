package com.venussystem.venusmobile.view;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerfilProntoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_perfil_pronto);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        findViewById(R.id.btnAvancar).setOnClickListener(v -> {
            new PerfilRepository(this).marcarQuestionarioRespondido();
            NavegacaoPosLogin.irParaMenu(this);
        });
    }
}
