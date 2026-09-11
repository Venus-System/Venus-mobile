package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;

import java.util.ArrayList;
import java.util.List;

public class ColecaoAdapter extends RecyclerView.Adapter<ColecaoAdapter.ColecaoViewHolder> {
    public interface AoClicar {
        void naColecao(Colecao colecao);
    }

    private final List<Colecao> colecoes = new ArrayList<>();
    private final AoClicar aoClicar;

    public ColecaoAdapter(AoClicar aoClicar) {
        this.aoClicar = aoClicar;
    }

    public void atualizar(List<Colecao> novas) {
        colecoes.clear();
        if (novas != null) {
            colecoes.addAll(novas);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ColecaoViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_colecao, pai, false);
        return new ColecaoViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull ColecaoViewHolder holder, int posicao) {
        holder.preencher(colecoes.get(posicao), aoClicar);
    }

    @Override
    public int getItemCount() {
        return colecoes.size();
    }

    static class ColecaoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView capa;
        private final TextView nome;

        ColecaoViewHolder(@NonNull View item) {
            super(item);
            capa = item.findViewById(R.id.imgCapa);
            nome = item.findViewById(R.id.textNomeLista);
        }

        void preencher(Colecao colecao, AoClicar aoClicar) {
            nome.setText(colecao.getName());

            if (colecao.getImagemLocal() != 0) {
                capa.setImageResource(colecao.getImagemLocal());
            } else {
                ImageLoader carregador = Coil.imageLoader(itemView.getContext());
                carregador.enqueue(new ImageRequest.Builder(itemView.getContext())
                        .data(colecao.getImageUrl())
                        .target(capa)
                        .build());
            }

            itemView.setOnClickListener(v -> aoClicar.naColecao(colecao));
        }
    }
}
