package com.venussystem.venusmobile.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.avaliacao.RegrasDaAvaliacao;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.AvaliacoesRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;

public class EscreverAvaliacaoActivity extends AppCompatActivity {

    private static final String EXTRA_PRODUTO_ID = "produto_id";
    private static final String EXTRA_NOME = "nome";
    private static final String EXTRA_MARCA = "marca";

    private RatingBar estrelas;
    private TextView textDicaNota;
    private EditText editTitulo;
    private EditText editComentario;
    private TextView textContador;
    private CheckBox checkUsou;
    private TextView textErroEnvio;
    private Button btnPublicar;

    private AvaliacoesRepository repositorio;
    private long produtoId;
    private boolean enviando = false;

    public static Intent intent(Context contexto, long produtoId, @Nullable String nome,
                                @Nullable String marca) {
        return new Intent(contexto, EscreverAvaliacaoActivity.class)
                .putExtra(EXTRA_PRODUTO_ID, produtoId)
                .putExtra(EXTRA_NOME, nome)
                .putExtra(EXTRA_MARCA, marca);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, R.layout.activity_escrever_avaliacao);

        produtoId = getIntent().getLongExtra(EXTRA_PRODUTO_ID, -1L);
        if (produtoId <= 0) {
            finish();
            return;
        }
        repositorio = new AvaliacoesRepository(this);

        estrelas = findViewById(R.id.estrelasNota);
        textDicaNota = findViewById(R.id.textDicaNota);
        editTitulo = findViewById(R.id.editTituloAvaliacao);
        editComentario = findViewById(R.id.editComentarioAvaliacao);
        textContador = findViewById(R.id.textContadorComentario);
        checkUsou = findViewById(R.id.checkUsou);
        textErroEnvio = findViewById(R.id.textErroEnvio);
        btnPublicar = findViewById(R.id.btnPublicarAvaliacao);

        preencherProduto();

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarAvaliacao).setOnClickListener(v -> finish());
        btnPublicar.setOnClickListener(v -> publicar());

        estrelas.setOnRatingBarChangeListener((barra, nota, doUsuario) -> {
            if (doUsuario && nota < 1) {
                barra.setRating(1);
            }
            mostrarDicaDaNota(false);
        });

        atualizarContador();
        editComentario.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                atualizarContador();
            }
        });
    }

    private void preencherProduto() {
        ((TextView) findViewById(R.id.textNomeProdutoAvaliado))
                .setText(getIntent().getStringExtra(EXTRA_NOME));
        TextView marca = findViewById(R.id.textMarcaProdutoAvaliado);
        marca.setText(getIntent().getStringExtra(EXTRA_MARCA));

        Produto produto = new ProdutoRepository().buscarNoCache(produtoId);
        ImageView foto = findViewById(R.id.imgProdutoAvaliado);
        if (produto != null) {
            ProdutoImagemLoader.carregar(this, foto, produto, R.drawable.bg_foto_produto);
        }
    }

    private void atualizarContador() {
        textContador.setText(getString(R.string.escrever_contador,
                editComentario.length(), RegrasDaAvaliacao.MAXIMO_DO_COMENTARIO));
    }

    private void mostrarDicaDaNota(boolean comErro) {
        textDicaNota.setText(comErro ? R.string.escrever_erro_nota : R.string.escrever_nota_dica);
        textDicaNota.setTextColor(ContextCompat.getColor(this,
                comErro ? R.color.nota_ruim_texto : R.color.texto_ajuda));
    }

    private void publicar() {
        if (enviando) {
            return;
        }
        textErroEnvio.setVisibility(TextView.GONE);
        int nota = Math.round(estrelas.getRating());
        String titulo = editTitulo.getText().toString();
        String comentario = editComentario.getText().toString();

        RegrasDaAvaliacao.Problema problema =
                RegrasDaAvaliacao.conferir(nota, titulo, comentario);
        if (problema != null) {
            mostrarProblema(problema);
            return;
        }

        enviando = true;
        btnPublicar.setEnabled(false);
        btnPublicar.setText(R.string.escrever_publicando);
        repositorio.publicar(produtoId, nota, titulo, comentario, checkUsou.isChecked(),
                resultado -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (resultado == AvaliacoesRepository.Envio.OK) {
                        setResult(RESULT_OK);
                        finish();
                        return;
                    }
                    enviando = false;
                    btnPublicar.setEnabled(true);
                    btnPublicar.setText(R.string.escrever_publicar);
                    textErroEnvio.setText(mensagemDeFalha(resultado));
                    textErroEnvio.setVisibility(TextView.VISIBLE);
                });
    }

    private void mostrarProblema(RegrasDaAvaliacao.Problema problema) {
        switch (problema) {
            case SEM_NOTA:
                mostrarDicaDaNota(true);
                estrelas.requestFocus();
                break;
            case SEM_TITULO:
                erroNoCampo(editTitulo, getString(R.string.escrever_erro_titulo));
                break;
            case TITULO_LONGO:
                erroNoCampo(editTitulo, getString(R.string.escrever_erro_titulo_longo,
                        RegrasDaAvaliacao.MAXIMO_DO_TITULO));
                break;
            case SEM_COMENTARIO:
                erroNoCampo(editComentario, getString(R.string.escrever_erro_comentario));
                break;
            case COMENTARIO_LONGO:
                erroNoCampo(editComentario, getString(R.string.escrever_erro_comentario_longo,
                        RegrasDaAvaliacao.MAXIMO_DO_COMENTARIO));
                break;
        }
    }

    private void erroNoCampo(EditText campo, String mensagem) {
        campo.setError(mensagem);
        campo.requestFocus();
    }

    private int mensagemDeFalha(@Nullable AvaliacoesRepository.Envio resultado) {
        if (resultado == AvaliacoesRepository.Envio.DUPLICADA) {
            return R.string.avaliacoes_ja_avaliou;
        }
        if (resultado == AvaliacoesRepository.Envio.SEM_CONTA) {
            return R.string.avaliacao_sem_conta;
        }
        if (resultado == AvaliacoesRepository.Envio.SEM_VERSAO) {
            return R.string.escrever_erro_sem_versao;
        }
        return R.string.escrever_erro_envio;
    }
}
