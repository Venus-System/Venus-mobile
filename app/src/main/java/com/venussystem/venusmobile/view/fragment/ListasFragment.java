package com.venussystem.venusmobile.view.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.ColecaoRepository;
import com.venussystem.venusmobile.view.CriarListaActivity;
import com.venussystem.venusmobile.view.DetalheListaActivity;
import com.venussystem.venusmobile.model.Colecao;
import com.venussystem.venusmobile.view.adapter.ColecaoAdapter;
import com.venussystem.venusmobile.view.adapter.SeguindoAdapter;

public class ListasFragment extends Fragment {
    private final ColecaoRepository repository = new ColecaoRepository();

    private ColecaoAdapter adapterMinhas;
    private SeguindoAdapter adapterSeguindo;
    private RecyclerView lista;
    private View btnNovaLista;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_listas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapterMinhas = new ColecaoAdapter(this::abrirLista);

        adapterSeguindo = new SeguindoAdapter(colecao ->
                Toast.makeText(requireContext(),
                        getString(R.string.seguindo_agora, colecao.getName()),
                        Toast.LENGTH_SHORT).show());

        lista = view.findViewById(R.id.listaColecoes);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));

        btnNovaLista = view.findViewById(R.id.btnNovaLista);
        btnNovaLista.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CriarListaActivity.class)));

        TabLayout abas = view.findViewById(R.id.abasListas);
        abas.addTab(abas.newTab().setText(R.string.aba_minhas_listas));
        abas.addTab(abas.newTab().setText(R.string.aba_seguindo));

        abas.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab aba) {
                mostrar(aba.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab aba) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab aba) {
            }
        });

        mostrar(0);
    }

    private void mostrar(int aba) {
        boolean minhas = aba == 0;

        lista.setAdapter(minhas ? adapterMinhas : adapterSeguindo);
        btnNovaLista.setVisibility(minhas ? View.VISIBLE : View.GONE);

        if (minhas) {
            repository.minhasListas().observe(getViewLifecycleOwner(), adapterMinhas::atualizar);
        } else {
            repository.seguindo().observe(getViewLifecycleOwner(), adapterSeguindo::atualizar);
        }
    }

    private void abrirLista(Colecao colecao) {
        Intent intent = new Intent(requireContext(), DetalheListaActivity.class);
        intent.putExtra(DetalheListaActivity.EXTRA_NOME, colecao.getName());
        // A Colecao ainda nao carrega a privacidade, e as listas desta aba sao
        // as do proprio usuario: por enquanto abrem como privadas.
        intent.putExtra(DetalheListaActivity.EXTRA_PUBLICA, false);
        intent.putExtra(DetalheListaActivity.EXTRA_AUTOR, colecao.getAutor());
        startActivity(intent);
    }
}
