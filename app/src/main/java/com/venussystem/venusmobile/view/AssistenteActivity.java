package com.venussystem.venusmobile.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.view.adapter.ConversaAdapter;
import com.venussystem.venusmobile.viewmodel.AssistenteViewModel;

/**
 * Conversa com a assistente, que responde pela Venus-AI-api.
 *
 * A tela so desenha: a conversa e o envio moram no AssistenteViewModel. Sem a
 * URL da API configurada no build, a Venus responde que ainda nao esta
 * conectada, em vez de inventar uma resposta que daria a impressao de que ja
 * funciona.
 */
public class AssistenteActivity extends AppCompatActivity {

    // Enquanto a Venus responde, o botao e as sugestoes ficam apagados assim.
    private static final float ALFA_DESABILITADO = 0.4f;

    private AssistenteViewModel viewModel;
    private ConversaAdapter adapter;
    private RecyclerView lista;
    private EditText campo;
    private View botaoEnviar;
    private LinearLayout linhaSugestoes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_assistente);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());

            // O teclado cobre o campo de digitar: quando ele esta aberto, a
            // margem de baixo passa a ser a altura dele, e nao a da barra.
            v.setPadding(barras.left, barras.top, barras.right,
                    Math.max(barras.bottom, teclado.bottom));
            return insets;
        });

        campo = findViewById(R.id.campoMensagem);
        lista = findViewById(R.id.listaMensagens);
        botaoEnviar = findViewById(R.id.btnEnviar);
        linhaSugestoes = findViewById(R.id.linhaSugestoes);

        adapter = new ConversaAdapter();
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        botaoEnviar.setOnClickListener(v -> enviar());

        campo.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEND) {
                enviar();
                return true;
            }
            return false;
        });

        montarSugestoes();

        viewModel = new ViewModelProvider(this).get(AssistenteViewModel.class);
        viewModel.getMensagens().observe(this, mensagens -> {
            adapter.definir(mensagens);
            if (!mensagens.isEmpty()) {
                lista.smoothScrollToPosition(mensagens.size() - 1);
            }
        });
        viewModel.getAguardando().observe(this, this::mostrarAguardando);
    }

    private void montarSugestoes() {
        int[] sugestoes = {
                R.string.assistente_sugestao_um,
                R.string.assistente_sugestao_dois,
                R.string.assistente_sugestao_tres,
        };

        for (int sugestao : sugestoes) {
            TextView chip = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_chip_sugestao, linhaSugestoes, false);
            chip.setText(sugestao);
            chip.setOnClickListener(v -> viewModel.perguntar(chip.getText().toString()));
            linhaSugestoes.addView(chip);
        }
    }

    private void enviar() {
        // So limpa o campo se a pergunta foi aceita: enquanto a Venus responde a
        // anterior, o texto digitado fica esperando ali.
        if (viewModel.perguntar(campo.getText().toString())) {
            campo.setText("");
        }
    }

    private void mostrarAguardando(boolean aguardando) {
        float alfa = aguardando ? ALFA_DESABILITADO : 1f;

        botaoEnviar.setEnabled(!aguardando);
        botaoEnviar.setAlpha(alfa);
        for (int i = 0; i < linhaSugestoes.getChildCount(); i++) {
            View chip = linhaSugestoes.getChildAt(i);
            chip.setEnabled(!aguardando);
            chip.setAlpha(alfa);
        }
    }
}
