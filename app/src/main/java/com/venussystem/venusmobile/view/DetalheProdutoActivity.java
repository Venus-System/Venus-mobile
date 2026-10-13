package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.google.android.material.tabs.TabLayout;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;
import com.venussystem.venusmobile.model.IngredienteProduto;
import com.venussystem.venusmobile.domain.match.ResultadoMatch;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.ColecaoRepository;
import com.venussystem.venusmobile.repository.ListaItemRepository;
import com.venussystem.venusmobile.repository.MatchProdutoRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.repository.SincronizacaoListas;
import com.venussystem.venusmobile.view.adapter.AlternativaAdapter;
import com.venussystem.venusmobile.view.util.NotaProdutoUtil;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;

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
    private View carregandoMatch;
    private TextView textMatchVeredito;
    private TextView textMatchMensagem;
    private TextView textMatchNota;
    private TextView textMatchAviso;
    private LinearLayout listaMatchAlertas;
    private LinearLayout listaMatchPositivos;
    private RecyclerView listaAlternativas;
    private TextView textSemAlternativas;

    private View carregandoIngredientes;
    private TextView textIngredientes;
    private LinearLayout listaIngredientes;
    private ImageView imgProduto;
    private ImageButton btnSalvarNaLista;

    private ColecaoRepository colecaoRepository;
    private ListaItemRepository itemRepository;

    private Produto produto;
    private long produtoIdRecebido = -1L;

    private boolean ingredientesCarregados = false;
    private boolean alternativasMontadas = false;
    private boolean matchCarregado = false;
    private boolean telaInicializada = false;
    private boolean mensagemErroMostrada = false;

    // So vai para a API quando a tela fecha, juntando todos os toques do
    // menu de listas num envio so (como na DetalheListaActivity).
    private boolean listasAlteradas = false;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_detalhe_produto);

        prepararReferenciasDeTela();

        colecaoRepository =
                new ColecaoRepository(this);

        itemRepository =
                new ListaItemRepository(this);

        findViewById(
                R.id.btnVoltar
        ).setOnClickListener(
                v -> finish()
        );

        btnSalvarNaLista.setOnClickListener(
                this::mostrarListasParaSalvar
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

    @Override
    protected void onResume() {
        super.onResume();

        // O produto pode ter saido de uma lista enquanto esta tela estava
        // por baixo (ex.: removido na DetalheListaActivity).
        if (telaInicializada) {
            atualizarIconeDoMarcador();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (listasAlteradas) {
            listasAlteradas = false;
            new SincronizacaoListas(this)
                    .sincronizarEmSegundoPlano();
        }
    }

    private void prepararReferenciasDeTela() {
        imgProduto = findViewById(R.id.imgProduto);

        btnSalvarNaLista =
                findViewById(
                        R.id.btnSalvarNaLista
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

        carregandoMatch = findViewById(R.id.carregandoMatch);
        textMatchVeredito = findViewById(R.id.textMatchVeredito);
        textMatchMensagem = findViewById(R.id.textMatchMensagem);
        textMatchNota = findViewById(R.id.textMatchNota);
        textMatchAviso = findViewById(R.id.textMatchAviso);
        listaMatchAlertas = findViewById(R.id.listaMatchAlertas);
        listaMatchPositivos = findViewById(R.id.listaMatchPositivos);

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

        // Escondido ate aqui: antes de achar o produto nao ha o que salvar.
        btnSalvarNaLista.setVisibility(
                View.VISIBLE
        );
        atualizarIconeDoMarcador();
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

        // A foto é independente da leitura dos ingredientes. Resolve-a logo
        // pelo media_assets para não fazer a imagem esperar as consultas da
        // composição completa do produto.
        ProdutoImagemLoader.carregar(
                this, imgProduto, produto, R.drawable.bg_foto_produto);
    }

    private void carregarImagem(String url) {
        ImageLoader carregador =
                Coil.imageLoader(this);

        carregador.enqueue(
                new ImageRequest.Builder(this)
                        .data(ProdutoImagemLoader.urlParaExibicao(url, 640))
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

        if (aba == ABA_MATCH
                && !matchCarregado) {

            carregarMatch();
        }

        if (naAlternativas
                && !alternativasMontadas) {

            montarAlternativas();
        }
    }

    private void carregarMatch() {

        if (produto == null || produto.getId() == null) {
            return;
        }

        matchCarregado = true;

        carregandoMatch.setVisibility(View.VISIBLE);
        textMatchVeredito.setVisibility(View.GONE);
        textMatchMensagem.setVisibility(View.GONE);
        textMatchNota.setVisibility(View.GONE);
        textMatchAviso.setVisibility(View.GONE);
        listaMatchAlertas.removeAllViews();
        listaMatchPositivos.removeAllViews();

        new MatchProdutoRepository(new PerfilRepository(this)).calcular(
                produto.getId(),
                new MatchProdutoRepository.AoCalcular() {
                    @Override
                    public void aoConcluir(
                            @NonNull ResultadoMatch resultado,
                            ProductScoreResponse nota,
                            @NonNull List<String> consideradas
                    ) {
                        if (isFinishing() || isDestroyed()) return;
                        mostrarMatch(resultado, nota, consideradas);
                    }

                    @Override
                    public void aoFalhar() {
                        if (isFinishing() || isDestroyed()) return;
                        // Deixa tentar de novo ao reabrir a aba.
                        matchCarregado = false;
                        carregandoMatch.setVisibility(View.GONE);
                        mostrarMensagemMatch(R.string.produto_match_indisponivel);
                    }
                }
        );
    }

    private void mostrarMatch(
            ResultadoMatch resultado,
            ProductScoreResponse nota,
            List<String> consideradas
    ) {

        carregandoMatch.setVisibility(View.GONE);

        switch (resultado.veredito) {
            case SEM_PERFIL:
                mostrarMensagemMatch(R.string.produto_match_sem_perfil);
                // Quando a pessoa responder o questionario, a aba recalcula.
                matchCarregado = false;
                return;
            case NAO_RECOMENDADO:
                mostrarVeredito(R.string.produto_match_veredito_nao_recomendado);
                break;
            case ATENCAO:
                mostrarVeredito(R.string.produto_match_veredito_atencao);
                break;
            case COMBINA:
                mostrarVeredito(R.string.produto_match_veredito_combina);
                break;
            default:
                if (consideradas.isEmpty()) {
                    mostrarMensagemMatch(R.string.produto_match_sem_pontos);
                } else {
                    textMatchMensagem.setText(getString(
                            R.string.produto_match_sem_pontos_considerado,
                            String.join(", ", consideradas)
                    ));
                    textMatchMensagem.setVisibility(View.VISIBLE);
                }
                break;
        }

        adicionarAchados(
                listaMatchAlertas,
                R.string.produto_match_titulo_alertas,
                resultado.alertas
        );
        adicionarAchados(
                listaMatchPositivos,
                R.string.produto_match_titulo_positivos,
                resultado.positivos
        );

        if (nota != null && nota.overallScore != null) {
            textMatchNota.setText(
                    getString(R.string.produto_match_nota, nota.overallScore)
            );
            textMatchNota.setVisibility(View.VISIBLE);
        }
        textMatchAviso.setVisibility(View.VISIBLE);
    }

    private void mostrarVeredito(int texto) {
        textMatchVeredito.setText(texto);
        textMatchVeredito.setVisibility(View.VISIBLE);
    }

    private void mostrarMensagemMatch(int texto) {
        textMatchMensagem.setText(texto);
        textMatchMensagem.setVisibility(View.VISIBLE);
    }

    private void adicionarAchados(
            LinearLayout destino,
            int titulo,
            List<ResultadoMatch.Achado> achados
    ) {

        if (achados.isEmpty()) {
            return;
        }

        destino.addView(
                novoTexto(getString(titulo), 15, true, R.color.preto_texto, 24)
        );

        for (ResultadoMatch.Achado achado : achados) {

            destino.addView(
                    novoTexto(
                            achado.titulo,
                            14,
                            true,
                            achado.grave
                                    ? R.color.amarelo_aviso_texto
                                    : R.color.preto_texto,
                            12
                    )
            );

            if (!achado.descricao.isEmpty()) {
                destino.addView(
                        novoTexto(achado.descricao, 13, false,
                                R.color.cinza_descricao, 2)
                );
            }

            destino.addView(
                    novoTexto(
                            getString(
                                    R.string.produto_match_ingredientes,
                                    String.join(", ", achado.ingredientes)
                            ),
                            12, false, R.color.cinza_descricao, 2
                    )
            );
        }
    }

    private TextView novoTexto(
            String texto,
            int tamanhoSp,
            boolean negrito,
            int cor,
            int margemTopoDp
    ) {

        TextView view = new TextView(this);
        view.setText(texto);
        view.setTextSize(tamanhoSp);
        view.setTextColor(ContextCompat.getColor(this, cor));
        if (negrito) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Math.round(
                margemTopoDp * getResources().getDisplayMetrics().density
        );
        view.setLayoutParams(params);
        return view;
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

    /**
     * Menu preso ao marcador com todas as listas do usuario. As que ja tem
     * este produto aparecem marcadas; tocar numa lista salva o produto nela
     * (no topo, como o "Adicionar produto" da propria lista) ou tira, se ja
     * estava. A ultima opcao cria uma lista nova ja com o produto - e a unica
     * quando a pessoa ainda nao tem lista nenhuma.
     */
    private void mostrarListasParaSalvar(
            View ancora
    ) {

        if (produto == null) {
            return;
        }

        List<Colecao> encontradas =
                colecaoRepository
                        .minhasListas()
                        .getValue();

        List<Colecao> listas =
                encontradas != null
                        ? encontradas
                        : new ArrayList<>();

        PopupMenu menu =
                new PopupMenu(
                        this,
                        ancora,
                        Gravity.END
                );

        for (int i = 0; i < listas.size(); i++) {

            Colecao lista = listas.get(i);

            // O id do item e a posicao na lista, para achar a Colecao no clique.
            menu.getMenu()
                    .add(
                            Menu.NONE,
                            i,
                            i,
                            lista.getName()
                    )
                    .setCheckable(true)
                    .setChecked(
                            itemRepository.contem(
                                    lista.getId(),
                                    produto.getId()
                            )
                    );
        }

        // Depois de todas as listas: o id dela e o primeiro que sobra.
        int idNovaLista = listas.size();

        menu.getMenu()
                .add(
                        Menu.NONE,
                        idNovaLista,
                        idNovaLista,
                        R.string.produto_nova_lista
                );

        menu.setOnMenuItemClickListener(
                item -> {

                    if (item.getItemId() == idNovaLista) {

                        mostrarDialogoNovaLista();

                    } else {

                        alternarProdutoNaLista(
                                listas.get(
                                        item.getItemId()
                                )
                        );
                    }

                    return true;
                }
        );

        menu.show();
    }

    /**
     * Pede so o nome (como o "Editar nome" da lista) e ja salva o produto
     * na lista criada. Descricao e capa ficam para a tela da lista.
     */
    private void mostrarDialogoNovaLista() {

        View conteudo =
                getLayoutInflater()
                        .inflate(
                                R.layout.dialog_editar_texto,
                                null
                        );

        EditText campo =
                conteudo.findViewById(
                        R.id.editTexto
                );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.lista_nova)
                        .setView(conteudo)
                        .setPositiveButton(R.string.lista_salvar, null)
                        .setNegativeButton(R.string.lista_cancelar, null)
                        .create();

        // Botao positivo tratado a mao: nome vazio ou repetido mantem o
        // dialog aberto mostrando o erro (o listener do Builder sempre fecha).
        dialog.setOnShowListener(
                d -> dialog
                        .getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(
                                v -> {

                                    String nome =
                                            campo.getText()
                                                    .toString()
                                                    .trim();

                                    if (nome.isEmpty()) {
                                        campo.setError(
                                                getString(R.string.lista_erro_nome)
                                        );
                                        return;
                                    }

                                    if (colecaoRepository.nomeEmUso(nome, -1)) {
                                        campo.setError(
                                                getString(R.string.lista_erro_nome_repetido)
                                        );
                                        return;
                                    }

                                    Colecao criada =
                                            colecaoRepository.criar(
                                                    nome,
                                                    "",
                                                    null
                                            );

                                    alternarProdutoNaLista(
                                            criada
                                    );

                                    dialog.dismiss();
                                }
                        )
        );

        dialog.show();
    }

    private void alternarProdutoNaLista(
            @NonNull Colecao lista
    ) {

        long listaId = lista.getId();
        long produtoId = produto.getId();

        int mensagem;

        if (itemRepository.contem(
                listaId,
                produtoId
        )) {

            itemRepository.remover(
                    listaId,
                    produtoId
            );

            mensagem = R.string.produto_removido_da_lista;

        } else {

            itemRepository.adicionar(
                    listaId,
                    produtoId
            );

            mensagem = R.string.produto_salvo_na_lista;
        }

        listasAlteradas = true;
        atualizarIconeDoMarcador();

        Toast.makeText(
                this,
                getString(
                        mensagem,
                        lista.getName()
                ),
                Toast.LENGTH_SHORT
        ).show();
    }

    /** Marcador preenchido quando o produto esta em pelo menos uma lista. */
    private void atualizarIconeDoMarcador() {

        boolean salvo = false;

        List<Colecao> listas =
                colecaoRepository
                        .minhasListas()
                        .getValue();

        if (listas != null) {

            for (Colecao lista : listas) {

                if (itemRepository.contem(
                        lista.getId(),
                        produto.getId()
                )) {

                    salvo = true;
                    break;
                }
            }
        }

        btnSalvarNaLista.setImageResource(
                salvo
                        ? R.drawable.ic_marcador_preenchido
                        : R.drawable.ic_marcador
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
