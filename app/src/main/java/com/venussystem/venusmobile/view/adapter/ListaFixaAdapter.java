package com.venussystem.venusmobile.view.adapter;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.view.ProdutosCurtidosActivity;
import com.venussystem.venusmobile.view.ProdutosEmAnaliseActivity;

/**
 * As listas fixas do topo da aba Listas, com o mesmo cartao das listas do
 * usuario: "Produtos curtidos" (o coracao da tela do produto) e "Produtos em
 * analise". So existem na tela: nao sao Colecao do ColecaoRepository, entao
 * nao sobem para /api/user-lists, nao aparecem no "salvar na lista" do
 * produto e nao podem ser arrastadas nem excluidas. Cada uma abre a sua tela.
 */
public class ListaFixaAdapter extends RecyclerView.Adapter<ListaFixaAdapter.CartaoViewHolder> {

    private static final class Cartao {
        @StringRes final int nome;
        @DrawableRes final int capa;
        final Class<? extends Activity> tela;

        Cartao(@StringRes int nome, @DrawableRes int capa, Class<? extends Activity> tela) {
            this.nome = nome;
            this.capa = capa;
            this.tela = tela;
        }
    }

    private static final Cartao[] CARTOES = {
            new Cartao(R.string.curtidos_titulo, R.drawable.capa_favoritos,
                    ProdutosCurtidosActivity.class),
            new Cartao(R.string.analise_titulo, R.drawable.capa_escaneados,
                    ProdutosEmAnaliseActivity.class),
    };

    @NonNull
    @Override
    public CartaoViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_colecao, pai, false);
        return new CartaoViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull CartaoViewHolder holder, int posicao) {
        holder.preencher(CARTOES[posicao]);
    }

    @Override
    public int getItemCount() {
        return CARTOES.length;
    }

    static class CartaoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView capa;
        private final TextView nome;

        CartaoViewHolder(@NonNull View item) {
            super(item);
            capa = item.findViewById(R.id.imgCapa);
            nome = item.findViewById(R.id.textNomeLista);
        }

        void preencher(Cartao cartao) {
            nome.setText(cartao.nome);
            capa.setImageResource(cartao.capa);
            itemView.setOnClickListener(v -> v.getContext().startActivity(
                    new Intent(v.getContext(), cartao.tela)));
        }
    }
}
