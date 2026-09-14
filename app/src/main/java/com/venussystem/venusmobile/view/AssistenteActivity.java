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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;
import com.venussystem.venusmobile.view.adapter.ConversaAdapter;

/**
 * Conversa com a assistente.
 *
 * A API de chat ainda nao existe, entao nada e enviado para lugar nenhum: a
 * mensagem do usuario entra na lista e a assistente responde dizendo que ainda
 * nao esta conectada. Preferi isso a inventar uma resposta pronta, que daria a
 * impressao de que ja funciona.
 *
 * Quando a API entrar, o unico ponto a mexer e o responder().
 */
public class AssistenteActivity extends AppCompatActivity {

    private ConversaAdapter adapter;
    private RecyclerView lista;
    private EditText campo;

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

        adapter = new ConversaAdapter();
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnEnviar).setOnClickListener(v -> enviar());

        campo.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEND) {
                enviar();
                return true;
            }
            return false;
        });

        montarSugestoes();
        adapter.adicionar(Mensagem.doAssistente(getString(R.string.assistente_saudacao)));
    }

    private void montarSugestoes() {
        LinearLayout linha = findViewById(R.id.linhaSugestoes);
        int[] sugestoes = {
                R.string.assistente_sugestao_um,
                R.string.assistente_sugestao_dois,
                R.string.assistente_sugestao_tres,
        };

        for (int sugestao : sugestoes) {
            TextView chip = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_chip_sugestao, linha, false);
            chip.setText(sugestao);
            chip.setOnClickListener(v -> perguntar(chip.getText().toString()));
            linha.addView(chip);
        }
    }

    private void enviar() {
        String texto = campo.getText().toString().trim();
        if (texto.isEmpty()) {
            return;
        }
        campo.setText("");
        perguntar(texto);
    }

    private void perguntar(String texto) {
        adapter.adicionar(Mensagem.doUsuario(texto));
        responder();
        lista.smoothScrollToPosition(adapter.getItemCount() - 1);
    }

    private void responder() {
        adapter.adicionar(Mensagem.doAssistente(getString(R.string.assistente_offline)));
    }
}
