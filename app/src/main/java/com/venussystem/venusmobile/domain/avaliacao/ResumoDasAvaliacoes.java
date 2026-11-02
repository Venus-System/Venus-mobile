package com.venussystem.venusmobile.domain.avaliacao;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.model.Avaliacao;

import java.util.List;

public final class ResumoDasAvaliacoes {

    public static final int ESTRELAS = 5;

    @Nullable private final Double media;
    private final int total;
    private final int[] porEstrela;

    private ResumoDasAvaliacoes(@Nullable Double media, int total, int[] porEstrela) {
        this.media = media;
        this.total = total;
        this.porEstrela = porEstrela;
    }

    public static ResumoDasAvaliacoes de(List<Avaliacao> avaliacoes) {
        int[] porEstrela = new int[ESTRELAS];
        double soma = 0;
        for (Avaliacao avaliacao : avaliacoes) {
            porEstrela[estrelas(avaliacao.getNota()) - 1]++;
            soma += avaliacao.getNota();
        }
        int total = avaliacoes.size();
        Double media = total == 0 ? null : Math.round(soma / total * 10) / 10.0;
        return new ResumoDasAvaliacoes(media, total, porEstrela);
    }

    public static int estrelas(double nota) {
        return (int) Math.min(Math.max(Math.round(nota), 1), ESTRELAS);
    }

    @Nullable
    public Double getMedia() {
        return media;
    }

    public int getTotal() {
        return total;
    }

    public int comEstrelas(int estrelas) {
        return porEstrela[estrelas - 1];
    }
}
