package com.venussystem.venusmobile.view;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.viewmodel.CadastrarViewModel;

public class CadastrarActivity extends AppCompatActivity {
    private CadastrarViewModel viewModel;
    private EditText editNome;
    private EditText editEmail;
    private EditText editSenha;
    private AppCompatButton btnCriarConta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cadastrar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

            int folga = Math.max(teclado.bottom, systemBars.bottom);
            findViewById(R.id.cardCadastrar).setPadding(0, 0, 0, folga);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(CadastrarViewModel.class);

        editNome = findViewById(R.id.editNome);
        editEmail = findViewById(R.id.editEmail);
        editSenha = findViewById(R.id.editSenha);
        btnCriarConta = findViewById(R.id.btnCriarConta);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        SenhaVisibilidade.aplicar(editSenha);

        btnCriarConta.setOnClickListener(v -> viewModel.cadastrar(
                editNome.getText().toString(),
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
        viewModel.getErroNome().observe(this, erro -> editNome.setError(erro));
        viewModel.getErroEmail().observe(this, erro -> editEmail.setError(erro));
        viewModel.getErroSenha().observe(this, erro -> editSenha.setError(erro));

        viewModel.getCarregando().observe(this, carregando -> {
            btnCriarConta.setEnabled(!carregando);
            btnCriarConta.setText(carregando
                    ? getString(R.string.estado_criando_conta)
                    : getString(R.string.acao_criar_conta));
        });

        viewModel.getResultado().observe(this, resultado -> {
            if (resultado == null) {
                return;
            }
            if (resultado.isSucesso()) {
                Toast.makeText(this, R.string.cadastro_sucesso, Toast.LENGTH_LONG).show();

                NavegacaoPosLogin.seguir(this);
            } else {
                Toast.makeText(this, resultado.getErro(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
