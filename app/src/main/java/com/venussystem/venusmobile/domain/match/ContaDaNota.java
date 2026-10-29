package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

public final class ContaDaNota {
    private static final double FOLGA = 1.5;

    @Nullable
    public final Integer qualidade;
    @Nullable
    public final Double pontosQualidade;
    @Nullable
    public final Double pontosPerfil;
    public final boolean limitada;

    private ContaDaNota(@Nullable Integer qualidade, @Nullable Double pontosQualidade,
                        @Nullable Double pontosPerfil, boolean limitada) {
        this.qualidade = qualidade;
        this.pontosQualidade = pontosQualidade;
        this.pontosPerfil = pontosPerfil;
        this.limitada = limitada;
    }

    public static ContaDaNota de(@Nullable Integer qualidade, @Nullable Double pontosQualidade,
                                 @Nullable Double pontosPerfil, int notaFinal) {
        Double soma = null;
        if (pontosQualidade != null && pontosPerfil != null) {
            soma = pontosQualidade + pontosPerfil;
        } else if (pontosPerfil == null && qualidade != null) {
            soma = qualidade.doubleValue();
        }
        boolean limitada = soma != null && notaFinal < soma - FOLGA;
        return new ContaDaNota(qualidade, pontosQualidade, pontosPerfil, limitada);
    }

    public boolean vazia() {
        return pontosQualidade == null && pontosPerfil == null && qualidade == null;
    }
}
