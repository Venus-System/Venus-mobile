package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;

import java.util.ArrayList;
import java.util.List;

public class ConversaAdapter extends RecyclerView.Adapter<ConversaAdapter.MensagemViewHolder> {

    private final List<Mensagem> mensagens = new ArrayList<>();

    public void adicionar(Mensagem mensagem) {
        mensagens.add(mensagem);
        notifyItemInserted(mensagens.size() - 1);
    }

    @Override
    public int getItemViewType(int posicao) {
        return mensagens.get(posicao).getAutor();
    }

    @NonNull
    @Override
    public MensagemViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        int layout = tipo == Mensagem.DO_USUARIO
                ? R.layout.item_mensagem_usuario
                : R.layout.item_mensagem_assistente;
        return new MensagemViewHolder(
                LayoutInflater.from(pai.getContext()).inflate(layout, pai, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MensagemViewHolder holder, int posicao) {
        holder.preencher(mensagens.get(posicao));
    }

    @Override
    public int getItemCount() {
        return mensagens.size();
    }

    static class MensagemViewHolder extends RecyclerView.ViewHolder {
        private final TextView texto;

        MensagemViewHolder(@NonNull View item) {
            super(item);
            texto = item.findViewById(R.id.textMensagem);
        }

        void preencher(Mensagem mensagem) {
            texto.setText(mensagem.getTexto());
        }
    }
}
