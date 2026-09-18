package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import com.google.android.material.tabs.TabLayout;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.view.adapter.AlternativaAdapter;
import com.venussystem.venusmobile.view.util.NotaProdutoUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela de um produto. Aberta a partir de uma lista que ja mostrava esse
 * produto (Busca, Historico, Alternativas), entao o catalogo ja esta em
 * memoria e o produto e lido de la, sem nova chamada de rede.
 *
 * A aba "Match com voce" nao tem dado real: a API nao tem nenhum endpoint que
 * receba usuario+produto e devolva compatibilidade, entao mostra um aviso
 * honesto em vez de inventar percentual ou motivo.
 */
public class DetalheProdutoActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "produto_id";

    private static final int ABA_AVALIACAO = 0;
    private static final int ABA_MATCH = 1;
    private static final int ABA_ALTERNATIVAS = 2;

    private final ProdutoRepository repository = new ProdutoRepository();

    private View conteudoAvaliacao;
    private View conteudoMatch;
    private RecyclerView listaAlternativas;
    private TextView textSemAlternativas;

    private View carregandoIngredientes;
    private TextView textIngredientes;
    private ImageView imgProduto;

    private Produto produto;
    private boolean ingredientesCarregados;
    private boolean alternativasMontadas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_detalhe_produto);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        produto = repository.buscarNoCache(id);

        if (produto == null) {
            // So aconteceria com um link direto para um produto fora do
            // catalogo carregado; nas telas do app isso nunca acontece porque
            // so se chega aqui clicando num produto que ja estava na lista.
            Toast.makeText(this, R.string.produto_sem_alternativas, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        conteudoAvaliacao = findViewById(R.id.conteudoAvaliacao);
        conteudoMatch = findViewById(R.id.conteudoMatch);
        listaAlternativas = findViewById(R.id.listaAlternativas);
        textSemAlternativas = findViewById(R.id.textSemAlternativas);
        carregandoIngredientes = findViewById(R.id.carregandoIngredientes);
        textIngredientes = findViewById(R.id.textIngredientes);
        imgProduto = findViewById(R.id.imgProduto);

        preencherCabecalho();
        prepararAbas();
    }

    private void preencherCabecalho() {
        ((TextView) findViewById(R.id.textNomeProduto)).setText(produto.getName());

        TextView categoria = findViewById(R.id.textCategoriaProduto);
        boolean temCategoria = produto.getCategoryName() != null
                && !produto.getCategoryName().trim().isEmpty();
        categoria.setText(temCategoria ? produto.getCategoryName() : "");
        categoria.setVisibility(temCategoria ? View.VISIBLE : View.GONE);

        TextView nota = findViewById(R.id.textNotaProduto);
        if (produto.getOverallScore() == null) {
            nota.setText(R.string.nota_indisponivel);
        } else {
            nota.setText(String.valueOf(produto.getOverallScore()));
        }
        Integer valorNota = produto.getOverallScore();
        nota.setTextColor(ContextCompat.getColor(this, NotaProdutoUtil.corPara(valorNota)));
        nota.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, NotaProdutoUtil.fundoPara(valorNota))));

        carregarImagem(produto.getImageUrl());
    }

    private void carregarImagem(String url) {
        ImageLoader carregador = Coil.imageLoader(this);
        carregador.enqueue(new ImageRequest.Builder(this)
                .data(url)
                .target(imgProduto)
                .placeholder(R.drawable.bg_foto_produto)
                .error(R.drawable.bg_foto_produto)
                .fallback(R.drawable.bg_foto_produto)
                .build());
    }

    private void prepararAbas() {
        TabLayout abas = findViewById(R.id.abasProduto);
        abas.addTab(abas.newTab().setText(R.string.produto_aba_avaliacao));
        abas.addTab(abas.newTab().setText(R.string.produto_aba_match));
        abas.addTab(abas.newTab().setText(R.string.produto_aba_alternativas));

        abas.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab aba) {
                mostrarAba(aba.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab aba) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab aba) {
            }
        });

        mostrarAba(ABA_AVALIACAO);
    }

    private void mostrarAba(int aba) {
        conteudoAvaliacao.setVisibility(aba == ABA_AVALIACAO ? View.VISIBLE : View.GONE);
        conteudoMatch.setVisibility(aba == ABA_MATCH ? View.VISIBLE : View.GONE);

        boolean naAlternativas = aba == ABA_ALTERNATIVAS;
        listaAlternativas.setVisibility(
                naAlternativas && !listaVazia() ? View.VISIBLE : View.GONE);
        textSemAlternativas.setVisibility(
                naAlternativas && listaVazia() ? View.VISIBLE : View.GONE);

        if (aba == ABA_AVALIACAO && !ingredientesCarregados) {
            carregarIngredientes();
        }
        if (naAlternativas && !alternativasMontadas) {
            montarAlternativas();
        }
    }

    private boolean listaVazia() {
        return listaAlternativas.getAdapter() == null
                || listaAlternativas.getAdapter().getItemCount() == 0;
    }

    private void carregarIngredientes() {
        ingredientesCarregados = true;
        carregandoIngredientes.setVisibility(View.VISIBLE);
        textIngredientes.setVisibility(View.GONE);

        repository.buscarDetalheProduto(produto.getId(), (textoRotulo, urlFoto) -> {
            carregandoIngredientes.setVisibility(View.GONE);
            textIngredientes.setVisibility(View.VISIBLE);

            boolean temTexto = textoRotulo != null && !textoRotulo.trim().isEmpty();
            textIngredientes.setText(temTexto
                    ? textoRotulo : getString(R.string.produto_ingredientes_erro));

            if (urlFoto != null) {
                carregarImagem(urlFoto);
            }
        });
    }

    /**
     * Outros produtos da mesma categoria, sem contar o proprio. Sai do
     * catalogo ja carregado - mesma fonte da Busca, sem chamada nova.
     */
    private void montarAlternativas() {
        alternativasMontadas = true;

        List<Produto> catalogo = repository.getCatalogo().getValue();
        List<Produto> alternativas = new ArrayList<>();

        if (catalogo != null && produto.getCategoryId() != null) {
            for (Produto candidato : catalogo) {
                boolean mesmaCategoria = produto.getCategoryId().equals(candidato.getCategoryId());
                boolean ehOutro = !produto.getId().equals(candidato.getId());
                if (mesmaCategoria && ehOutro) {
                    alternativas.add(candidato);
                }
            }
        }

        AlternativaAdapter adapter = new AlternativaAdapter(this::abrirProduto);
        adapter.atualizar(alternativas);

        listaAlternativas.setLayoutManager(new LinearLayoutManager(this));
        listaAlternativas.setAdapter(adapter);

        listaAlternativas.setVisibility(alternativas.isEmpty() ? View.GONE : View.VISIBLE);
        textSemAlternativas.setVisibility(alternativas.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void abrirProduto(Produto outroProduto) {
        Intent intent = new Intent(this, DetalheProdutoActivity.class);
        intent.putExtra(EXTRA_ID, outroProduto.getId());
        startActivity(intent);
    }
}
