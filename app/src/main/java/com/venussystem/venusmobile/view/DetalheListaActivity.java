package com.venussystem.venusmobile.view;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Usuario;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;

/**
 * Uma lista aberta. Recebe pelo Intent o que ja se sabe dela; a lista ainda nao
 * tem produtos porque nao existe onde guardar essa relacao - nem no aparelho,
 * nem na API (o /api/user-list-items depende de um userId que o app ainda nao
 * tem). Ate entao a tela mostra o bloco de lista vazia.
 */
public class DetalheListaActivity extends AppCompatActivity {

    public static final String EXTRA_NOME = "nome";
    public static final String EXTRA_DESCRICAO = "descricao";
    public static final String EXTRA_PUBLICA = "publica";
    public static final String EXTRA_AUTOR = "autor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detalhe_lista);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        preencher();
    }

    private void preencher() {
        TextView chip = findViewById(R.id.chipPrivacidade);
        TextView nome = findViewById(R.id.textNomeLista);
        TextView autor = findViewById(R.id.textAutorLista);
        TextView descricao = findViewById(R.id.textDescricaoLista);

        boolean publica = getIntent().getBooleanExtra(EXTRA_PUBLICA, false);
        chip.setText(publica ? R.string.lista_publica : R.string.lista_privada);

        nome.setText(getIntent().getStringExtra(EXTRA_NOME));
        autor.setText(getString(R.string.lista_criada_por, nomeDoAutor()));

        String texto = getIntent().getStringExtra(EXTRA_DESCRICAO);
        boolean temDescricao = texto != null && !texto.trim().isEmpty();
        descricao.setText(temDescricao ? texto : "");
        descricao.setVisibility(temDescricao ? View.VISIBLE : View.GONE);
    }

    /**
     * Listas que o usuario segue trazem o autor no Intent. As dele proprio nao
     * trazem nada, e ai o nome sai da conta logada.
     */
    private String nomeDoAutor() {
        String doIntent = getIntent().getStringExtra(EXTRA_AUTOR);
        if (doIntent != null && !doIntent.trim().isEmpty()) {
            return doIntent;
        }

        Usuario usuario = new AutenticacaoRepository().usuarioLogado();
        if (usuario == null || usuario.getNome() == null || usuario.getNome().trim().isEmpty()) {
            return getString(R.string.perfil_sem_nome);
        }
        return usuario.getNome();
    }
}
