package com.venussystem.venusmobile.view.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.view.BuscaActivity;
import com.venussystem.venusmobile.view.DetalheProdutoActivity;
import com.venussystem.venusmobile.view.FiltroActivity;
import com.venussystem.venusmobile.view.adapter.ProdutoCardAdapter;
import com.venussystem.venusmobile.view.util.EstadoCatalogo;
import com.venussystem.venusmobile.viewmodel.BuscaViewModel;

/**
 * A casinha: o campo de busca, o filtro e as sugestoes de produtos, do melhor
 * avaliado para o pior. Outras secoes vem depois.
 */
public class InicioFragment extends Fragment {

    private BuscaViewModel viewModel;
    private ProdutoCardAdapter adapter;
    private EstadoCatalogo estado;
    private ImageButton btnFiltro;

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

        adapter = new ProdutoCardAdapter(produto -> {
            Intent intent = new Intent(requireContext(), DetalheProdutoActivity.class);
            intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produto.getId());
            startActivity(intent);
        });

        RecyclerView lista = view.findViewById(R.id.listaSugestoes);
        lista.setLayoutManager(new GridLayoutManager(
                requireContext(), getResources().getInteger(R.integer.colunas_card_produto)));
        lista.setAdapter(adapter);
        // A lista troca inteira a cada mudanca de filtro; a animacao padrao de
        // insercao e remocao faria a troca parecer lenta.
        lista.setItemAnimator(null);

        estado = new EstadoCatalogo(
                view.findViewById(R.id.painelEstado), lista, () -> viewModel.recarregar());

        view.findViewById(R.id.campoBusca).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BuscaActivity.class)));
        btnFiltro.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FiltroActivity.class)));

        observarViewModel();
    }

    private void observarViewModel() {
        viewModel.getSugestoes().observe(getViewLifecycleOwner(), produtos -> {
            adapter.atualizar(produtos);
            atualizarEstado();
        });

        viewModel.getFiltro().observe(getViewLifecycleOwner(), filtro -> {
            // O icone muda de cor com o filtro ligado, para a pessoa lembrar
            // que a lista nao e o catalogo inteiro.
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

    private void atualizarEstado() {
        estado.atualizar(true, viewModel.temCatalogo(), adapter.getItemCount(),
                viewModel.isFiltroAtivo()
                        ? R.string.home_sem_resultado_filtro
                        : R.string.busca_sem_resultado);
    }

    @Override
    public void onDestroyView() {
        estado.parar();
        super.onDestroyView();
    }
}
