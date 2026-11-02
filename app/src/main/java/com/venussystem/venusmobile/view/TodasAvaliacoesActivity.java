package com.venussystem.venusmobile.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Avaliacao;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.AvaliacoesRepository;
import com.venussystem.venusmobile.view.adapter.AvaliacaoAdapter;
import com.venussystem.venusmobile.view.componente.DialogoDenuncia;
import com.venussystem.venusmobile.view.componente.VotosDasAvaliacoes;
import com.venussystem.venusmobile.view.util.MensagemRapida;

public class TodasAvaliacoesActivity extends AppCompatActivity {

    private static final String EXTRA_PRODUTO_ID = "produto_id";
    private static final String EXTRA_NOME = "nome";
    private static final String EXTRA_MARCA = "marca";

    private long produtoId;
    private AvaliacoesRepository repositorio;
    private VotosDasAvaliacoes votos;
    private AvaliacaoAdapter adapter;
    private RecyclerView lista;
    private View carregando;
    private View blocoErro;
    private int pedido = 0;

    private final ActivityResultLauncher<Intent> abrirEscrita =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    resultado -> {
                        if (resultado.getResultCode() != RESULT_OK) return;
                        carregar();
                        MensagemRapida.sucesso(lista,
                                getString(R.string.avaliacoes_publicada)).show();
                    });

    public static Intent intent(Context contexto, @NonNull Produto produto) {
        return new Intent(contexto, TodasAvaliacoesActivity.class)
                .putExtra(EXTRA_PRODUTO_ID, produto.getId())
                .putExtra(EXTRA_NOME, produto.getName())
                .putExtra(EXTRA_MARCA, produto.getBrandName());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_todas_avaliacoes);

        produtoId = getIntent().getLongExtra(EXTRA_PRODUTO_ID, -1L);
        if (produtoId <= 0) {
            finish();
            return;
        }
        setResult(RESULT_OK);

        repositorio = new AvaliacoesRepository(this);
        lista = findViewById(R.id.listaTodasAvaliacoes);
        carregando = findViewById(R.id.carregando);
        blocoErro = findViewById(R.id.blocoErroTodas);

        votos = new VotosDasAvaliacoes(repositorio, id -> adapter.atualizar(id));
        adapter = new AvaliacaoAdapter(votos, this::denunciar,
                () -> abrirEscrita.launch(EscreverAvaliacaoActivity.intent(this, produtoId,
                        getIntent().getStringExtra(EXTRA_NOME),
                        getIntent().getStringExtra(EXTRA_MARCA))));
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnRecarregarTodas).setOnClickListener(v -> carregar());

        carregar();
    }

    private void carregar() {
        int atual = ++pedido;
        carregando.setVisibility(View.VISIBLE);
        blocoErro.setVisibility(View.GONE);
        repositorio.buscar(produtoId, lidas -> {
            if (atual != pedido || isFinishing() || isDestroyed()) return;
            carregando.setVisibility(View.GONE);
            if (lidas == null) {
                lista.setVisibility(View.GONE);
                blocoErro.setVisibility(View.VISIBLE);
                return;
            }
            votos.esquecer();
            adapter.mostrar(lidas.getAvaliacoes(), lidas.isTruncada());
            lista.setVisibility(View.VISIBLE);
        });
    }

    private void denunciar(Avaliacao avaliacao) {
        DialogoDenuncia.mostrar(this, avaliacao, repositorio, lista);
    }
}
