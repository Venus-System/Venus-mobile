package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.LinhaHistorico;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.view.util.ProdutoImagemLoader;

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
            ((ProdutoViewHolder) holder).preencher(
                    linha.getProduto(), linha.getStatus(), aoClicar);
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
        private final TextView status;

        ProdutoViewHolder(@NonNull View item) {
            super(item);
            imagem = item.findViewById(R.id.imgProduto);
            nome = item.findViewById(R.id.textNome);
            marca = item.findViewById(R.id.textMarca);
            status = item.findViewById(R.id.textStatus);
        }

        void preencher(Produto produto, String scanStatus, AoClicar aoClicar) {
            nome.setText(produto.getName());
            String brandName = produto.getBrandName();
            marca.setText(brandName == null ? "" : brandName);
            marca.setVisibility(brandName == null || brandName.trim().isEmpty()
                    ? View.GONE : View.VISIBLE);
            if (status != null) {
                String statusText = textoStatus(scanStatus);
                status.setText(statusText);
                status.setVisibility(statusText.isEmpty() ? View.GONE : View.VISIBLE);
            }

            ProdutoImagemLoader.carregar(
                    itemView.getContext(), imagem, produto, R.drawable.bg_foto_historico);

            boolean abreDetalhe = produto.getId() != null && produto.getId() > 0;
            itemView.setOnClickListener(abreDetalhe
                    ? v -> aoClicar.noProduto(produto) : null);
            itemView.setClickable(abreDetalhe);
            itemView.setFocusable(abreDetalhe);
        }

        private String textoStatus(String status) {
            if (status == null) {
                return "";
            }
            switch (status) {
                case "PENDING_REVIEW":
                case "IN_REVIEW":
                    return itemView.getContext().getString(R.string.historico_em_analise);
                case "APPROVED":
                case "SYNCED":
                    return itemView.getContext().getString(R.string.historico_aprovado);
                case "REJECTED":
                    return itemView.getContext().getString(R.string.historico_recusado);
                case "SYNC_FAILED":
                    return itemView.getContext().getString(R.string.historico_aguardando_sincronizacao);
                default:
                    return "";
            }
        }
    }
}
