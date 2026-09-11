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

public class SeguindoAdapter extends RecyclerView.Adapter<SeguindoAdapter.SeguindoViewHolder> {

    public interface AoSeguir {
        void naColecao(Colecao colecao);
    }

    private final List<Colecao> colecoes = new ArrayList<>();
    private final AoSeguir aoSeguir;

    public SeguindoAdapter(AoSeguir aoSeguir) {
        this.aoSeguir = aoSeguir;
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
    public SeguindoViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_seguindo, pai, false);
        return new SeguindoViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull SeguindoViewHolder holder, int posicao) {
        holder.preencher(colecoes.get(posicao), aoSeguir);
    }

    @Override
    public int getItemCount() {
        return colecoes.size();
    }

    static class SeguindoViewHolder extends RecyclerView.ViewHolder {

        private final ImageView miniatura;
        private final TextView nome;
        private final TextView autor;
        private final View botaoSeguir;

        SeguindoViewHolder(@NonNull View item) {
            super(item);
            miniatura = item.findViewById(R.id.imgMiniatura);
            nome = item.findViewById(R.id.textNomeSeguindo);
            autor = item.findViewById(R.id.textAutor);
            botaoSeguir = item.findViewById(R.id.btnSeguir);
        }

        void preencher(Colecao colecao, AoSeguir aoSeguir) {
            nome.setText(colecao.getName());
            autor.setText(colecao.getAutor());

            if (colecao.getImagemLocal() != 0) {
                miniatura.setImageResource(colecao.getImagemLocal());
            } else {
                ImageLoader carregador = Coil.imageLoader(itemView.getContext());
                carregador.enqueue(new ImageRequest.Builder(itemView.getContext())
                        .data(colecao.getImageUrl())
                        .target(miniatura)
                        .build());
            }

            botaoSeguir.setOnClickListener(v -> aoSeguir.naColecao(colecao));
        }
    }
}
