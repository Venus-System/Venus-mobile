package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.request.ImageRequest;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.view.util.SituacaoAnaliseUtil;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ProdutoEmAnaliseAdapter
        extends RecyclerView.Adapter<ProdutoEmAnaliseAdapter.ProdutoViewHolder> {

    public interface AoClicar {
        void noProduto(ProdutoEmAnalise produto);
    }

    public static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final List<ProdutoEmAnalise> produtos = new ArrayList<>();
    private final AoClicar aoClicar;

    public ProdutoEmAnaliseAdapter(AoClicar aoClicar) {
        this.aoClicar = aoClicar;
    }

    public void atualizar(List<ProdutoEmAnalise> novos) {
        produtos.clear();
        if (novos != null) {
            produtos.addAll(novos);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProdutoViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_produto_em_analise, pai, false);
        return new ProdutoViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull ProdutoViewHolder holder, int posicao) {
        holder.preencher(produtos.get(posicao), aoClicar);
    }

    @Override
    public int getItemCount() {
        return produtos.size();
    }

    static class ProdutoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView frente;
        private final TextView nome;
        private final TextView marca;
        private final TextView enviadoEm;
        private final TextView situacao;

        ProdutoViewHolder(@NonNull View item) {
            super(item);
            frente = item.findViewById(R.id.imgFrente);
            nome = item.findViewById(R.id.textNome);
            marca = item.findViewById(R.id.textMarca);
            enviadoEm = item.findViewById(R.id.textEnviadoEm);
            situacao = item.findViewById(R.id.textSituacao);
        }

        void preencher(ProdutoEmAnalise produto, AoClicar aoClicar) {
            if (produto.getNome() != null) {
                nome.setText(produto.getNome());
            } else {
                nome.setText(R.string.analise_sem_nome);
            }
            marca.setText(produto.getMarca());
            marca.setVisibility(produto.getMarca() == null ? View.GONE : View.VISIBLE);

            if (produto.getEnviadoEm() != null) {
                enviadoEm.setText(itemView.getContext().getString(R.string.analise_enviado_em,
                        produto.getEnviadoEm().format(FORMATO_DATA)));
                enviadoEm.setVisibility(View.VISIBLE);
            } else {
                enviadoEm.setVisibility(View.GONE);
            }

            SituacaoAnaliseUtil.aplicarSelo(situacao, produto.getSituacao());

            Coil.imageLoader(itemView.getContext()).enqueue(
                    new ImageRequest.Builder(itemView.getContext())
                            .data(produto.getFotoFrente())
                            .target(frente)
                            .placeholder(R.drawable.bg_foto_produto)
                            .error(R.drawable.bg_foto_produto)
                            .fallback(R.drawable.bg_foto_produto)
                            .build());

            itemView.setOnClickListener(v -> aoClicar.noProduto(produto));
        }
    }
}
