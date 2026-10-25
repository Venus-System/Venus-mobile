package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

/**
 * De onde vem a nota pessoal, para a linha do cartao "Qualidade 74 · combina 71%".
 * A Venus-Classificacao soma ate 35 pontos de qualidade do produto e ate 65 de
 * combinacao com o perfil; alergia e bloqueio depois limitam a nota. A analise
 * salva (GET) nao traz os pontos, entao a conta e refeita aqui com a nota geral
 * do produto, que e a mesma nota de qualidade.
 */
public final class ContaDaNota {

    static final double PESO_QUALIDADE = 0.35;
    static final double PESO_PERFIL = 0.65;
    // Arredondamentos da API somam no maximo 1 ponto; abaixo disso foi trava.
    private static final double FOLGA = 1.5;

    @Nullable
    public final Integer qualidade;
    /** Porcentagem de combinacao com o perfil, ja arredondada; null quando nao entrou na conta. */
    @Nullable
    public final Integer combinacao;
    /** A nota final ficou abaixo da soma: uma alergia ou um bloqueio a derrubou. */
    public final boolean limitada;

    private ContaDaNota(@Nullable Integer qualidade, @Nullable Integer combinacao,
                        boolean limitada) {
        this.qualidade = qualidade;
        this.combinacao = combinacao;
        this.limitada = limitada;
    }

    public static ContaDaNota de(@Nullable Integer qualidade, @Nullable Double combinacao,
                                 int notaFinal) {
        Integer porcentagem = combinacao == null ? null : (int) Math.round(combinacao);
        boolean limitada = false;
        if (qualidade != null) {
            double esperada = combinacao == null
                    ? qualidade
                    : PESO_QUALIDADE * qualidade + PESO_PERFIL * combinacao;
            limitada = notaFinal < esperada - FOLGA;
        }
        return new ContaDaNota(qualidade, porcentagem, limitada);
    }

    public boolean vazia() {
        return qualidade == null && combinacao == null;
    }
}
