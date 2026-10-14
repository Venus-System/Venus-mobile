package com.venussystem.venusmobile.view;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.view.adapter.ConversaAdapter;
import com.venussystem.venusmobile.view.util.NotaProdutoUtil;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;
import com.venussystem.venusmobile.viewmodel.AssistenteViewModel;

/**
 * Conversa com a assistente, que responde pela Venus-AI-api.
 *
 * A tela so desenha: a conversa e o envio moram no AssistenteViewModel. Aberta
 * pelo detalhe de um produto (paraProduto), mostra o produto no topo e as
 * perguntas vao falando dele; pelo botao da tela principal, e o chat geral.
 */
public class AssistenteActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUTO_ID = "assistente_produto_id";
    public static final String EXTRA_PRODUTO_NOME = "assistente_produto_nome";
    public static final String EXTRA_PRODUTO_MARCA = "assistente_produto_marca";
    public static final String EXTRA_PRODUTO_NOTA = "assistente_produto_nota";
    public static final String EXTRA_PRODUTO_IMAGEM = "assistente_produto_imagem";

    // Extra de int nao aceita null: nota fora de 0-100 quer dizer "sem nota".
    private static final int SEM_NOTA = -1;

    // Enquanto a Venus responde, o botao e as sugestoes ficam apagados assim.
    private static final float ALFA_DESABILITADO = 0.4f;

    private static final int MAXIMO_LINHAS_CAMPO = 4;

    private AssistenteViewModel viewModel;
    private ConversaAdapter adapter;
    private RecyclerView lista;
    private EditText campo;
    private View botaoEnviar;
    private View botaoNovaConversa;
    private LinearLayout linhaSugestoes;

    // A primeira lista pode ter a conversa salva inteira: pula direto para o
    // fim em vez de rolar mensagem por mensagem.
    private boolean jaRolou = false;

    /** Abre o chat falando de um produto. */
    public static Intent paraProduto(Context context, @Nullable Produto produto) {
        Intent intent = new Intent(context, AssistenteActivity.class);
        if (produto == null) {
            return intent;
        }
        Long id = produto.getId();
        Integer nota = produto.getOverallScore();
        intent.putExtra(EXTRA_PRODUTO_ID, id == null ? -1L : id);
        intent.putExtra(EXTRA_PRODUTO_NOME, produto.getName());
        intent.putExtra(EXTRA_PRODUTO_MARCA, produto.getBrandName());
        intent.putExtra(EXTRA_PRODUTO_NOTA, nota == null ? SEM_NOTA : nota);
        intent.putExtra(EXTRA_PRODUTO_IMAGEM, produto.getImageUrl());
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_assistente);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());

            // O teclado cobre o campo de digitar: quando ele esta aberto, a
            // margem de baixo passa a ser a altura dele, e nao a da barra.
            v.setPadding(barras.left, barras.top, barras.right,
                    Math.max(barras.bottom, teclado.bottom));
            return insets;
        });

        campo = findViewById(R.id.campoMensagem);
        lista = findViewById(R.id.listaMensagens);
        botaoEnviar = findViewById(R.id.btnEnviar);
        botaoNovaConversa = findViewById(R.id.btnNovaConversa);
        linhaSugestoes = findViewById(R.id.linhaSugestoes);

        // Pergunta longa quebra a linha, mas o campo continua de uma linha so
        // para o teclado: um campo multilinha troca o "enviar" por "nova linha".
        campo.setHorizontallyScrolling(false);
        campo.setMaxLines(MAXIMO_LINHAS_CAMPO);

        adapter = new ConversaAdapter();
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        botaoEnviar.setOnClickListener(v -> enviar());
        botaoNovaConversa.setOnClickListener(v -> confirmarNovaConversa());

        campo.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEND) {
                enviar();
                return true;
            }
            return false;
        });

        Produto produto = produtoDo(getIntent());

        viewModel = new ViewModelProvider(this).get(AssistenteViewModel.class);
        if (produto != null) {
            viewModel.definirProduto(produto.getName(), produto.getBrandName());
        }

        mostrarProduto(produto);
        montarSugestoes(produto != null);

        viewModel.getMensagens().observe(this, mensagens -> {
            adapter.definir(mensagens);
            if (mensagens.isEmpty()) {
                return;
            }
            if (jaRolou) {
                lista.smoothScrollToPosition(mensagens.size() - 1);
            } else {
                lista.scrollToPosition(mensagens.size() - 1);
                jaRolou = true;
            }
        });
        viewModel.getAguardando().observe(this, this::mostrarAguardando);
        viewModel.getPerguntaQueFalhou().observe(this, this::devolverAoCampo);
    }

    @Nullable
    private static Produto produtoDo(Intent intent) {
        String nome = intent.getStringExtra(EXTRA_PRODUTO_NOME);
        if (nome == null || nome.trim().isEmpty()) {
            return null;
        }
        long id = intent.getLongExtra(EXTRA_PRODUTO_ID, -1L);
        int nota = intent.getIntExtra(EXTRA_PRODUTO_NOTA, SEM_NOTA);
        return new Produto(
                id > 0 ? id : null,
                nome,
                intent.getStringExtra(EXTRA_PRODUTO_MARCA),
                nota < 0 ? null : nota,
                intent.getStringExtra(EXTRA_PRODUTO_IMAGEM),
                null,
                null);
    }

    private void mostrarProduto(@Nullable Produto produto) {
        View card = findViewById(R.id.cardProduto);
        if (produto == null) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);

        TextView nome = findViewById(R.id.textNomeProduto);
        nome.setText(produto.getName());

        TextView marca = findViewById(R.id.textMarcaProduto);
        String textoMarca = produto.getBrandName();
        boolean temMarca = textoMarca != null && !textoMarca.trim().isEmpty();
        marca.setText(temMarca ? textoMarca : "");
        marca.setVisibility(temMarca ? View.VISIBLE : View.GONE);

        // Mesmo selo das outras telas (ver AlternativaAdapter).
        TextView nota = findViewById(R.id.textNotaProduto);
        Integer valor = produto.getOverallScore();
        nota.setText(valor == null ? getString(R.string.nota_indisponivel) : String.valueOf(valor));
        nota.setTextColor(ContextCompat.getColor(this, NotaProdutoUtil.corPara(valor)));
        nota.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, NotaProdutoUtil.fundoPara(valor))));

        ImageView imagem = findViewById(R.id.imgProduto);
        ProdutoImagemLoader.carregar(this, imagem, produto, R.drawable.bg_foto_historico);
    }

    private void montarSugestoes(boolean doProduto) {
        int[] sugestoes = doProduto
                ? new int[]{
                        R.string.assistente_sugestao_produto_um,
                        R.string.assistente_sugestao_produto_dois,
                        R.string.assistente_sugestao_produto_tres,
                }
                : new int[]{
                        R.string.assistente_sugestao_geral_um,
                        R.string.assistente_sugestao_geral_dois,
                        R.string.assistente_sugestao_geral_tres,
                };

        for (int sugestao : sugestoes) {
            TextView chip = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_chip_sugestao, linhaSugestoes, false);
            chip.setText(sugestao);
            chip.setOnClickListener(v -> viewModel.perguntar(chip.getText().toString()));
            linhaSugestoes.addView(chip);
        }
    }

    private void enviar() {
        // So limpa o campo se a pergunta foi aceita: enquanto a Venus responde a
        // anterior, o texto digitado fica esperando ali.
        if (viewModel.perguntar(campo.getText().toString())) {
            campo.setText("");
        }
    }

    /**
     * A pergunta sem resposta volta para o campo, e tentar de novo e so tocar
     * em enviar. Se a pessoa ja comecou a escrever outra, nada e trocado.
     */
    private void devolverAoCampo(@Nullable String pergunta) {
        if (pergunta == null) {
            return;
        }
        if (campo.getText().toString().trim().isEmpty()) {
            campo.setText(pergunta);
            campo.setSelection(campo.getText().length());
        }
        viewModel.esquecerPerguntaQueFalhou();
    }

    private void confirmarNovaConversa() {
        if (Boolean.TRUE.equals(viewModel.getAguardando().getValue())) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.assistente_nova_conversa_titulo)
                .setMessage(R.string.assistente_nova_conversa_texto)
                .setNegativeButton(R.string.lista_cancelar, null)
                .setPositiveButton(R.string.assistente_nova_conversa_confirmar,
                        (dialogo, botao) -> viewModel.novaConversa())
                .show();
    }

    private void mostrarAguardando(@NonNull Boolean aguardando) {
        float alfa = aguardando ? ALFA_DESABILITADO : 1f;

        botaoEnviar.setEnabled(!aguardando);
        botaoEnviar.setAlpha(alfa);
        botaoNovaConversa.setEnabled(!aguardando);
        botaoNovaConversa.setAlpha(alfa);
        for (int i = 0; i < linhaSugestoes.getChildCount(); i++) {
            View chip = linhaSugestoes.getChildAt(i);
            chip.setEnabled(!aguardando);
            chip.setAlpha(alfa);
        }
    }
}
