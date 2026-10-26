package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

/**
 * De onde vem a nota pessoal, para a linha do cartao "A nota soma duas partes:
 * 25,9 de 35 pela qualidade do produto e 15,3 de 65 por combinar com o seu perfil".
 * Os numeros sao os da Venus-Classificacao (breakdown do POST, guardado para o
 * GET); o app nao refaz a conta. Sem perguntas do perfil, a nota e a propria
 * qualidade. Alergia e bloqueio depois limitam a nota.
 */
public final class ContaDaNota {

    // Arredondamentos da API somam no maximo 1 ponto; abaixo disso foi trava.
    private static final double FOLGA = 1.5;

    /** Nota de qualidade de 0 a 100 (qualityScore). */
    @Nullable
    public final Integer qualidade;
    /** Ate 35 pontos (qualityPoints). */
    @Nullable
    public final Double pontosQualidade;
    /** Ate 65 pontos (profilePoints); null quando o perfil nao entrou na conta. */
    @Nullable
    public final Double pontosPerfil;
    /** A nota final ficou abaixo da soma: uma alergia ou um bloqueio a derrubou. */
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
