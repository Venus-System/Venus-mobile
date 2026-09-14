package com.venussystem.venusmobile.view.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.LinhaHistorico;
import com.venussystem.venusmobile.model.Produto;

import java.util.ArrayList;
import java.util.List;

public class HistoricoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface AoClicar {
        void noProduto(Produto produto);
    }

    private final List<LinhaHistorico> linhas = new ArrayList<>();
    private final AoClicar aoClicar;

    public HistoricoAdapter(AoClicar aoClicar) {
        this.aoClicar = aoClicar;
    }

    public void atualizar(List<LinhaHistorico> novas) {
        linhas.clear();
        if (novas != null) {
            linhas.addAll(novas);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int posicao) {
        return linhas.get(posicao).getTipo();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        LayoutInflater inflater = LayoutInflater.from(pai.getContext());
        if (tipo == LinhaHistorico.TIPO_DATA) {
            return new DataViewHolder(
                    inflater.inflate(R.layout.item_historico_data, pai, false));
        }
        return new ProdutoViewHolder(
                inflater.inflate(R.layout.item_historico_produto, pai, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int posicao) {
        LinhaHistorico linha = linhas.get(posicao);
        if (holder instanceof DataViewHolder) {
            ((DataViewHolder) holder).preencher(linha.getData());
        } else {
            ((ProdutoViewHolder) holder).preencher(linha.getProduto(), aoClicar);
        }
    }

    @Override
    public int getItemCount() {
        return linhas.size();
    }

    static class DataViewHolder extends RecyclerView.ViewHolder {
        private final TextView data;

        DataViewHolder(@NonNull View item) {
            super(item);
            data = item.findViewById(R.id.textData);
        }

        void preencher(String texto) {
            data.setText(texto);
        }
    }

    static class ProdutoViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imagem;
        private final TextView nome;
        private final TextView marca;
        private final TextView nota;

        ProdutoViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            nome = item.findViewById(R.id.textNome);
            marca = item.findViewById(R.id.textMarca);
            nota = item.findViewById(R.id.textNota);
        }

        void preencher(Produto produto, AoClicar aoClicar) {
            nome.setText(produto.getName());
            marca.setText(produto.getBrandName());
            mostrarNota(produto.getOverallScore());

            ImageLoader carregador = Coil.imageLoader(itemView.getContext());
            carregador.enqueue(new ImageRequest.Builder(itemView.getContext())
                    .data(produto.getImageUrl())
                    .target(imagem)
                    .placeholder(R.drawable.bg_foto_historico)
                    .error(R.drawable.bg_foto_historico)
                    .build());

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
        }

        /**
         * A pilula usa duas cores da mesma faixa: um fundo bem claro e o numero
         * na versao forte. O fundo vem do tint porque o drawable e compartilhado
         * entre todos os itens - pintar o proprio drawable vazaria a cor de um
         * item para os outros quando a linha fosse reciclada.
         */
        private void mostrarNota(Integer valor) {
            if (valor == null) {
                aplicarCores(R.color.cinza_fechar, R.color.cinza_descricao);
                nota.setText(R.string.nota_indisponivel);
                return;
            }

            if (valor >= 70) {
                aplicarCores(R.color.nota_boa_fundo, R.color.nota_boa);
            } else if (valor >= 40) {
                aplicarCores(R.color.nota_media_fundo, R.color.nota_media);
            } else {
                aplicarCores(R.color.nota_ruim_fundo, R.color.nota_ruim);
            }
            nota.setText(String.valueOf(valor));
        }

        private void aplicarCores(@ColorRes int fundo, @ColorRes int texto) {
            nota.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), fundo)));
            nota.setTextColor(ContextCompat.getColor(itemView.getContext(), texto));
        }
    }
}
