package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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

/**
 * Tela de detalhes do produto.
 *
 * O produto é identificado pelo ID recebido através do Intent.
 */
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

    private Produto produto;

    private boolean ingredientesCarregados =
            false;

    private boolean alternativasMontadas =
            false;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(
                R.layout.activity_detalhe_produto
        );

        View main =
                findViewById(R.id.main);

        if (main != null) {

            ViewCompat.setOnApplyWindowInsetsListener(
                    main,
                    (v, insets) -> {

                        Insets systemBars =
                                insets.getInsets(
                                        WindowInsetsCompat.Type.systemBars()
                                );

                        v.setPadding(
                                systemBars.left,
                                systemBars.top,
                                systemBars.right,
                                systemBars.bottom
                        );

                        return insets;
                    }
            );
        }

        /*
         * IMPORTANTE:
         *
         * A ScanSuccessFrontActivity envia o ID
         * com putExtra(EXTRA_ID, produtoId).
         */
        long id =
                getIntent().getLongExtra(
                        EXTRA_ID,
                        -1L
                );

        Log.d(
                TAG,
                "ID RECEBIDO DO SCAN: "
                        + id
        );

        /*
         * ID inválido.
         */
        if (id <= 0) {

            Log.e(
                    TAG,
                    "ID DO PRODUTO INVÁLIDO: "
                            + id
            );

            Toast.makeText(
                    this,
                    R.string.produto_sem_alternativas,
                    Toast.LENGTH_SHORT
            ).show();

            finish();

            return;
        }

        /*
         * Busca EXATAMENTE o produto daquele ID
         * no catálogo já carregado.
         */
        produto =
                repository.buscarNoCache(id);

        /*
         * Produto não está no catálogo.
         */
        if (produto == null) {

            Log.e(
                    TAG,
                    "PRODUTO NÃO ENCONTRADO NO CACHE"
            );

            Log.e(
                    TAG,
                    "ID PROCURADO: "
                            + id
            );

            Toast.makeText(
                    this,
                    R.string.produto_sem_alternativas,
                    Toast.LENGTH_SHORT
            ).show();

            finish();

            return;
        }

        /*
         * Confirma exatamente qual registro foi aberto.
         */
        Log.d(
                TAG,
                "PRODUTO ABERTO:"
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

        findViewById(
                R.id.btnVoltar
        ).setOnClickListener(
                v -> finish()
        );

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

        preencherCabecalho();

        prepararAbas();
    }

    /**
     * Preenche nome, categoria, nota e imagem.
     */
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

        ImageView imagem =
                findViewById(
                        R.id.imgProduto
                );

        ImageLoader carregador =
                Coil.imageLoader(this);

        carregador.enqueue(
                new ImageRequest.Builder(this)
                        .data(produto.getImageUrl())
                        .target(imagem)
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

    /**
     * Configura as abas.
     */
    private void prepararAbas() {

        TabLayout abas =
                findViewById(
                        R.id.abasProduto
                );

        /*
         * Evita adicionar as abas duas vezes
         * caso a Activity seja reconstruída.
         */
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

    /**
     * Exibe o conteúdo da aba selecionada.
     */
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
                naAlternativas
                        && !listaVazia()
                        ? View.VISIBLE
                        : View.GONE
        );

        textSemAlternativas.setVisibility(
                naAlternativas
                        && listaVazia()
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

    /**
     * Verifica se a lista de alternativas está vazia.
     */
    private boolean listaVazia() {

        return listaAlternativas.getAdapter() == null
                || listaAlternativas
                .getAdapter()
                .getItemCount() == 0;
    }

    /**
     * Carrega ingredientes do produto.
     */
    private void carregarIngredientes() {

        ingredientesCarregados = true;

        carregandoIngredientes.setVisibility(
                View.VISIBLE
        );

        textIngredientes.setVisibility(
                View.GONE
        );

        repository.buscarIngredientes(
                produto.getId(),
                texto -> {

                    carregandoIngredientes.setVisibility(
                            View.GONE
                    );

                    textIngredientes.setVisibility(
                            View.VISIBLE
                    );

                    boolean temTexto =
                            texto != null
                                    && !texto
                                    .trim()
                                    .isEmpty();

                    textIngredientes.setText(
                            temTexto
                                    ? texto
                                    : getString(
                                    R.string.produto_ingredientes_erro
                            )
                    );
                }
        );
    }

    /**
     * Monta alternativas da mesma categoria.
     */
    private void montarAlternativas() {

        alternativasMontadas = true;

        java.util.List<Produto> catalogo =
                repository
                        .getCatalogo()
                        .getValue();

        java.util.List<Produto> alternativas =
                new java.util.ArrayList<>();

        if (catalogo != null
                && produto.getCategoryId() != null) {

            for (Produto candidato :
                    catalogo) {

                if (candidato == null) {
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

    /**
     * Abre outro produto da lista de alternativas.
     */
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