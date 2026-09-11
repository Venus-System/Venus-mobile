package com.venussystem.venusmobile.view.adapter;

import android.content.res.ColorStateList;
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
import com.venussystem.venusmobile.view.util.NotaProdutoUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de produtos em card com selo de nota circular, usada na aba
 * "Alternativas" do detalhe do produto.
 */
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
        private final TextView nota;

        AlternativaViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            nome = item.findViewById(R.id.textNome);
            marca = item.findViewById(R.id.textMarca);
            nota = item.findViewById(R.id.textNota);
        }

        void preencher(Produto produto, AoClicar aoClicar) {
            nome.setText(produto.getName());
            marca.setText(produto.getBrandName());

            Integer valor = produto.getOverallScore();
            nota.setText(valor == null
                    ? itemView.getContext().getString(R.string.nota_indisponivel)
                    : String.valueOf(valor));
            nota.setTextColor(ContextCompat.getColor(
                    itemView.getContext(), NotaProdutoUtil.corPara(valor)));

            // O drawable e compartilhado entre os itens reciclados - pintar ele
            // direto vazaria a cor de um item para os outros. O tint resolve.
            nota.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    itemView.getContext(), NotaProdutoUtil.fundoPara(valor))));

            ImageLoader carregador = Coil.imageLoader(itemView.getContext());
            carregador.enqueue(new ImageRequest.Builder(itemView.getContext())
                    .data(produto.getImageUrl())
                    .target(imagem)
                    .placeholder(R.drawable.bg_foto_historico)
                    .error(R.drawable.bg_foto_historico)
                    .fallback(R.drawable.bg_foto_historico)
                    .build());

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
        }
    }
}
