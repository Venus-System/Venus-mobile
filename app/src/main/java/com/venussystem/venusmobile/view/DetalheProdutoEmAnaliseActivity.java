package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import coil.Coil;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;
import com.venussystem.venusmobile.view.adapter.ProdutoEmAnaliseAdapter;
import com.venussystem.venusmobile.view.util.SituacaoAnaliseUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Um produto enviado para analise. Recebe o produto inteiro pelo Intent (ver
 * ProdutosEmAnaliseActivity), sem ir de novo na API.
 */
public class DetalheProdutoEmAnaliseActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUTO = "produto_em_analise";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_detalhe_produto_em_analise);
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        ProdutoEmAnalise produto =
                getIntent().getSerializableExtra(EXTRA_PRODUTO, ProdutoEmAnalise.class);
        if (produto == null) {
            finish();
            return;
        }
        mostrar(produto);
    }

    private void mostrar(ProdutoEmAnalise produto) {
        carregarFoto(findViewById(R.id.imgFrente), produto.getFotoFrente());
        carregarFoto(findViewById(R.id.imgVerso), produto.getFotoVerso());

        TextView nome = findViewById(R.id.textNome);
        if (produto.getNome() != null) {
            nome.setText(produto.getNome());
        } else {
            nome.setText(R.string.analise_sem_nome);
        }
        TextView marca = findViewById(R.id.textMarca);
        marca.setText(produto.getMarca());
        marca.setVisibility(produto.getMarca() == null ? View.GONE : View.VISIBLE);

        SituacaoAnaliseUtil.aplicarSelo(findViewById(R.id.textSituacao), produto.getSituacao());
        ((TextView) findViewById(R.id.textExplicacao)).setText(
                SituacaoAnaliseUtil.explicacao(produto.getSituacao(), produto.getProdutoId()));

        mostrarDatas(produto);

        boolean recusado = produto.getSituacao() == Situacao.RECUSADO;
        if (recusado && produto.getMotivo() != null) {
            ((TextView) findViewById(R.id.textMotivo)).setText(produto.getMotivo());
            findViewById(R.id.blocoMotivo).setVisibility(View.VISIBLE);
        }

        Long produtoId = produto.getProdutoId();
        if (produtoId != null) {
            View verProduto = findViewById(R.id.btnVerProduto);
            verProduto.setVisibility(View.VISIBLE);
            verProduto.setOnClickListener(v -> {
                Intent intent = new Intent(this, DetalheProdutoActivity.class);
                intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produtoId.longValue());
                startActivity(intent);
            });
        }
    }

    /** "Enviado em", e embaixo "Aprovado em"/"Recusado em" quando ja decidiram. */
    private void mostrarDatas(ProdutoEmAnalise produto) {
        List<String> linhas = new ArrayList<>();
        if (produto.getEnviadoEm() != null) {
            linhas.add(getString(R.string.analise_enviado_em,
                    produto.getEnviadoEm().format(ProdutoEmAnaliseAdapter.FORMATO_DATA)));
        }
        if (produto.getDecididoEm() != null) {
            String dia = produto.getDecididoEm().format(ProdutoEmAnaliseAdapter.FORMATO_DATA);
            if (produto.getSituacao() == Situacao.APROVADO) {
                linhas.add(getString(R.string.analise_aprovado_em, dia));
            } else if (produto.getSituacao() == Situacao.RECUSADO) {
                linhas.add(getString(R.string.analise_recusado_em, dia));
            }
        }
        TextView datas = findViewById(R.id.textDatas);
        datas.setText(String.join("\n", linhas));
        datas.setVisibility(linhas.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void carregarFoto(ImageView alvo, @Nullable String link) {
        Coil.imageLoader(this).enqueue(new ImageRequest.Builder(this)
                .data(link)
                .target(alvo)
                .placeholder(R.drawable.bg_foto_produto)
                .error(R.drawable.bg_foto_produto)
                .fallback(R.drawable.bg_foto_produto)
                .build());
    }
}
