package com.venussystem.venusmobile.view.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.CategoriaContagem;
import com.venussystem.venusmobile.view.DetalheProdutoActivity;
import com.venussystem.venusmobile.view.adapter.ProdutoAdapter;
import com.venussystem.venusmobile.viewmodel.BuscaViewModel;

import java.util.List;

public class BuscaFragment extends Fragment {

    private static final String ARG_MODO_ESCOLHA = "modo_escolha";

    /** Chave do FragmentResult devolvido quando o fragment esta em modo escolha. */
    public static final String RESULTADO_ESCOLHA_PRODUTO = "escolha_produto";
    public static final String EXTRA_PRODUTO_ID = "produto_id";

    /**
     * Mesma tela de busca, mas ao inves de abrir o produto ela devolve a
     * escolha pelo FragmentResult - usada para escolher um produto pra
     * adicionar numa lista (ver EscolherProdutoActivity).
     */
    public static BuscaFragment paraEscolherProduto() {
        BuscaFragment fragment = new BuscaFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_MODO_ESCOLHA, true);
        fragment.setArguments(args);
        return fragment;
    }

    private BuscaViewModel viewModel;
    private ProdutoAdapter adapter;
    private boolean modoEscolha;

    // O servidor gratuito hiberna, e acordar ele foi medido em ~100s. Sem aviso,
    // esse tempo parado passa impressao de travamento - entao depois de alguns
    // segundos de espera explicamos o que esta acontecendo.
    private static final long ESPERA_ATE_AVISAR = 4000L;

    private EditText campo;
    private TextView textVazio;
    private TextView textErro;
    private TextView textFiltroAtivo;
    private View blocoErro;
    private View carregando;
    private View textAcordando;
    private View painelLanding;
    private View secaoRecentes;
    private View secaoCategorias;
    private View blocoFiltroAtivo;
    private LinearLayout listaRecentes;
    private ChipGroup grupoCategorias;
    private RecyclerView listaProdutos;

    // O clearCheck() do proprio codigo dispara o listener do ChipGroup de novo.
    // Sem esta trava, limpar o filtro entraria em recursao.
    private boolean ajustandoChips;

    private final Handler relogio = new Handler(Looper.getMainLooper());
    private final Runnable avisarDemora = () -> textAcordando.setVisibility(View.VISIBLE);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_busca, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        modoEscolha = getArguments() != null && getArguments().getBoolean(ARG_MODO_ESCOLHA, false);
        viewModel = new ViewModelProvider(this).get(BuscaViewModel.class);

        campo = view.findViewById(R.id.campoBuscaProduto);
        textVazio = view.findViewById(R.id.textVazio);
        textErro = view.findViewById(R.id.textErro);
        textFiltroAtivo = view.findViewById(R.id.textFiltroAtivo);
        blocoErro = view.findViewById(R.id.blocoErro);
        carregando = view.findViewById(R.id.carregando);
        textAcordando = view.findViewById(R.id.textAcordando);
        painelLanding = view.findViewById(R.id.painelLanding);
        secaoRecentes = view.findViewById(R.id.secaoRecentes);
        secaoCategorias = view.findViewById(R.id.secaoCategorias);
        blocoFiltroAtivo = view.findViewById(R.id.blocoFiltroAtivo);
        listaRecentes = view.findViewById(R.id.listaRecentes);
        grupoCategorias = view.findViewById(R.id.grupoCategorias);
        listaProdutos = view.findViewById(R.id.listaProdutos);

        adapter = new ProdutoAdapter(produto -> {
            // Abrir um produto vindo do resultado confirma que a busca serviu,
            // entao o termo entra nos recentes aqui tambem - nao so quando o
            // usuario aperta "buscar" no teclado.
            viewModel.registrarTermoAtual();

            if (modoEscolha) {
                Bundle resultado = new Bundle();
                resultado.putLong(EXTRA_PRODUTO_ID, produto.getId());
                getParentFragmentManager().setFragmentResult(RESULTADO_ESCOLHA_PRODUTO, resultado);
                return;
            }

            Intent intent = new Intent(requireContext(), DetalheProdutoActivity.class);
            intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produto.getId());
            startActivity(intent);
        });

        listaProdutos.setLayoutManager(new LinearLayoutManager(requireContext()));
        listaProdutos.setAdapter(adapter);

        view.findViewById(R.id.btnTentarDeNovo)
                .setOnClickListener(v -> viewModel.recarregar());

        view.findViewById(R.id.btnLimparFiltro).setOnClickListener(v -> limparCategoria());

        prepararCampo();
        prepararCategorias();
        observarViewModel();
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

        // So o "buscar" do teclado guarda o termo nos recentes. Fazer isso a
        // cada tecla encheria a lista de fragmentos do que foi digitado.
        campo.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.confirmarBusca(campo.getText().toString());
                esconderTeclado();
                atualizarEstado();
                return true;
            }
            return false;
        });
    }

    private void prepararCategorias() {
        grupoCategorias.setOnCheckedStateChangeListener((grupo, marcados) -> {
            if (ajustandoChips) {
                return;
            }
            if (marcados.isEmpty()) {
                viewModel.selecionarCategoria(null);
            } else {
                Chip chip = grupo.findViewById(marcados.get(0));
                if (chip != null && chip.getTag() instanceof Long) {
                    viewModel.selecionarCategoria((Long) chip.getTag());
                    textFiltroAtivo.setText(chip.getText());
                }
            }
            atualizarEstado();
        });
    }

    private void observarViewModel() {
        viewModel.getProdutos().observe(getViewLifecycleOwner(), produtos -> {
            adapter.atualizar(produtos);
            atualizarEstado();
        });

        viewModel.getTermosRecentes().observe(getViewLifecycleOwner(), termos -> {
            montarRecentes(termos);
            atualizarEstado();
        });

        viewModel.getCategorias().observe(getViewLifecycleOwner(), categorias -> {
            montarCategorias(categorias);
            atualizarEstado();
        });

        viewModel.getCarregando().observe(getViewLifecycleOwner(), ocupado -> {
            boolean esperando = Boolean.TRUE.equals(ocupado);
            carregando.setVisibility(esperando ? View.VISIBLE : View.GONE);

            relogio.removeCallbacks(avisarDemora);
            textAcordando.setVisibility(View.GONE);
            if (esperando) {
                relogio.postDelayed(avisarDemora, ESPERA_ATE_AVISAR);
            }
            atualizarEstado();
        });

        viewModel.getErro().observe(getViewLifecycleOwner(), mensagem -> {
            textErro.setText(mensagem);
            atualizarEstado();
        });
    }

    private void montarRecentes(List<String> termos) {
        listaRecentes.removeAllViews();

        boolean temRecentes = termos != null && !termos.isEmpty();
        secaoRecentes.setVisibility(temRecentes ? View.VISIBLE : View.GONE);
        if (!temRecentes) {
            return;
        }

        for (String termo : termos) {
            View linha = getLayoutInflater()
                    .inflate(R.layout.item_chip_selecionado, listaRecentes, false);

            ((TextView) linha.findViewById(R.id.textChip)).setText(termo);

            linha.setOnClickListener(v -> {
                campo.setText(termo);
                campo.setSelection(termo.length());
                viewModel.confirmarBusca(termo);
                esconderTeclado();
                atualizarEstado();
            });

            linha.findViewById(R.id.btnRemoverChip)
                    .setOnClickListener(v -> viewModel.removerRecente(termo));

            listaRecentes.addView(linha);
        }
    }

    private void montarCategorias(List<CategoriaContagem> categorias) {
        ajustandoChips = true;
        grupoCategorias.removeAllViews();

        boolean temCategorias = categorias != null && !categorias.isEmpty();
        secaoCategorias.setVisibility(temCategorias ? View.VISIBLE : View.GONE);

        if (temCategorias) {
            for (CategoriaContagem categoria : categorias) {
                Chip chip = (Chip) getLayoutInflater()
                        .inflate(R.layout.item_chip_categoria, grupoCategorias, false);
                chip.setId(View.generateViewId());
                chip.setText(categoria.getNome());
                chip.setTag(categoria.getId());
                grupoCategorias.addView(chip);
            }
        }
        ajustandoChips = false;
    }

    private void limparCategoria() {
        ajustandoChips = true;
        grupoCategorias.clearCheck();
        ajustandoChips = false;

        viewModel.limparCategoria();
        atualizarEstado();
    }

    private void esconderTeclado() {
        InputMethodManager teclado = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (teclado != null) {
            teclado.hideSoftInputFromWindow(campo.getWindowToken(), 0);
        }
        campo.clearFocus();
    }

    @Override
    public void onDestroyView() {
        // O aviso agendado aponta para uma View que esta indo embora: sem tirar
        // ele da fila, dispararia em cima de uma tela que nao existe mais.
        relogio.removeCallbacks(avisarDemora);
        super.onDestroyView();
    }

    /**
     * Um estado por vez, e a ordem importa: o landing precisa ser decidido
     * antes do vazio, senao a lista vazia que o landing produz faria piscar
     * "nenhum produto encontrado" por cima dele.
     */
    private void atualizarEstado() {
        boolean ocupado = carregando.getVisibility() == View.VISIBLE;

        // Erro so bloqueia a tela enquanto nao houver catalogo: se uma
        // atualizacao falhar depois de um carregamento que deu certo, o que
        // esta em memoria continua bom e a tela segue utilizavel.
        boolean temErro = !ocupado && textErro.getText().length() > 0 && !viewModel.temCatalogo();
        boolean landing = !ocupado && !temErro && viewModel.isLanding();
        boolean vazio = !ocupado && !temErro && !landing && adapter.getItemCount() == 0;

        blocoErro.setVisibility(temErro ? View.VISIBLE : View.GONE);
        painelLanding.setVisibility(landing ? View.VISIBLE : View.GONE);
        textVazio.setVisibility(vazio ? View.VISIBLE : View.GONE);
        listaProdutos.setVisibility(
                !ocupado && !temErro && !landing && !vazio ? View.VISIBLE : View.GONE);
        blocoFiltroAtivo.setVisibility(
                !landing && viewModel.getCategoriaSelecionada() != null ? View.VISIBLE : View.GONE);
    }
}
