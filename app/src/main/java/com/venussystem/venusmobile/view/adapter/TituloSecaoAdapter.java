package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;

/**
 * Um separador da aba Listas ("Listas fixas", "Listas criadas") como item da
 * propria lista, entre os adapters do ConcatAdapter. Sempre 1 item.
 */
public class TituloSecaoAdapter extends RecyclerView.Adapter<TituloSecaoAdapter.TituloViewHolder> {

    @StringRes
    private final int titulo;

    public TituloSecaoAdapter(@StringRes int titulo) {
        this.titulo = titulo;
    }

    @NonNull
    @Override
    public TituloViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_titulo_secao_listas, pai, false);
        return new TituloViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull TituloViewHolder holder, int posicao) {
        holder.texto.setText(titulo);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class TituloViewHolder extends RecyclerView.ViewHolder {
        final TextView texto;

        TituloViewHolder(@NonNull View item) {
            super(item);
            texto = item.findViewById(R.id.textTituloSecao);
        }
    }
}
