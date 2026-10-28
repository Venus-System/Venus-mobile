package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.CurtidosRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.repository.SincronizacaoCurtidos;
import com.venussystem.venusmobile.view.adapter.ItemListaAdapter;
import com.venussystem.venusmobile.view.util.MensagemRapida;

import java.util.ArrayList;
import java.util.List;

public class ProdutosCurtidosActivity extends AppCompatActivity {

    private CurtidosRepository curtidos;
    private ProdutoRepository produtoRepository;
    private ItemListaAdapter adapter;
    private RecyclerView lista;
    private View carregando;
    private View blocoVazio;

    private boolean alterados = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_produtos_curtidos);

        curtidos = new CurtidosRepository(this);
        produtoRepository = new ProdutoRepository();

        lista = findViewById(R.id.listaCurtidos);
        carregando = findViewById(R.id.carregando);
        blocoVazio = findViewById(R.id.blocoVazio);

        adapter = new ItemListaAdapter(this::abrirProduto, this::descurtir);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        produtoRepository.getCatalogo().observe(this, catalogo -> carregar());
        produtoRepository.getCarregando().observe(this, emAndamento -> carregar());
        produtoRepository.carregar(false);

        new SincronizacaoCurtidos(this).atualizarEmSegundoPlano(this::carregar);
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (alterados) {
            alterados = false;
            new SincronizacaoCurtidos(this).sincronizarEmSegundoPlano();
        }
    }

    private void carregar() {
        List<Long> ids = curtidos.getProdutoIds();
        List<Produto> produtos = new ArrayList<>();
        for (Long id : ids) {
            Produto produto = produtoRepository.buscarNoCache(id);
            if (produto != null) {
                produtos.add(produto);
            }
        }
        adapter.atualizar(produtos);

        boolean esperandoCatalogo = !ids.isEmpty()
                && produtoRepository.getCatalogo().getValue() == null
                && Boolean.TRUE.equals(produtoRepository.getCarregando().getValue());
        boolean vazio = produtos.isEmpty();

        carregando.setVisibility(esperandoCatalogo ? View.VISIBLE : View.GONE);
        blocoVazio.setVisibility(!esperandoCatalogo && vazio ? View.VISIBLE : View.GONE);
        lista.setVisibility(vazio ? View.GONE : View.VISIBLE);
    }

    private void abrirProduto(Produto produto) {
        Intent intent = new Intent(this, DetalheProdutoActivity.class);
        intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produto.getId());
        startActivity(intent);
    }

    private void descurtir(Produto produto) {
        curtidos.descurtir(produto.getId());
        alterados = true;
        carregar();
        MensagemRapida.neutra(findViewById(R.id.main), getString(R.string.produto_descurtido));
    }
}
