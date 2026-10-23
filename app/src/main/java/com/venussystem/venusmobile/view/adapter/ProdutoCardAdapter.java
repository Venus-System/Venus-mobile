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

/**
 * Cards de produto em grade, usados nas sugestões da Home e nos resultados da
 * busca: foto no topo e nome do produto embaixo.
 */
public class ProdutoCardAdapter extends RecyclerView.Adapter<ProdutoCardAdapter.CardViewHolder> {

    public interface AoClicar {
        void noProduto(Produto produto);
    }

    private final List<Produto> produtos = new ArrayList<>();
    private final AoClicar aoClicar;

    public ProdutoCardAdapter(AoClicar aoClicar) {
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
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_produto_card, pai, false);
        return new CardViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int posicao) {
        holder.preencher(produtos.get(posicao), aoClicar);
    }

    @Override
    public int getItemCount() {
        return produtos.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imagem;
        private final TextView nome;

        CardViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            nome = item.findViewById(R.id.textNome);
        }

        void preencher(Produto produto, AoClicar aoClicar) {
            nome.setText(semMarca(produto.getName(), produto.getBrandName()));

            ProdutoImagemLoader.carregar(
                    itemView.getContext(), imagem, produto, R.drawable.bg_foto_produto);

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
        }

        /**
         * O nome costuma vir com a marca na frente ("Nivea Creme"). Como o
         * card exibe somente o nome, tira o prefixo da marca na exibicao.
         * Se sobrar nada (nome igual a marca), mantem o nome inteiro.
         */
        static String semMarca(String nome, String marca) {
            if (nome == null || marca == null) {
                return nome;
            }
            String nomeLimpo = nome.trim();
            String marcaLimpa = marca.trim();
            if (marcaLimpa.isEmpty()
                    || !nomeLimpo.regionMatches(true, 0, marcaLimpa, 0, marcaLimpa.length())) {
                return nome;
            }

            String depois = nomeLimpo.substring(marcaLimpa.length());
            // "Nivea" nao deve cortar o inicio de "Niveaxyz".
            if (!depois.isEmpty() && Character.isLetterOrDigit(depois.charAt(0))) {
                return nome;
            }

            String resto = depois.replaceFirst("^[\\s\\-\\u2013:]+", "");
            return resto.isEmpty() ? nome : resto;
        }

    }
}
