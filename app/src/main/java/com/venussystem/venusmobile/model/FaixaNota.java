package com.venussystem.venusmobile.model;

import androidx.annotation.Nullable;

/**
 * Faixa da nota geral: boa a partir de 70, media a partir de 40, ruim abaixo
 * disso. E a unica definicao desses limites - o selo colorido
 * (NotaProdutoUtil) e o filtro usam esta mesma.
 */
public enum FaixaNota {
    BOA(70),
    MEDIA(40),
    RUIM(0);

    private final int minimo;

    FaixaNota(int minimo) {
        this.minimo = minimo;
    }

    /** Null quando o produto nao tem nota. */
    @Nullable
    public static FaixaNota de(@Nullable Integer nota) {
        if (nota == null) {
            return null;
        }
        if (nota >= BOA.minimo) {
            return BOA;
        }
        if (nota >= MEDIA.minimo) {
            return MEDIA;
        }
        return RUIM;
    }
}
