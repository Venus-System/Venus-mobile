package com.venussystem.venusmobile.view.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.view.BuscaActivity;
import com.venussystem.venusmobile.view.DetalheProdutoActivity;
import com.venussystem.venusmobile.view.FiltroActivity;
import com.venussystem.venusmobile.view.adapter.ProdutoCardAdapter;
import com.venussystem.venusmobile.view.componente.CartaoPerfilView;
import com.venussystem.venusmobile.view.util.EstadoCatalogo;
import com.venussystem.venusmobile.viewmodel.BuscaViewModel;

public class InicioFragment extends Fragment {

    private BuscaViewModel viewModel;
    private ProdutoCardAdapter adapter;
    private EstadoCatalogo estado;
    private ImageButton btnFiltro;
    private CartaoPerfilView cartaoPerfil;
    private RecyclerView listaSugestoes;
    private TextView tituloSugestoes;
    private String tituloHomeAtual;
    private int versaoAnimacaoTitulo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inicio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(BuscaViewModel.class);
        btnFiltro = view.findViewById(R.id.btnFiltro);
        cartaoPerfil = view.findViewById(R.id.cartaoPerfil);

        adapter = new ProdutoCardAdapter(produto -> {
            Intent intent = new Intent(requireContext(), DetalheProdutoActivity.class);
            intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produto.getId());
            startActivity(intent);
        });

        listaSugestoes = view.findViewById(R.id.listaSugestoes);
        tituloSugestoes = view.findViewById(R.id.textTituloSugestoes);
        RecyclerView lista = listaSugestoes;
        int colunas = getResources().getInteger(R.integer.colunas_card_produto);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), colunas);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isCabecalho(position) ? colunas : 1;
            }
        });
        lista.setLayoutManager(layoutManager);
        lista.setAdapter(adapter);
        lista.setItemAnimator(null);
        lista.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                atualizarTituloDaCategoria(recyclerView, tituloSugestoes);
            }
        });
        tituloHomeAtual = getString(R.string.home_sugestoes);
        tituloSugestoes.setText(tituloHomeAtual);
        tituloSugestoes.setAlpha(1f);

        estado = new EstadoCatalogo(
                view.findViewById(R.id.painelEstado), lista, () -> viewModel.recarregar());

        view.findViewById(R.id.campoBusca).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BuscaActivity.class)));
        btnFiltro.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FiltroActivity.class)));

        observarViewModel();
    }

    private void observarViewModel() {
        viewModel.getSugestoesPorCategoria().observe(getViewLifecycleOwner(), secoes -> {
            adapter.atualizarSecoes(secoes);
            listaSugestoes.post(() -> atualizarTituloDaCategoria(listaSugestoes, tituloSugestoes));
            atualizarEstado();
        });

        viewModel.getFiltro().observe(getViewLifecycleOwner(), filtro -> {
            if (filtro != null && filtro.isAtivo()) {
                btnFiltro.setColorFilter(ContextCompat.getColor(requireContext(), R.color.magenta_venus));
            } else {
                btnFiltro.clearColorFilter();
            }
            atualizarEstado();
        });

        viewModel.getCarregando().observe(getViewLifecycleOwner(), ocupado -> {
            estado.definirCarregando(Boolean.TRUE.equals(ocupado));
            atualizarEstado();
        });

        viewModel.getErro().observe(getViewLifecycleOwner(), mensagem -> {
            estado.definirErro(mensagem);
            atualizarEstado();
        });
    }

    private void atualizarTituloDaCategoria(RecyclerView lista, TextView titulo) {
        String novoTitulo = getString(R.string.home_sugestoes);
        if (lista.canScrollVertically(-1)
                && lista.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) lista.getLayoutManager();
            int primeiro = layoutManager.findFirstVisibleItemPosition();
            int posicaoCabecalho = primeiro;
            while (posicaoCabecalho >= 0 && !adapter.isCabecalho(posicaoCabecalho)) {
                posicaoCabecalho--;
            }
            String categoria = adapter.getTituloCabecalho(posicaoCabecalho);
            if (categoria != null) {
                novoTitulo = categoria;
            }
        }

        if (novoTitulo.equals(tituloHomeAtual)) {
            return;
        }

        tituloHomeAtual = novoTitulo;
        String tituloAnimado = novoTitulo;
        int animacaoAtual = ++versaoAnimacaoTitulo;
        titulo.animate().cancel();
        titulo.animate()
                .alpha(0f)
                .setDuration(90)
                .withEndAction(() -> {
                    if (animacaoAtual != versaoAnimacaoTitulo) {
                        return;
                    }
                    titulo.setText(tituloAnimado);
                    titulo.animate().alpha(1f).setDuration(120).start();
                })
                .start();
    }

    private void atualizarEstado() {
        estado.atualizar(true, viewModel.temCatalogo(), adapter.getItemCount(),
                viewModel.isFiltroAtivo()
                        ? R.string.home_sem_resultado_filtro
                        : R.string.busca_sem_resultado);
    }

    @Override
    public void onResume() {
        super.onResume();
        cartaoPerfil.atualizar(new PerfilRepository(requireContext()).progresso());
    }

    @Override
    public void onDestroyView() {
        estado.parar();
        super.onDestroyView();
    }
}
