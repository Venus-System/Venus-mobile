package com.venussystem.venusmobile.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.view.adapter.HistoricoAdapter;
import com.venussystem.venusmobile.view.adapter.ProdutoCardAdapter;
import com.venussystem.venusmobile.view.util.EstadoCatalogo;
import com.venussystem.venusmobile.viewmodel.BuscaViewModel;
import com.venussystem.venusmobile.viewmodel.HistoricoViewModel;

public class BuscaActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUTO_ID = "produto_id";
    private static final String EXTRA_MODO_ESCOLHA = "busca_modo_escolha";

    public static Intent paraEscolherProduto(Context context) {
        return new Intent(context, BuscaActivity.class).putExtra(EXTRA_MODO_ESCOLHA, true);
    }

    private BuscaViewModel viewModel;
    private ProdutoCardAdapter adapterResultados;
    private HistoricoAdapter adapterHistorico;
    private EstadoCatalogo estado;
    private EditText campo;
    private ImageButton btnFiltro;
    private View grupoHistorico;
    private View textHistoricoVazio;
    private boolean modoEscolha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicarComTeclado(this, R.layout.activity_busca);

        modoEscolha = getIntent().getBooleanExtra(EXTRA_MODO_ESCOLHA, false);
        viewModel = new ViewModelProvider(this).get(BuscaViewModel.class);
        HistoricoViewModel historico = new ViewModelProvider(this).get(HistoricoViewModel.class);

        campo = findViewById(R.id.campoBuscaProduto);
        btnFiltro = findViewById(R.id.btnFiltro);
        grupoHistorico = findViewById(R.id.grupoHistorico);
        textHistoricoVazio = findViewById(R.id.textHistoricoVazio);

        if (modoEscolha) {
            campo.setHint(R.string.busca_hint_escolher);
        }

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        btnFiltro.setOnClickListener(v ->
                startActivity(new Intent(this, FiltroActivity.class)));

        prepararHistorico(historico);
        prepararResultados();
        prepararCampo();
        observarViewModel();
        abrirTeclado();
    }

    private void prepararHistorico(HistoricoViewModel historico) {
        adapterHistorico = new HistoricoAdapter(this::aoEscolherProduto);

        RecyclerView lista = findViewById(R.id.listaHistorico);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adapterHistorico);

        historico.getLinhas().observe(this, linhas -> {
            adapterHistorico.atualizar(linhas);
            textHistoricoVazio.setVisibility(
                    adapterHistorico.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        });
    }

    private void prepararResultados() {
        adapterResultados = new ProdutoCardAdapter(this::aoEscolherProduto);

        RecyclerView lista = findViewById(R.id.listaResultados);
        lista.setLayoutManager(new GridLayoutManager(
                this, getResources().getInteger(R.integer.colunas_card_produto)));
        lista.setAdapter(adapterResultados);
        lista.setItemAnimator(null);

        estado = new EstadoCatalogo(
                findViewById(R.id.painelEstado), lista, () -> viewModel.recarregar());
    }

    private void prepararCampo() {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int i, int j, int k) {
            }

            @Override
            public void onTextChanged(CharSequence s, int i, int j, int k) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.buscar(s.toString());
                atualizarEstado();
            }
        });

        campo.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEARCH) {
                WindowCompat.getInsetsController(getWindow(), campo)
                        .hide(WindowInsetsCompat.Type.ime());
                return true;
            }
            return false;
        });
    }

    private void observarViewModel() {
        viewModel.getResultados().observe(this, produtos -> {
            adapterResultados.atualizar(produtos);
            atualizarEstado();
        });

        viewModel.getFiltro().observe(this, filtro -> {
            if (filtro != null && filtro.isAtivo()) {
                btnFiltro.setColorFilter(ContextCompat.getColor(this, R.color.magenta_venus));
            } else {
                btnFiltro.clearColorFilter();
            }
            atualizarEstado();
        });

        viewModel.getCarregando().observe(this, ocupado -> {
            estado.definirCarregando(Boolean.TRUE.equals(ocupado));
            atualizarEstado();
        });

        viewModel.getErro().observe(this, mensagem -> {
            estado.definirErro(mensagem);
            atualizarEstado();
        });
    }

    private void atualizarEstado() {
        boolean mostrandoHistorico = viewModel.isMostrandoHistorico();
        grupoHistorico.setVisibility(mostrandoHistorico ? View.VISIBLE : View.GONE);
        estado.atualizar(!mostrandoHistorico, viewModel.temCatalogo(),
                adapterResultados.getItemCount(), R.string.busca_sem_resultado);
    }

    private void aoEscolherProduto(Produto produto) {
        if (modoEscolha) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_PRODUTO_ID, (long) produto.getId()));
            finish();
            return;
        }
        Intent intent = new Intent(this, DetalheProdutoActivity.class);
        intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produto.getId());
        startActivity(intent);
    }

    private void abrirTeclado() {
        campo.requestFocus();
        campo.post(() -> WindowCompat.getInsetsController(getWindow(), campo)
                .show(WindowInsetsCompat.Type.ime()));
    }

    @Override
    protected void onDestroy() {
        estado.parar();
        super.onDestroy();
    }
}
