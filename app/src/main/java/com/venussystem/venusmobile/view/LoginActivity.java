package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.viewmodel.LoginViewModel;

public class LoginActivity extends AppCompatActivity {
    private LoginViewModel viewModel;
    private EditText editEmail;
    private EditText editSenha;
    private AppCompatButton btnLogar;
    private TextView txtEsqueciSenha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

            int folga = Math.max(teclado.bottom, systemBars.bottom);
            findViewById(R.id.cardLogin).setPadding(0, 0, 0, folga);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        editEmail = findViewById(R.id.editEmail);
        editSenha = findViewById(R.id.editSenha);
        btnLogar = findViewById(R.id.btnLogar);
        txtEsqueciSenha = findViewById(R.id.textEsqueciSenha);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        SenhaVisibilidade.aplicar(editSenha);

        txtEsqueciSenha.setOnClickListener(v ->
                startActivity(new Intent(this, RecuperarSenhaActivity.class)));

        btnLogar.setOnClickListener(v -> viewModel.entrar(
                editEmail.getText().toString(),
                editSenha.getText().toString()));

        LoginGoogleHelper google = new LoginGoogleHelper(this);
        findViewById(R.id.btnGoogle).setOnClickListener(v -> google.iniciar(
                new LoginGoogleHelper.Callback() {
                    @Override
                    public void aoObterToken(String idToken) {
                        viewModel.entrarComGoogle(idToken);
                    }

                    @Override
                    public void aoFalhar(String mensagem) {
                        viewModel.falhaGoogle(mensagem);
                    }

                    @Override
                    public void aoCancelar() {
                        viewModel.cancelarCarregamento();
                    }
                }));

        observarViewModel();
    }

    private void observarViewModel() {
        viewModel.getErroEmail().observe(this, erro -> editEmail.setError(erro));
        viewModel.getErroSenha().observe(this, erro -> editSenha.setError(erro));

        viewModel.getCarregando().observe(this, carregando -> {
            btnLogar.setEnabled(!carregando);
            btnLogar.setText(carregando
                    ? getString(R.string.estado_entrando)
                    : getString(R.string.acao_fazer_login));
        });

        viewModel.getResultado().observe(this, resultado -> {
            if (resultado == null) {
                return;
            }
            if (resultado.isSucesso()) {
                NavegacaoPosLogin.seguir(this);
            } else {
                Toast.makeText(this, resultado.getErro(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
