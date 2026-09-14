package com.venussystem.venusmobile.view.util;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;

/** Banda de cor da nota (boa/media/ruim), usada nas telas que mostram o selo. */
public final class NotaProdutoUtil {

    private NotaProdutoUtil() {
    }

    @ColorRes
    public static int corPara(@Nullable Integer nota) {
        if (nota == null) {
            return R.color.cinza_descricao;
        }
        if (nota >= 70) {
            return R.color.nota_boa;
        }
        if (nota >= 40) {
            return R.color.nota_media;
        }
        return R.color.nota_ruim;
    }

    /** Fundo do selo circular - sempre a versao clara da mesma banda de corPara(). */
    @ColorRes
    public static int fundoPara(@Nullable Integer nota) {
        if (nota == null) {
            return R.color.cinza_fechar;
        }
        if (nota >= 70) {
            return R.color.nota_boa_fundo;
        }
        if (nota >= 40) {
            return R.color.nota_media_fundo;
        }
        return R.color.nota_ruim_fundo;
    }
}
