package com.venussystem.venusmobile.view.adapter;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;

import java.util.ArrayList;
import java.util.List;

public class ConversaAdapter extends RecyclerView.Adapter<ConversaAdapter.MensagemViewHolder> {
    private static final String PONTOS = "...";
    private static final long INTERVALO_PONTOS_MS = 400;

    private final List<Mensagem> mensagens = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    public void definir(List<Mensagem> novas) {
        mensagens.clear();
        mensagens.addAll(novas);
        notifyDataSetChanged();
    }

    @VisibleForTesting
    static int pontosVisiveis(int passo) {
        return passo % (PONTOS.length() + 1);
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
    public void onViewRecycled(@NonNull MensagemViewHolder holder) {
        holder.pararPontos();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return mensagens.size();
    }

    static class MensagemViewHolder extends RecyclerView.ViewHolder {
        private final TextView texto;
        @Nullable
        private Runnable pontos;
        private int passo;

        MensagemViewHolder(@NonNull View item) {
            super(item);
            texto = item.findViewById(R.id.textMensagem);
        }

        void preencher(Mensagem mensagem) {
            pararPontos();
            if (mensagem.isDigitando() && ValueAnimator.areAnimatorsEnabled()) {
                texto.setContentDescription(mensagem.getTexto());
                passo = 0;
                mostrarPontos();
                Runnable proximos = new Runnable() {
                    @Override
                    public void run() {
                        if (pontos != this) {
                            return;
                        }
                        passo++;
                        mostrarPontos();
                        texto.postDelayed(this, INTERVALO_PONTOS_MS);
                    }
                };
                pontos = proximos;
                texto.postDelayed(proximos, INTERVALO_PONTOS_MS);
            } else {
                texto.setContentDescription(null);
                texto.setText(mensagem.getTexto());
            }
        }

        void pararPontos() {
            if (pontos != null) {
                texto.removeCallbacks(pontos);
                pontos = null;
            }
        }

        private void mostrarPontos() {
            String base = texto.getContext().getString(R.string.assistente_digitando_sem_pontos);
            SpannableString frase = new SpannableString(base + PONTOS);
            int inicioEscondido = base.length() + pontosVisiveis(passo);
            if (inicioEscondido < frase.length()) {
                frase.setSpan(new ForegroundColorSpan(Color.TRANSPARENT),
                        inicioEscondido, frase.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            texto.setText(frase);
        }
    }
}
