package com.venussystem.venusmobile.model;

import androidx.annotation.Nullable;

public enum FaixaNota {
    BOA(70),
    MEDIA(40),
    RUIM(0);

    private final int minimo;

    FaixaNota(int minimo) {
        this.minimo = minimo;
    }

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
