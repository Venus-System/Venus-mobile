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
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Mensagem;

import java.util.ArrayList;
import java.util.List;

public class ConversaAdapter extends RecyclerView.Adapter<ConversaAdapter.MensagemViewHolder> {

    // "Digitando", "Digitando.", "Digitando.." e "Digitando...", em volta.
    private static final String PONTOS = "...";
    private static final long INTERVALO_PONTOS_MS = 400;

    private final List<Mensagem> mensagens = new ArrayList<>();

    /**
     * Troca a conversa inteira pela que o ViewModel publicou. Uma conversa tem
     * poucas dezenas de mensagens, entao redesenhar tudo nao pesa.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void definir(List<Mensagem> novas) {
        mensagens.clear();
        mensagens.addAll(novas);
        notifyDataSetChanged();
    }

    /** Quantos dos tres pontos do "Digitando" aparecem neste passo. */
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
        private final Runnable proximosPontos = this::andarPontos;
        private int passo;

        MensagemViewHolder(@NonNull View item) {
            super(item);
            texto = item.findViewById(R.id.textMensagem);
        }

        void preencher(Mensagem mensagem) {
            pararPontos();
            // Com as animacoes do sistema desligadas, fica o "Digitando…" parado.
            if (mensagem.isDigitando() && ValueAnimator.areAnimatorsEnabled()) {
                // O leitor de tela le a frase uma vez, e nao a cada ponto.
                texto.setContentDescription(mensagem.getTexto());
                passo = 0;
                mostrarPontos();
                texto.postDelayed(proximosPontos, INTERVALO_PONTOS_MS);
            } else {
                texto.setContentDescription(null);
                texto.setText(mensagem.getTexto());
            }
        }

        void pararPontos() {
            texto.removeCallbacks(proximosPontos);
        }

        private void andarPontos() {
            passo++;
            mostrarPontos();
            texto.postDelayed(proximosPontos, INTERVALO_PONTOS_MS);
        }

        private void mostrarPontos() {
            String base = texto.getContext().getString(R.string.assistente_digitando_sem_pontos);
            SpannableString frase = new SpannableString(base + PONTOS);
            // Os pontos que ainda nao apareceram ficam la, transparentes: assim a
            // bolha nao muda de largura a cada passo.
            int inicioEscondido = base.length() + pontosVisiveis(passo);
            if (inicioEscondido < frase.length()) {
                frase.setSpan(new ForegroundColorSpan(Color.TRANSPARENT),
                        inicioEscondido, frase.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            texto.setText(frase);
        }
    }
}
