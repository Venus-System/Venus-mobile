package com.venussystem.venusmobile.view.util;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.FaixaNota;

public final class NotaProdutoUtil {

    private NotaProdutoUtil() {
    }

    @ColorRes
    public static int corPara(@Nullable Integer nota) {
        FaixaNota faixa = FaixaNota.de(nota);
        if (faixa == null) {
            return R.color.cinza_descricao;
        }
        switch (faixa) {
            case BOA:
                return R.color.nota_boa;
            case MEDIA:
                return R.color.nota_media;
            default:
                return R.color.nota_ruim;
        }
    }

    @ColorRes
    public static int textoPara(@Nullable Integer nota) {
        FaixaNota faixa = FaixaNota.de(nota);
        if (faixa == null) {
            return R.color.cinza_descricao;
        }
        switch (faixa) {
            case BOA:
                return R.color.nota_boa_texto;
            case MEDIA:
                return R.color.nota_media_texto;
            default:
                return R.color.nota_ruim_texto;
        }
    }

    @ColorRes
    public static int fundoPara(@Nullable Integer nota) {
        FaixaNota faixa = FaixaNota.de(nota);
        if (faixa == null) {
            return R.color.cinza_fechar;
        }
        switch (faixa) {
            case BOA:
                return R.color.nota_boa_fundo;
            case MEDIA:
                return R.color.nota_media_fundo;
            default:
                return R.color.nota_ruim_fundo;
        }
    }
}
