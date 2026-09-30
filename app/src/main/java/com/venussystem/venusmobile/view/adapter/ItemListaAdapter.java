package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;

import java.util.ArrayList;
import java.util.List;

public class ItemListaAdapter extends RecyclerView.Adapter<ItemListaAdapter.ItemViewHolder> {
    public interface AoClicar {
        void noProduto(Produto produto);
    }

    public interface AoRemover {
        void doProduto(Produto produto);
    }

    private final List<Produto> produtos = new ArrayList<>();
    private final AoClicar aoClicar;
    private final AoRemover aoRemover;

    public ItemListaAdapter(AoClicar aoClicar, AoRemover aoRemover) {
        this.aoClicar = aoClicar;
        this.aoRemover = aoRemover;
    }

    public void atualizar(List<Produto> novos) {
        produtos.clear();
        if (novos != null) {
            produtos.addAll(novos);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_produto_lista, pai, false);
        return new ItemViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int posicao) {
        holder.preencher(produtos.get(posicao), aoClicar, aoRemover);
    }

    @Override
    public int getItemCount() {
        return produtos.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imagem;
        private final TextView marca;
        private final TextView nome;
        private final TextView verNota;
        private final View btnRemover;

        ItemViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            marca = item.findViewById(R.id.textMarca);
            nome = item.findViewById(R.id.textNome);
            verNota = item.findViewById(R.id.btnVerNota);
            btnRemover = item.findViewById(R.id.btnRemoverProduto);
        }

        void preencher(Produto produto, AoClicar aoClicar, AoRemover aoRemover) {
            marca.setText(produto.getBrandName());
            nome.setText(produto.getName());
            mostrarNota(produto.getOverallScore());

            ImageLoader carregador = Coil.imageLoader(itemView.getContext());
            carregador.enqueue(new ImageRequest.Builder(itemView.getContext())
                    .data(produto.getImageUrl())
                    .target(imagem)
                    .placeholder(R.drawable.bg_card_produto)
                    .error(R.drawable.bg_card_produto)
                    .fallback(R.drawable.bg_card_produto)
                    .build());

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
            btnRemover.setOnClickListener(v -> aoRemover.doProduto(produto));
        }

        private void mostrarNota(Integer valor) {
            if (valor == null) {
                verNota.setText(R.string.nota_indisponivel);
                verNota.setTextColor(ContextCompat.getColor(
                        itemView.getContext(), R.color.cinza_descricao));
                return;
            }

            int cor;
            if (valor >= 70) {
                cor = R.color.nota_boa;
            } else if (valor >= 40) {
                cor = R.color.nota_media;
            } else {
                cor = R.color.nota_ruim;
            }

            verNota.setText(String.valueOf(valor));
            verNota.setTextColor(ContextCompat.getColor(itemView.getContext(), cor));
        }
    }
}
