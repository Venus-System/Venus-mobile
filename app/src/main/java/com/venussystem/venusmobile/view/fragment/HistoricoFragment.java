package com.venussystem.venusmobile.view.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.view.DetalheProdutoActivity;
import com.venussystem.venusmobile.view.adapter.HistoricoAdapter;
import com.venussystem.venusmobile.viewmodel.HistoricoViewModel;

public class HistoricoFragment extends Fragment {

    private HistoricoViewModel viewModel;
    private HistoricoAdapter adapter;
    private View textVazio;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_historico, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HistoricoViewModel.class);
        textVazio = view.findViewById(R.id.textVazio);

        adapter = new HistoricoAdapter(produto -> {
            Intent intent = new Intent(requireContext(), DetalheProdutoActivity.class);
            intent.putExtra(DetalheProdutoActivity.EXTRA_ID, produto.getId());
            startActivity(intent);
        });

        RecyclerView lista = view.findViewById(R.id.listaHistorico);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        viewModel.getLinhas().observe(getViewLifecycleOwner(), linhas -> {
            adapter.atualizar(linhas);
            textVazio.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        });
    }
}
