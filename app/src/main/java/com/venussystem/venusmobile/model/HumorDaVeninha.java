package com.venussystem.venusmobile.model;

import androidx.annotation.Nullable;

/**
 * A cara da Veninha no chat, com as mesmas regras da web: pensando enquanto a
 * resposta nao chega, confusa quando a pergunta falhou, chorando quando a API
 * diz que a mensagem ofendeu (expressao "magoada") e feliz no resto.
 */
public enum HumorDaVeninha {
    FELIZ,
    CHORANDO,
    CONFUSA,
    PENSANDO;

    private static final String MAGOADA = "magoada";

    /**
     * A cara para a expressao que a API devolveu. Valor fora da lista (um SDK
     * com uma cara nova) vira feliz, como a API ja faz com o que nao conhece.
     */
    public static HumorDaVeninha daExpressao(@Nullable String expressao) {
        return MAGOADA.equals(expressao) ? CHORANDO : FELIZ;
    }
}
