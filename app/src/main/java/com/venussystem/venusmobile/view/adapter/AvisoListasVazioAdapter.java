package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;

/**
 * O aviso "voce ainda nao tem listas" como um item da propria lista, depois
 * do separador "Listas criadas": solto por cima da RecyclerView, ele cobriria
 * as listas fixas. Tem 1 item quando visivel e nenhum quando nao.
 */
public class AvisoListasVazioAdapter extends RecyclerView.Adapter<AvisoListasVazioAdapter.AvisoViewHolder> {

    private boolean visivel;

    public void mostrar(boolean mostrar) {
        if (mostrar == visivel) {
            return;
        }
        visivel = mostrar;
        if (mostrar) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    @NonNull
    @Override
    public AvisoViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View item = LayoutInflater.from(pai.getContext())
                .inflate(R.layout.item_aviso_listas_vazio, pai, false);
        return new AvisoViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull AvisoViewHolder holder, int posicao) {
        // O texto e fixo, ja vem do layout.
    }

    @Override
    public int getItemCount() {
        return visivel ? 1 : 0;
    }

    static class AvisoViewHolder extends RecyclerView.ViewHolder {
        AvisoViewHolder(@NonNull View item) {
            super(item);
        }
    }
}
