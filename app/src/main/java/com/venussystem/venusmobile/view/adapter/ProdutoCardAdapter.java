package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ProdutoSecao;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;

import java.util.ArrayList;
import java.util.List;

public class ProdutoCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TIPO_PRODUTO = 0;
    private static final int TIPO_CABECALHO = 1;

    public interface AoClicar {
        void noProduto(Produto produto);
    }

    private final List<Produto> produtos = new ArrayList<>();
    private final List<Object> itens = new ArrayList<>();
    private final AoClicar aoClicar;

    public ProdutoCardAdapter(AoClicar aoClicar) {
        this.aoClicar = aoClicar;
    }

    public void atualizar(List<Produto> novos) {
        produtos.clear();
        itens.clear();
        if (novos != null) {
            produtos.addAll(novos);
            itens.addAll(novos);
        }
        notifyDataSetChanged();
    }

    public void atualizarSecoes(List<ProdutoSecao> secoes) {
        produtos.clear();
        itens.clear();
        if (secoes != null) {
            for (ProdutoSecao secao : secoes) {
                itens.add(secao);
                itens.addAll(secao.getProdutos());
                produtos.addAll(secao.getProdutos());
            }
        }
        notifyDataSetChanged();
    }

    public boolean isCabecalho(int position) {
        return position >= 0 && position < itens.size() && itens.get(position) instanceof ProdutoSecao;
    }

    @Nullable
    public String getTituloCabecalho(int position) {
        if (!isCabecalho(position)) {
            return null;
        }
        return ((ProdutoSecao) itens.get(position)).getNome();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        LayoutInflater inflater = LayoutInflater.from(pai.getContext());
        if (tipo == TIPO_CABECALHO) {
            return new SectionViewHolder(inflater.inflate(R.layout.item_secao_produto, pai, false));
        }
        return new CardViewHolder(inflater.inflate(R.layout.item_produto_card, pai, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int posicao) {
        Object item = itens.get(posicao);
        if (item instanceof ProdutoSecao) {
            ((SectionViewHolder) holder).preencher((ProdutoSecao) item);
        } else {
            ((CardViewHolder) holder).preencher((Produto) item, aoClicar);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return isCabecalho(position) ? TIPO_CABECALHO : TIPO_PRODUTO;
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        SectionViewHolder(@NonNull View item) {
            super(item);
        }

        void preencher(ProdutoSecao secao) {
            ((TextView) itemView).setText(secao.getNome());
        }
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imagem;
        private final TextView marca;
        private final TextView nome;

        CardViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            marca = item.findViewById(R.id.textMarca);
            nome = item.findViewById(R.id.textNome);
        }

        void preencher(Produto produto, AoClicar aoClicar) {
            marca.setText(produto.getBrandName());
            nome.setText(semMarca(produto.getName(), produto.getBrandName()));

            ProdutoImagemLoader.carregar(
                    itemView.getContext(), imagem, produto, R.drawable.bg_foto_produto);

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
        }

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
            if (!depois.isEmpty() && Character.isLetterOrDigit(depois.charAt(0))) {
                return nome;
            }

            String resto = depois.replaceFirst("^[\\s\\-\\u2013:]+", "");
            return resto.isEmpty() ? nome : resto;
        }

    }
}
