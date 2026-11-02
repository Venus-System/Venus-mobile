package com.venussystem.venusmobile.model;

import androidx.annotation.Nullable;

public enum HumorDaVeninha {
    FELIZ,
    CHORANDO,
    CONFUSA,
    PENSANDO;

    private static final String MAGOADA = "magoada";

    public static HumorDaVeninha daExpressao(@Nullable String expressao) {
        return MAGOADA.equals(expressao) ? CHORANDO : FELIZ;
    }
}
