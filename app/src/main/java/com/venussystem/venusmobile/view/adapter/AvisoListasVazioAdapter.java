package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;

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
