package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.google.android.material.tabs.TabLayout;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.IngredienteProduto;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.view.adapter.AlternativaAdapter;
import com.venussystem.venusmobile.view.util.NotaProdutoUtil;

import java.util.ArrayList;
import java.util.List;


public class DetalheProdutoActivity extends AppCompatActivity {

    public static final String EXTRA_ID =
            "produto_id";

    private static final String TAG =
            "VENUS_DETAIL";

    private static final int ABA_AVALIACAO = 0;
    private static final int ABA_MATCH = 1;
    private static final int ABA_ALTERNATIVAS = 2;

    private final ProdutoRepository repository =
            new ProdutoRepository();

    private View conteudoAvaliacao;
    private View conteudoMatch;
    private RecyclerView listaAlternativas;
    private TextView textSemAlternativas;

    private View carregandoIngredientes;
    private TextView textIngredientes;
    private LinearLayout listaIngredientes;
    private ImageView imgProduto;

    private Produto produto;
    private long produtoIdRecebido = -1L;

    private boolean ingredientesCarregados = false;
    private boolean alternativasMontadas = false;
    private boolean telaInicializada = false;
    private boolean mensagemErroMostrada = false;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_detalhe_produto);

        prepararReferenciasDeTela();

        findViewById(
                R.id.btnVoltar
        ).setOnClickListener(
                v -> finish()
        );

        produtoIdRecebido =
                getIntent().getLongExtra(
                        EXTRA_ID,
                        -1L
                );

        Log.d(
                TAG,
                "ID RECEBIDO DO SCAN: "
                        + produtoIdRecebido
        );

        if (produtoIdRecebido <= 0) {

            finalizarSemProduto(
                    "ID DO PRODUTO INVÁLIDO: "
                            + produtoIdRecebido
            );

            return;
        }

        resolverProduto();
    }

    private void prepararReferenciasDeTela() {
        imgProduto = findViewById(R.id.imgProduto);

        conteudoAvaliacao =
                findViewById(
                        R.id.conteudoAvaliacao
                );

        conteudoMatch =
                findViewById(
                        R.id.conteudoMatch
                );

        listaAlternativas =
                findViewById(
                        R.id.listaAlternativas
                );

        textSemAlternativas =
                findViewById(
                        R.id.textSemAlternativas
                );

        carregandoIngredientes =
                findViewById(
                        R.id.carregandoIngredientes
                );

        textIngredientes =
                findViewById(
                        R.id.textIngredientes
                );

        listaIngredientes =
                findViewById(
                        R.id.listaIngredientes
                );
    }

    private void resolverProduto() {

        Produto emCache =
                repository.buscarNoCache(
                        produtoIdRecebido
                );

        if (emCache != null) {

            inicializarComProduto(
                    emCache
            );

            return;
        }

        repository
                .getCatalogo()
                .observe(
                        this,
                        catalogo -> {

                            if (telaInicializada
                                    || catalogo == null) {
                                return;
                            }

                            Produto encontrado =
                                    encontrarPorId(
                                            catalogo,
                                            produtoIdRecebido
                                    );

                            if (encontrado != null) {

                                inicializarComProduto(
                                        encontrado
                                );

                            } else {

                                Boolean carregando =
                                        repository
                                                .getCarregando()
                                                .getValue();

                                if (!Boolean.TRUE.equals(
                                        carregando
                                )) {

                                    finalizarSemProduto(
                                            "ID não encontrado no catálogo: "
                                                    + produtoIdRecebido
                                    );
                                }
                            }
                        }
                );

        repository
                .getErro()
                .observe(
                        this,
                        erro -> {

                            if (telaInicializada
                                    || erro == null
                                    || erro.trim().isEmpty()
                                    || mensagemErroMostrada) {
                                return;
                            }

                            Boolean carregando =
                                    repository
                                            .getCarregando()
                                            .getValue();

                            if (Boolean.TRUE.equals(
                                    carregando
                            )) {
                                return;
                            }

                            mensagemErroMostrada = true;

                            finalizarSemProduto(
                                    erro
                            );
                        }
                );

        Boolean carregandoAtual =
                repository
                        .getCarregando()
                        .getValue();

        if (!Boolean.TRUE.equals(
                carregandoAtual
        )) {

            repository.carregar(false);
        }
    }

    private Produto encontrarPorId(
            @NonNull List<Produto> catalogo,
            long id
    ) {

        for (Produto candidato : catalogo) {

            if (candidato == null
                    || candidato.getId() == null) {
                continue;
            }

            if (candidato.getId().longValue()
                    == id) {

                return candidato;
            }
        }

        return null;
    }

    private void inicializarComProduto(
            @NonNull Produto produtoEncontrado
    ) {

        if (telaInicializada) {
            return;
        }

        if (produtoEncontrado.getId() == null
                || produtoEncontrado.getId() <= 0) {

            finalizarSemProduto(
                    "Produto encontrado sem ID válido."
            );

            return;
        }

        produto = produtoEncontrado;
        telaInicializada = true;

        Log.d(
                TAG,
                "PRODUTO ABERTO"
        );

        Log.d(
                TAG,
                "ID: "
                        + produto.getId()
        );

        Log.d(
                TAG,
                "NOME: "
                        + produto.getName()
        );

        Log.d(
                TAG,
                "MARCA: "
                        + produto.getBrandName()
        );

        preencherCabecalho();
        prepararAbas();
    }

    private void finalizarSemProduto(
            String logOuMensagem
    ) {

        Log.e(
                TAG,
                logOuMensagem
        );

        Toast.makeText(
                this,
                R.string.produto_sem_alternativas,
                Toast.LENGTH_SHORT
        ).show();

        finish();
    }

    private void preencherCabecalho() {

        TextView nome =
                findViewById(
                        R.id.textNomeProduto
                );

        nome.setText(
                produto.getName()
        );

        TextView categoria =
                findViewById(
                        R.id.textCategoriaProduto
                );

        boolean temCategoria =
                produto.getCategoryName() != null
                        && !produto
                        .getCategoryName()
                        .trim()
                        .isEmpty();

        categoria.setText(
                temCategoria
                        ? produto.getCategoryName()
                        : ""
        );

        categoria.setVisibility(
                temCategoria
                        ? View.VISIBLE
                        : View.GONE
        );

        TextView nota =
                findViewById(
                        R.id.textNotaProduto
                );

        if (produto.getOverallScore() == null) {

            nota.setText(
                    R.string.nota_indisponivel
            );

        } else {

            nota.setText(
                    String.valueOf(
                            produto.getOverallScore()
                    )
            );
        }

        Integer valorNota =
                produto.getOverallScore();

        nota.setTextColor(
                ContextCompat.getColor(
                        this,
                        NotaProdutoUtil.corPara(
                                valorNota
                        )
                )
        );

        nota.setBackgroundTintList(
                ColorStateList.valueOf(
                        ContextCompat.getColor(
                                this,
                                NotaProdutoUtil.fundoPara(
                                        valorNota
                                )
                        )
                )
        );

        carregarImagem(produto.getImageUrl());
    }

    private void carregarImagem(String url) {
        ImageLoader carregador =
                Coil.imageLoader(this);

        carregador.enqueue(
                new ImageRequest.Builder(this)
                        .data(url)
                        .target(imgProduto)
                        .placeholder(
                                R.drawable.bg_foto_produto
                        )
                        .error(
                                R.drawable.bg_foto_produto
                        )
                        .fallback(
                                R.drawable.bg_foto_produto
                        )
                        .build()
        );
    }

    private void prepararAbas() {

        TabLayout abas =
                findViewById(
                        R.id.abasProduto
                );

        if (abas.getTabCount() == 0) {

            abas.addTab(
                    abas.newTab()
                            .setText(
                                    R.string.produto_aba_avaliacao
                            )
            );

            abas.addTab(
                    abas.newTab()
                            .setText(
                                    R.string.produto_aba_match
                            )
            );

            abas.addTab(
                    abas.newTab()
                            .setText(
                                    R.string.produto_aba_alternativas
                            )
            );
        }

        abas.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {

                    @Override
                    public void onTabSelected(
                            TabLayout.Tab aba
                    ) {
                        mostrarAba(
                                aba.getPosition()
                        );
                    }

                    @Override
                    public void onTabUnselected(
                            TabLayout.Tab aba
                    ) {
                    }

                    @Override
                    public void onTabReselected(
                            TabLayout.Tab aba
                    ) {
                    }
                }
        );

        mostrarAba(
                ABA_AVALIACAO
        );
    }

    private void mostrarAba(
            int aba
    ) {

        conteudoAvaliacao.setVisibility(
                aba == ABA_AVALIACAO
                        ? View.VISIBLE
                        : View.GONE
        );

        conteudoMatch.setVisibility(
                aba == ABA_MATCH
                        ? View.VISIBLE
                        : View.GONE
        );

        boolean naAlternativas =
                aba == ABA_ALTERNATIVAS;

        listaAlternativas.setVisibility(
                naAlternativas && !listaVazia()
                        ? View.VISIBLE
                        : View.GONE
        );

        textSemAlternativas.setVisibility(
                naAlternativas && listaVazia()
                        ? View.VISIBLE
                        : View.GONE
        );

        if (aba == ABA_AVALIACAO
                && !ingredientesCarregados) {

            carregarIngredientes();
        }

        if (naAlternativas
                && !alternativasMontadas) {

            montarAlternativas();
        }
    }

    private boolean listaVazia() {

        return listaAlternativas.getAdapter() == null
                || listaAlternativas
                .getAdapter()
                .getItemCount() == 0;
    }

    @SuppressWarnings("unchecked")
    private void carregarIngredientes() {

        ingredientesCarregados = true;

        carregandoIngredientes.setVisibility(View.VISIBLE);
        textIngredientes.setVisibility(View.GONE);
        listaIngredientes.setVisibility(View.GONE);

        // Duas chamadas independentes: o rotulo (texto corrido e foto) e a
        // lista de ingredientes com descricao. A tela so se monta quando as
        // duas voltam, para nao piscar o texto corrido antes da lista.
        final String[] rotulo = new String[1];
        final List<IngredienteProduto>[] lista = new List[1];
        final int[] pendentes = {2};
        Runnable concluida = () -> {
            if (--pendentes[0] > 0 || isFinishing() || isDestroyed()) return;
            mostrarIngredientes(lista[0], rotulo[0]);
        };

        repository.buscarDetalheProduto(produto.getId(), (textoRotulo, urlFoto) -> {
            if (isFinishing() || isDestroyed()) return;
            rotulo[0] = textoRotulo;
            if (urlFoto != null) {
                carregarImagem(urlFoto);
            }
            concluida.run();
        });

        repository.buscarIngredientesDoProduto(produto.getId(), ingredientes -> {
            lista[0] = ingredientes;
            concluida.run();
        });
    }

    /**
     * Um ingrediente por item, com a descricao embaixo. Sem lista (produto sem
     * ingredientes cadastrados ou chamada falhou) cai no texto corrido do
     * rotulo, que era o comportamento anterior.
     */
    private void mostrarIngredientes(@Nullable List<IngredienteProduto> ingredientes,
                                     @Nullable String textoRotulo) {
        carregandoIngredientes.setVisibility(View.GONE);

        if (ingredientes != null && !ingredientes.isEmpty()) {
            listaIngredientes.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);
            for (IngredienteProduto ingrediente : ingredientes) {
                View item = inflater.inflate(
                        R.layout.item_ingrediente_produto, listaIngredientes, false);
                ((TextView) item.findViewById(R.id.textNomeIngrediente))
                        .setText(ingrediente.getNome());
                preencherOpcional(item.findViewById(R.id.textInciIngrediente),
                        ingrediente.getNomeInci());
                preencherOpcional(item.findViewById(R.id.textDescricaoIngrediente),
                        ingrediente.getDescricao());
                listaIngredientes.addView(item);
            }
            listaIngredientes.setVisibility(View.VISIBLE);
            return;
        }

        boolean temTexto = textoRotulo != null && !textoRotulo.trim().isEmpty();
        textIngredientes.setText(temTexto
                ? textoRotulo : getString(R.string.produto_ingredientes_erro));
        textIngredientes.setVisibility(View.VISIBLE);
    }

    private void preencherOpcional(TextView view, @Nullable String texto) {
        boolean tem = texto != null && !texto.isEmpty();
        view.setText(tem ? texto : null);
        view.setVisibility(tem ? View.VISIBLE : View.GONE);
    }

    private void montarAlternativas() {

        alternativasMontadas = true;

        List<Produto> catalogo =
                repository
                        .getCatalogo()
                        .getValue();

        List<Produto> alternativas =
                new ArrayList<>();

        if (catalogo != null
                && produto.getCategoryId() != null) {

            for (Produto candidato :
                    catalogo) {

                if (candidato == null
                        || candidato.getId() == null) {
                    continue;
                }

                boolean mesmaCategoria =
                        produto.getCategoryId()
                                .equals(
                                        candidato.getCategoryId()
                                );

                boolean ehOutro =
                        !produto.getId()
                                .equals(
                                        candidato.getId()
                                );

                if (mesmaCategoria
                        && ehOutro) {

                    alternativas.add(
                            candidato
                    );
                }
            }
        }

        AlternativaAdapter adapter =
                new AlternativaAdapter(
                        this::abrirProduto
                );

        adapter.atualizar(
                alternativas
        );

        listaAlternativas.setLayoutManager(
                new LinearLayoutManager(this)
        );

        listaAlternativas.setAdapter(
                adapter
        );

        boolean vazia =
                alternativas.isEmpty();

        listaAlternativas.setVisibility(
                vazia
                        ? View.GONE
                        : View.VISIBLE
        );

        textSemAlternativas.setVisibility(
                vazia
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void abrirProduto(
            @NonNull Produto outroProduto
    ) {

        if (outroProduto.getId() == null
                || outroProduto.getId() <= 0) {
            return;
        }

        Intent intent =
                new Intent(
                        this,
                        DetalheProdutoActivity.class
                );

        intent.putExtra(
                EXTRA_ID,
                outroProduto
                        .getId()
                        .longValue()
        );

        startActivity(intent);
    }
}
