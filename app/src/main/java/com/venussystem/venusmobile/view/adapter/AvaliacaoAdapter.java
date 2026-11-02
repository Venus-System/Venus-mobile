package com.venussystem.venusmobile.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.avaliacao.ResumoDasAvaliacoes;
import com.venussystem.venusmobile.model.Avaliacao;
import com.venussystem.venusmobile.view.componente.CartaoAvaliacao;
import com.venussystem.venusmobile.view.componente.ResumoAvaliacao;
import com.venussystem.venusmobile.view.componente.VotosDasAvaliacoes;

import java.util.ArrayList;
import java.util.List;

public class AvaliacaoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int CABECALHO = 0;
    private static final int AVALIACAO = 1;

    private final VotosDasAvaliacoes votos;
    private final CartaoAvaliacao.AoDenunciar aoDenunciar;
    private final Runnable aoEscrever;
    private final List<Avaliacao> avaliacoes = new ArrayList<>();
    private boolean truncada = false;

    public AvaliacaoAdapter(VotosDasAvaliacoes votos, CartaoAvaliacao.AoDenunciar aoDenunciar,
                            Runnable aoEscrever) {
        this.votos = votos;
        this.aoDenunciar = aoDenunciar;
        this.aoEscrever = aoEscrever;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void mostrar(List<Avaliacao> novas, boolean truncada) {
        avaliacoes.clear();
        avaliacoes.addAll(novas);
        this.truncada = truncada;
        notifyDataSetChanged();
    }

    public void atualizar(long avaliacaoId) {
        for (int i = 0; i < avaliacoes.size(); i++) {
            if (avaliacoes.get(i).getId() == avaliacaoId) {
                notifyItemChanged(i + 1);
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int posicao) {
        return posicao == 0 ? CABECALHO : AVALIACAO;
    }

    @Override
    public int getItemCount() {
        return avaliacoes.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        LayoutInflater inflater = LayoutInflater.from(pai.getContext());
        View view = inflater.inflate(tipo == CABECALHO
                ? R.layout.item_cabecalho_avaliacoes : R.layout.item_avaliacao, pai, false);
        return new RecyclerView.ViewHolder(view) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int posicao) {
        View view = holder.itemView;
        if (posicao > 0) {
            CartaoAvaliacao.preencher(view, avaliacoes.get(posicao - 1), votos, aoDenunciar);
            return;
        }
        ResumoAvaliacao.preencher(view.findViewById(R.id.blocoResumoAvaliacoes),
                ResumoDasAvaliacoes.de(avaliacoes), truncada);
        boolean jaAvaliou = false;
        for (Avaliacao avaliacao : avaliacoes) {
            if (avaliacao.isMinha()) {
                jaAvaliou = true;
                break;
            }
        }
        View escrever = view.findViewById(R.id.btnEscreverAvaliacao);
        escrever.setVisibility(jaAvaliou ? View.GONE : View.VISIBLE);
        escrever.setOnClickListener(v -> aoEscrever.run());
        view.findViewById(R.id.textJaAvaliou)
                .setVisibility(jaAvaliou ? View.VISIBLE : View.GONE);
    }
}
