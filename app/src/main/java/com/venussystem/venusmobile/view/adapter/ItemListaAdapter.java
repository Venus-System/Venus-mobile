package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;

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
        private final View btnRemover;

        ItemViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            marca = item.findViewById(R.id.textMarca);
            nome = item.findViewById(R.id.textNome);
            btnRemover = item.findViewById(R.id.btnRemoverProduto);
        }

        void preencher(Produto produto, AoClicar aoClicar, AoRemover aoRemover) {
            marca.setText(produto.getBrandName());
            nome.setText(produto.getName());

            ProdutoImagemLoader.carregar(
                    itemView.getContext(), imagem, produto, R.drawable.bg_card_produto);

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
            btnRemover.setOnClickListener(v -> aoRemover.doProduto(produto));
        }
    }
}
