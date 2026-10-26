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
import android.widget.ProgressBar;
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

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;
import com.venussystem.venusmobile.model.IngredienteProduto;
import com.venussystem.venusmobile.domain.match.ContaDaNota;
import com.venussystem.venusmobile.domain.match.EtiquetasDoPerfil;
import com.venussystem.venusmobile.domain.match.PontosDaClassificacao;
import com.venussystem.venusmobile.domain.match.ResultadoMatch;
import com.venussystem.venusmobile.domain.perfil.ProgressoPerfil;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.Usuario;
import com.venussystem.venusmobile.repository.api.dto.ClassificationResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.AcessosRecentesRepository;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.repository.ClassificacaoRepository;
import com.venussystem.venusmobile.repository.ColecaoRepository;
import com.venussystem.venusmobile.repository.CurtidosRepository;
import com.venussystem.venusmobile.repository.HistoricoCatalogoRepository;
import com.venussystem.venusmobile.repository.ListaItemRepository;
import com.venussystem.venusmobile.repository.MatchProdutoRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.repository.SincronizacaoCurtidos;
import com.venussystem.venusmobile.repository.SincronizacaoListas;
import com.venussystem.venusmobile.view.adapter.AlternativaAdapter;
import com.venussystem.venusmobile.view.componente.CartaoPerfilView;
import com.venussystem.venusmobile.view.util.MensagemRapida;
import com.venussystem.venusmobile.view.util.NotaProdutoUtil;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;
import com.venussystem.venusmobile.view.util.TextosMatch;

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
    private static final int MAXIMO_CHIPS_MATCH = 6;

    private final ProdutoRepository repository =
            new ProdutoRepository();

    private View conteudoAvaliacao;
    private View conteudoMatch;
    private View carregandoMatch;
    private TextView textMatchMensagem;
    private CartaoPerfilView cartaoPerfilMatch;
    private View cartaoMatch;
    private TextView textMatchTitulo;
    private TextView textMatchCruzado;
    private TextView textMatchVeredito;
    private View anelNotaMatch;
    private CircularProgressIndicator progressoNotaMatch;
    private ProgressBar carregandoNotaMatch;
    private TextView textMatchNota;
    private TextView textMatchNotaRotulo;
    private TextView textMatchConta;
    private LinearLayout listaMatchPontos;
    private TextView textMatchSemEfeitos;
    private View secaoMatchDestaques;
    private ChipGroup grupoMatchDestaques;
    private View cartaoMatchAlergias;
    private TextView textMatchAlergiaTitulo;
    private TextView textMatchAlergiaTexto;
    private TextView textMatchAviso;
    private RecyclerView listaAlternativas;
    private TextView textSemAlternativas;

    private View carregandoIngredientes;
    private TextView textIngredientes;
    private LinearLayout listaIngredientes;
    private ImageView imgProduto;
    private ImageButton btnSalvarNaLista;
    private ImageButton btnCurtir;
    private ImageButton btnPerguntarVenus;

    private ColecaoRepository colecaoRepository;
    private ListaItemRepository itemRepository;
    private CurtidosRepository curtidosRepository;
    private HistoricoCatalogoRepository historicoCatalogoRepository;

    private Produto produto;
    private long produtoIdRecebido = -1L;

    private boolean ingredientesCarregados = false;
    private boolean alternativasMontadas = false;
    private boolean matchCarregado = false;

    // A aba junta duas respostas que chegam em qualquer ordem: o cruzamento feito
    // no aparelho e a nota pessoal da Venus-Classificacao. O contador descarta a
    // resposta de um pedido antigo quando a aba e aberta de novo.
    private int pedidoMatch = 0;
    @Nullable
    private ResultadoMatch resultadoMatch;
    @Nullable
    private ProductScoreResponse notaGeralMatch;
    @Nullable
    private ClassificationResponse classificacaoMatch;
    private boolean classificacaoRespondeu = false;
    /** O code do erro da Classificacao, para explicar por que o anel ficou na nota geral. */
    @Nullable
    private String erroClassificacao;
    private boolean telaInicializada = false;
    private boolean mensagemErroMostrada = false;

    // So vai para a API quando a tela fecha, juntando todos os toques do
    // menu de listas num envio so (como na DetalheListaActivity).
    private boolean listasAlteradas = false;

    // O mesmo para o coracao: os toques vao para /api/favorites ao fechar.
    private boolean curtidosAlterados = false;

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

        curtidosRepository =
                new CurtidosRepository(this);

        historicoCatalogoRepository =
                new HistoricoCatalogoRepository(this);

        findViewById(
                R.id.btnVoltar
        ).setOnClickListener(
                v -> finish()
        );

        btnSalvarNaLista.setOnClickListener(
                this::mostrarListasParaSalvar
        );

        btnCurtir.setOnClickListener(
                v -> alternarCurtida()
        );

        btnPerguntarVenus.setOnClickListener(
                v -> startActivity(
                        AssistenteActivity.paraProduto(
                                this,
                                produto
                        )
                )
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

        // O produto pode ter saido de uma lista (ou dos curtidos) enquanto
        // esta tela estava por baixo (ex.: removido na DetalheListaActivity).
        if (telaInicializada) {
            atualizarIconeDoMarcador();
            atualizarIconeDoCoracao();
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

        if (curtidosAlterados) {
            curtidosAlterados = false;
            new SincronizacaoCurtidos(this)
                    .sincronizarEmSegundoPlano();
        }
    }

    private void prepararReferenciasDeTela() {
        imgProduto = findViewById(R.id.imgProduto);

        btnSalvarNaLista =
                findViewById(
                        R.id.btnSalvarNaLista
                );

        btnCurtir =
                findViewById(
                        R.id.btnCurtir
                );

        btnPerguntarVenus =
                findViewById(
                        R.id.btnPerguntarVenus
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
        textMatchMensagem = findViewById(R.id.textMatchMensagem);
        cartaoPerfilMatch = findViewById(R.id.cartaoPerfilMatch);
        cartaoMatch = findViewById(R.id.cartaoMatch);
        textMatchTitulo = findViewById(R.id.textMatchTitulo);
        textMatchCruzado = findViewById(R.id.textMatchCruzado);
        textMatchVeredito = findViewById(R.id.textMatchVeredito);
        anelNotaMatch = findViewById(R.id.anelNotaMatch);
        progressoNotaMatch = findViewById(R.id.progressoNotaMatch);
        carregandoNotaMatch = findViewById(R.id.carregandoNotaMatch);
        textMatchNota = findViewById(R.id.textMatchNota);
        textMatchNotaRotulo = findViewById(R.id.textMatchNotaRotulo);
        textMatchConta = findViewById(R.id.textMatchConta);
        listaMatchPontos = findViewById(R.id.listaMatchPontos);
        textMatchSemEfeitos = findViewById(R.id.textMatchSemEfeitos);
        secaoMatchDestaques = findViewById(R.id.secaoMatchDestaques);
        grupoMatchDestaques = findViewById(R.id.grupoMatchDestaques);
        cartaoMatchAlergias = findViewById(R.id.cartaoMatchAlergias);
        textMatchAlergiaTitulo = findViewById(R.id.textMatchAlergiaTitulo);
        textMatchAlergiaTexto = findViewById(R.id.textMatchAlergiaTexto);
        textMatchAviso = findViewById(R.id.textMatchAviso);
        findViewById(R.id.botaoMatchAlternativas).setOnClickListener(v -> abrirAlternativas());

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
        historicoCatalogoRepository.registrarAcesso(produtoEncontrado);

        registrarAcesso(
                produto.getId()
        );

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

        // Escondidos ate aqui: antes de achar o produto nao ha o que salvar
        // nem sobre o que perguntar.
        btnSalvarNaLista.setVisibility(
                View.VISIBLE
        );
        btnCurtir.setVisibility(
                View.VISIBLE
        );
        btnPerguntarVenus.setVisibility(
                View.VISIBLE
        );
        atualizarIconeDoMarcador();
        atualizarIconeDoCoracao();
    }

    /**
     * Anota o produto no historico da busca. Falhar aqui nao pode atrapalhar
     * a tela: o historico e so um atalho.
     */
    private void registrarAcesso(
            long produtoId
    ) {

        try {

            new AcessosRecentesRepository(this)
                    .registrar(produtoId);

        } catch (RuntimeException e) {

            Log.w(
                    TAG,
                    "Não foi possível registrar o acesso ao produto.",
                    e
            );
        }
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
        int pedido = ++pedidoMatch;
        resultadoMatch = null;
        notaGeralMatch = null;
        classificacaoMatch = null;
        classificacaoRespondeu = false;
        erroClassificacao = null;

        carregandoMatch.setVisibility(View.VISIBLE);
        textMatchMensagem.setVisibility(View.GONE);
        cartaoPerfilMatch.setVisibility(View.GONE);
        cartaoMatch.setVisibility(View.GONE);
        cartaoMatchAlergias.setVisibility(View.GONE);
        textMatchAviso.setVisibility(View.GONE);

        new MatchProdutoRepository(new PerfilRepository(this)).calcular(
                produto.getId(),
                new MatchProdutoRepository.AoCalcular() {
                    @Override
                    public void aoConcluir(
                            @NonNull ResultadoMatch resultado,
                            ProductScoreResponse nota,
                            @NonNull List<String> consideradas
                    ) {
                        if (isFinishing() || isDestroyed() || pedido != pedidoMatch) return;
                        mostrarMatch(resultado, nota, consideradas);
                    }

                    @Override
                    public void aoFalhar() {
                        if (isFinishing() || isDestroyed() || pedido != pedidoMatch) return;
                        // Deixa tentar de novo ao reabrir a aba.
                        matchCarregado = false;
                        carregandoMatch.setVisibility(View.GONE);
                        mostrarMensagemMatch(R.string.produto_match_indisponivel);
                    }
                }
        );

        // A nota pessoal vem de outro servidor (Render, que pode estar dormindo):
        // corre em paralelo e so troca o anel e o selo quando chega.
        new ClassificacaoRepository(this).classificar(
                produto.getId(),
                new ClassificacaoRepository.AoClassificar() {
                    @Override
                    public void aoConcluir(@NonNull ClassificationResponse classificacao) {
                        if (isFinishing() || isDestroyed() || pedido != pedidoMatch) return;
                        classificacaoMatch = classificacao;
                        classificacaoRespondeu = true;
                        atualizarNotaESelo();
                        // A alergia que ela achou pelo cadastro entra no cartao.
                        if (resultadoMatch != null) mostrarAlergias();
                    }

                    @Override
                    public void aoFalhar(@Nullable String codigo) {
                        if (isFinishing() || isDestroyed() || pedido != pedidoMatch) return;
                        classificacaoRespondeu = true;
                        erroClassificacao = codigo;
                        atualizarNotaESelo();
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

        if (resultado.veredito == ResultadoMatch.Veredito.SEM_PERFIL) {
            ProgressoPerfil progresso = new PerfilRepository(this).progresso();
            if (progresso.estado == ProgressoPerfil.Estado.COMPLETO) {
                mostrarMensagemMatch(R.string.produto_match_sem_perfil);
            } else {
                // O cartao chama para o questionario, com o botao que o texto nao tinha.
                cartaoPerfilMatch.atualizar(progresso);
            }
            // Quando a pessoa responder o questionario, a aba recalcula.
            matchCarregado = false;
            return;
        }

        resultadoMatch = resultado;
        notaGeralMatch = nota;

        String nome = primeiroNome();
        textMatchTitulo.setText(nome.isEmpty()
                ? getString(R.string.produto_match_para_voce_sem_nome)
                : getString(R.string.produto_match_para_voce, nome));
        textMatchCruzado.setText(consideradas.isEmpty()
                ? getString(R.string.produto_match_perfil_sem_etiquetas)
                : getString(R.string.produto_match_cruzado, String.join(", ", consideradas)));
        // Tambem desenha os pontos: com a nota pessoal, eles ganham os motivos dela.
        atualizarNotaESelo();
        textMatchSemEfeitos.setVisibility(resultado.catalogoCobre ? View.GONE : View.VISIBLE);
        mostrarDestaques(resultado.destaques);
        cartaoMatch.setVisibility(View.VISIBLE);

        mostrarAlergias();
        textMatchAviso.setVisibility(View.VISIBLE);
    }

    /**
     * Anel e selo do cabecalho. Com a Venus-Classificacao: a nota pessoal e a
     * faixa dela. Enquanto ela nao responde, o anel fica carregando; se falhar,
     * volta para a nota geral do produto e o veredito do calculo no aparelho.
     */
    private void atualizarNotaESelo() {
        if (resultadoMatch == null) return;

        ClassificationResponse pessoal = classificacaoMatch;
        FaixaPessoal faixa = pessoal == null ? null : FaixaPessoal.de(pessoal.recommendationLevel);
        if (pessoal != null && pessoal.finalScore != null && faixa != null) {
            mostrarConta(pessoal);
            mostrarPontos(PontosDaClassificacao.mesclar(resultadoMatch.pontos, motivosDe(pessoal)));
            mostrarSelo(faixa.texto, faixa.corTexto, faixa.fundo);
            mostrarAnel(pessoal.finalScore, faixa.corAnel, faixa.fundo, faixa.corTexto,
                    R.string.produto_match_rotulo_para_voce,
                    getString(R.string.produto_match_nota_pessoal_descricao, pessoal.finalScore));
            return;
        }

        textMatchConta.setVisibility(View.GONE);
        mostrarPontos(resultadoMatch.pontos);
        mostrarSeloLocal(resultadoMatch.veredito);
        if (!classificacaoRespondeu) {
            carregandoNotaMatch.setVisibility(View.VISIBLE);
            textMatchNota.setVisibility(View.GONE);
            textMatchNotaRotulo.setText(R.string.produto_match_rotulo_para_voce);
            preencherAnel(0, R.color.cinza_fechar, R.color.cinza_fechar);
            anelNotaMatch.setContentDescription(
                    getString(R.string.produto_match_calculando_descricao));
            anelNotaMatch.setVisibility(View.VISIBLE);
            return;
        }

        mostrarErroDaNota();
        Integer geral = notaGeralMatch == null ? null : notaGeralMatch.overallScore;
        if (geral == null) {
            anelNotaMatch.setVisibility(View.GONE);
            return;
        }
        mostrarAnel(geral, NotaProdutoUtil.corPara(geral), NotaProdutoUtil.fundoPara(geral),
                NotaProdutoUtil.textoPara(geral), R.string.produto_match_rotulo_nota_geral,
                getString(R.string.produto_match_nota_descricao, geral));
    }

    /**
     * "A nota soma duas partes: 25,9 de 35 ... e 15,3 de 65 ...": de onde vem a
     * nota pessoal, com os pontos da Classificacao. Sem eles (analise salva que
     * nao foi calculada neste aparelho), a linha nao aparece.
     */
    private void mostrarConta(ClassificationResponse pessoal) {
        ClassificationResponse.Breakdown partes = pessoal.breakdown;
        ContaDaNota conta = partes == null
                ? ContaDaNota.de(null, null, null, pessoal.finalScore)
                : ContaDaNota.de(partes.qualityScore, partes.qualityPoints, partes.profilePoints,
                        pessoal.finalScore);
        String texto;
        if (conta.pontosQualidade != null && conta.pontosPerfil != null) {
            texto = getString(conta.limitada
                            ? R.string.produto_match_conta_limitada
                            : R.string.produto_match_conta,
                    TextosMatch.pontos(conta.pontosQualidade),
                    TextosMatch.pontos(conta.pontosPerfil));
        } else if (conta.pontosPerfil == null && conta.qualidade != null) {
            texto = getString(conta.limitada
                            ? R.string.produto_match_conta_so_qualidade_limitada
                            : R.string.produto_match_conta_so_qualidade,
                    conta.qualidade);
        } else {
            textMatchConta.setVisibility(View.GONE);
            return;
        }
        textMatchConta.setText(texto);
        textMatchConta.setVisibility(View.VISIBLE);
    }

    /** Na mesma linha da conta, por que a nota pessoal nao veio (produto em revisao...). */
    private void mostrarErroDaNota() {
        Integer texto = TextosMatch.erroDaNota(erroClassificacao);
        if (texto == null) {
            textMatchConta.setVisibility(View.GONE);
            return;
        }
        textMatchConta.setText(texto);
        textMatchConta.setVisibility(View.VISIBLE);
    }

    /** Motivos de regra de ingrediente da Classificacao, sem o texto do banco. */
    private static List<PontosDaClassificacao.Motivo> motivosDe(ClassificationResponse c) {
        List<PontosDaClassificacao.Motivo> motivos = new ArrayList<>();
        if (c.reasons == null) return motivos;
        for (ClassificationResponse.Reason r : c.reasons) {
            if (r == null || !"INGREDIENT_RULE".equals(r.source) || r.profileTagSlug == null) {
                continue;
            }
            motivos.add(new PontosDaClassificacao.Motivo(r.profileTagSlug, r.ingredientName,
                    Boolean.TRUE.equals(r.warning)));
        }
        return motivos;
    }

    private void mostrarSeloLocal(ResultadoMatch.Veredito veredito) {
        switch (veredito) {
            case NAO_RECOMENDADO:
                mostrarSelo(R.string.produto_match_veredito_nao_recomendado,
                        R.color.nota_ruim_texto, R.color.nota_ruim_fundo);
                break;
            case ATENCAO:
                mostrarSelo(R.string.produto_match_veredito_atencao,
                        R.color.nota_media_texto, R.color.nota_media_fundo);
                break;
            case COMBINA:
                mostrarSelo(R.string.produto_match_veredito_combina,
                        R.color.nota_boa_texto, R.color.nota_boa_fundo);
                break;
            default:
                mostrarSelo(R.string.produto_match_veredito_sem_alertas,
                        R.color.nota_boa_texto, R.color.nota_boa_fundo);
                break;
        }
    }

    private void mostrarSelo(int texto, int corTexto, int fundo) {
        textMatchVeredito.setText(texto);
        textMatchVeredito.setTextColor(ContextCompat.getColor(this, corTexto));
        textMatchVeredito.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, fundo)));
    }

    private void mostrarAnel(int valor, int corAnel, int trilho, int corTexto, int rotulo,
                             String descricao) {
        carregandoNotaMatch.setVisibility(View.GONE);
        textMatchNota.setText(String.valueOf(valor));
        textMatchNota.setTextColor(ContextCompat.getColor(this, corTexto));
        textMatchNota.setVisibility(View.VISIBLE);
        textMatchNotaRotulo.setText(rotulo);
        preencherAnel(valor, corAnel, trilho);
        anelNotaMatch.setContentDescription(descricao);
        anelNotaMatch.setVisibility(View.VISIBLE);
    }

    /** Enche so a parte da volta que corresponde a nota (60 enche 60%), na cor da faixa. */
    private void preencherAnel(int valor, int cor, int trilho) {
        progressoNotaMatch.setIndicatorColor(ContextCompat.getColor(this, cor));
        progressoNotaMatch.setTrackColor(ContextCompat.getColor(this, trilho));
        progressoNotaMatch.setProgressCompat(Math.max(0, Math.min(100, valor)), true);
    }

    /** Faixa da nota pessoal (recommendationLevel da Classificacao) em texto e cor. */
    private static final class FaixaPessoal {
        final int texto;
        final int corAnel;
        final int corTexto;
        final int fundo;

        private FaixaPessoal(int texto, int corAnel, int corTexto, int fundo) {
            this.texto = texto;
            this.corAnel = corAnel;
            this.corTexto = corTexto;
            this.fundo = fundo;
        }

        @Nullable
        static FaixaPessoal de(@Nullable String nivel) {
            if (nivel == null) return null;
            switch (nivel) {
                case "IDEAL":
                    return boa(R.string.produto_match_faixa_ideal);
                case "RECOMMENDED":
                    return boa(R.string.produto_match_faixa_recomendado);
                case "ACCEPTABLE":
                    return new FaixaPessoal(R.string.produto_match_faixa_aceitavel,
                            R.color.nota_media, R.color.nota_media_texto, R.color.nota_media_fundo);
                case "NOT_RECOMMENDED":
                    return ruim(R.string.produto_match_veredito_nao_recomendado);
                case "CONTRAINDICATED":
                    return ruim(R.string.produto_match_faixa_contraindicado);
                default:
                    return null;
            }
        }

        private static FaixaPessoal boa(int texto) {
            return new FaixaPessoal(texto, R.color.nota_boa, R.color.nota_boa_texto,
                    R.color.nota_boa_fundo);
        }

        private static FaixaPessoal ruim(int texto) {
            return new FaixaPessoal(texto, R.color.nota_ruim, R.color.nota_ruim_texto,
                    R.color.nota_ruim_fundo);
        }
    }

    private void mostrarPontos(List<ResultadoMatch.Ponto> pontos) {
        listaMatchPontos.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        TextosMatch.Recursos recursos = this::getString;

        for (ResultadoMatch.Ponto ponto : pontos) {
            View item = inflater.inflate(R.layout.item_ponto_match, listaMatchPontos, false);
            TextView titulo = item.findViewById(R.id.textPontoTitulo);
            TextView linha = item.findViewById(R.id.textPontoLinha);

            titulo.setText(TextosMatch.titulo(recursos, ponto));
            String texto = TextosMatch.linha(recursos, ponto);
            linha.setText(texto);
            linha.setVisibility(texto.isEmpty() ? View.GONE : View.VISIBLE);

            int bolinha;
            int cor;
            switch (ponto.tipo) {
                case GRAVE:
                    bolinha = R.drawable.bg_ponto_grave;
                    cor = R.color.nota_ruim_texto;
                    break;
                case ALERTA:
                    bolinha = R.drawable.bg_ponto_alerta;
                    cor = R.color.nota_media_texto;
                    break;
                default:
                    bolinha = R.drawable.bg_ponto_positivo;
                    cor = R.color.nota_boa_texto;
                    break;
            }
            item.findViewById(R.id.pontoMatch).setBackgroundResource(bolinha);
            titulo.setTextColor(ContextCompat.getColor(this, cor));
            listaMatchPontos.addView(item);
        }
        listaMatchPontos.setVisibility(pontos.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Destaques gerais so como chips curtos: a descricao do catalogo era texto demais. */
    private void mostrarDestaques(List<ResultadoMatch.Destaque> destaques) {
        grupoMatchDestaques.removeAllViews();
        List<String> rotulos = TextosMatch.chips(this::getString, destaques, MAXIMO_CHIPS_MATCH);

        for (String rotulo : rotulos) {
            TextView chip = new TextView(this);
            chip.setText(rotulo);
            chip.setTextSize(12);
            chip.setTextColor(ContextCompat.getColor(this, R.color.roxo_avatar_borda));
            chip.setBackgroundResource(R.drawable.bg_chip_match);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            grupoMatchDestaques.addView(chip);
        }
        secaoMatchDestaques.setVisibility(rotulos.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** As achadas no aparelho mais as que a Classificacao mandou (ou que o POST deixou guardadas). */
    private void mostrarAlergias() {
        List<String> encontradas = PontosDaClassificacao.alergias(
                resultadoMatch.alergiasEncontradas,
                classificacaoMatch == null ? null : classificacaoMatch.ingredientesDeAlergia());
        List<String> alergias = EtiquetasDoPerfil.alergiasEmTexto(new PerfilRepository(this));
        TextosMatch.Recursos recursos = this::getString;

        if (!encontradas.isEmpty()) {
            textMatchAlergiaTitulo.setText(R.string.produto_match_contem_alergeno);
            textMatchAlergiaTitulo.setTextColor(
                    ContextCompat.getColor(this, R.color.nota_ruim_texto));
            textMatchAlergiaTexto.setText(getString(R.string.match_linha_encontrado,
                    TextosMatch.ingredientes(recursos, encontradas)));
        } else if (!alergias.isEmpty()) {
            textMatchAlergiaTitulo.setText(R.string.produto_match_sem_alergenos);
            textMatchAlergiaTitulo.setTextColor(
                    ContextCompat.getColor(this, R.color.roxo_avatar_borda));
            textMatchAlergiaTexto.setText(R.string.produto_match_sem_alergenos_texto);
        } else {
            textMatchAlergiaTitulo.setText(R.string.produto_match_sem_alergias_marcadas);
            textMatchAlergiaTitulo.setTextColor(
                    ContextCompat.getColor(this, R.color.roxo_avatar_borda));
            textMatchAlergiaTexto.setText(R.string.produto_match_marque_alergias);
        }
        cartaoMatchAlergias.setVisibility(View.VISIBLE);
    }

    private void abrirAlternativas() {
        TabLayout abas = findViewById(R.id.abasProduto);
        TabLayout.Tab aba = abas.getTabAt(ABA_ALTERNATIVAS);
        if (aba != null) {
            aba.select();
        }
    }

    private String primeiroNome() {
        Usuario usuario = new AutenticacaoRepository().usuarioLogado();
        String nome = usuario == null ? null : usuario.getNome();
        if (nome == null || nome.trim().isEmpty()) {
            return "";
        }
        return nome.trim().split("\\s+")[0];
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private void mostrarMensagemMatch(int texto) {
        textMatchMensagem.setText(texto);
        textMatchMensagem.setVisibility(View.VISIBLE);
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

        View raiz = findViewById(R.id.main);

        if (itemRepository.contem(
                listaId,
                produtoId
        )) {

            itemRepository.remover(
                    listaId,
                    produtoId
            );

            MensagemRapida.neutra(
                    raiz,
                    getString(
                            R.string.produto_removido_da_lista,
                            lista.getName()
                    )
            );

        } else {

            itemRepository.adicionar(
                    listaId,
                    produtoId
            );

            // Salvar ganha a mensagem verde; tirar fica no cinza.
            MensagemRapida.sucesso(
                    raiz,
                    getString(
                            R.string.produto_salvo_na_lista,
                            lista.getName()
                    )
            );
        }

        listasAlteradas = true;
        atualizarIconeDoMarcador();
    }

    /**
     * O coracao: curtir manda o produto para a lista fixa "Produtos curtidos"
     * (com a mensagem verde); tocar de novo tira, com a mensagem cinza.
     */
    private void alternarCurtida() {

        if (produto == null) {
            return;
        }

        long produtoId = produto.getId();
        View raiz = findViewById(R.id.main);

        if (curtidosRepository.contem(produtoId)) {

            curtidosRepository.descurtir(produtoId);
            MensagemRapida.neutra(
                    raiz,
                    getString(R.string.produto_descurtido)
            );

        } else {

            curtidosRepository.curtir(produtoId);
            MensagemRapida.sucesso(
                    raiz,
                    getString(R.string.produto_curtido)
            );
        }

        curtidosAlterados = true;
        atualizarIconeDoCoracao();
    }

    /** Coracao cheio quando o produto esta nos curtidos. */
    private void atualizarIconeDoCoracao() {

        boolean curtido =
                curtidosRepository.contem(
                        produto.getId()
                );

        btnCurtir.setImageResource(
                curtido
                        ? R.drawable.ic_coracao_preenchido
                        : R.drawable.ic_coracao
        );

        btnCurtir.setContentDescription(
                getString(
                        curtido
                                ? R.string.desc_descurtir
                                : R.string.desc_curtir
                )
        );
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
