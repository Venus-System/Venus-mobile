package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.view.adapter.ProdutoEmAnaliseAdapter;
import com.venussystem.venusmobile.viewmodel.ProdutosEmAnaliseViewModel;
import com.venussystem.venusmobile.viewmodel.ProdutosEmAnaliseViewModel.Estado;

import java.util.List;

/**
 * Os produtos que a pessoa escaneou, nao achou no catalogo e mandou para a
 * equipe conferir, com a situacao de cada um. Aberta pelo menu lateral do
 * perfil. Tocar num produto abre o detalhe (DetalheProdutoEmAnaliseActivity).
 */
public class ProdutosEmAnaliseActivity extends AppCompatActivity {

    private RecyclerView lista;
    private View carregando;
    private View blocoVazio;
    private View blocoErro;
    private ProdutoEmAnaliseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_produtos_em_analise);

        lista = findViewById(R.id.listaProdutosEmAnalise);
        carregando = findViewById(R.id.carregando);
        blocoVazio = findViewById(R.id.blocoVazio);
        blocoErro = findViewById(R.id.blocoErro);

        adapter = new ProdutoEmAnaliseAdapter(this::abrirDetalhe);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adapter);

        ProdutosEmAnaliseViewModel viewModel =
                new ViewModelProvider(this).get(ProdutosEmAnaliseViewModel.class);
        viewModel.getProdutos().observe(this, adapter::atualizar);
        viewModel.getEstado().observe(this, estado ->
                mostrar(estado, viewModel.getProdutos().getValue()));

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnTentarDeNovo).setOnClickListener(v -> viewModel.carregar());
    }

    private void mostrar(Estado estado, List<ProdutoEmAnalise> produtos) {
        boolean pronto = estado == Estado.PRONTO;
        boolean vazio = pronto && (produtos == null || produtos.isEmpty());
        carregando.setVisibility(estado == Estado.CARREGANDO ? View.VISIBLE : View.GONE);
        blocoErro.setVisibility(estado == Estado.ERRO ? View.VISIBLE : View.GONE);
        blocoVazio.setVisibility(vazio ? View.VISIBLE : View.GONE);
        lista.setVisibility(pronto && !vazio ? View.VISIBLE : View.GONE);
    }

    private void abrirDetalhe(ProdutoEmAnalise produto) {
        Intent intent = new Intent(this, DetalheProdutoEmAnaliseActivity.class);
        intent.putExtra(DetalheProdutoEmAnaliseActivity.EXTRA_PRODUTO, produto);
        startActivity(intent);
    }
}
