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

public class AlternativaAdapter extends RecyclerView.Adapter<AlternativaAdapter.AlternativaViewHolder> {

    public interface AoClicar {
        void noProduto(Produto produto);
    }

    private final List<Produto> produtos = new ArrayList<>();
    private final AoClicar aoClicar;

    public AlternativaAdapter(AoClicar aoClicar) {
        this.aoClicar = aoClicar;
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
    public AlternativaViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_alternativa_produto, pai, false);
        return new AlternativaViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull AlternativaViewHolder holder, int posicao) {
        holder.preencher(produtos.get(posicao), aoClicar);
    }

    @Override
    public int getItemCount() {
        return produtos.size();
    }

    static class AlternativaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imagem;
        private final TextView nome;
        private final TextView marca;

        AlternativaViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            nome = item.findViewById(R.id.textNome);
            marca = item.findViewById(R.id.textMarca);
        }

        void preencher(Produto produto, AoClicar aoClicar) {
            nome.setText(produto.getName());
            marca.setText(produto.getBrandName());

            ProdutoImagemLoader.carregar(
                    itemView.getContext(), imagem, produto, R.drawable.bg_foto_historico);

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
        }
    }
}
