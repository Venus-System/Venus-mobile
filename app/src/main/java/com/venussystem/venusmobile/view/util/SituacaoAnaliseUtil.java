package com.venussystem.venusmobile.view.util;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;

/**
 * Texto e cor do selo de cada situacao de um produto em analise. As cores sao
 * as mesmas das notas: verde aprovado, amarelo em analise, rosa recusado, e
 * cinza enquanto ninguem pegou para conferir.
 */
public final class SituacaoAnaliseUtil {

    private SituacaoAnaliseUtil() {
    }

    /** Escreve a situacao no selo (bg_selo_situacao) e pinta com a cor dela. */
    public static void aplicarSelo(TextView selo, Situacao situacao) {
        selo.setText(rotulo(situacao));
        selo.setTextColor(ContextCompat.getColor(selo.getContext(), corTexto(situacao)));
        selo.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(selo.getContext(), corFundo(situacao))));
    }

    @StringRes
    public static int rotulo(Situacao situacao) {
        switch (situacao) {
            case EM_ANALISE:
                return R.string.situacao_em_analise;
            case APROVADO:
                return R.string.situacao_aprovado;
            case RECUSADO:
                return R.string.situacao_recusado;
            default:
                return R.string.situacao_aguardando;
        }
    }

    @ColorRes
    public static int corTexto(Situacao situacao) {
        switch (situacao) {
            case EM_ANALISE:
                return R.color.nota_media;
            case APROVADO:
                return R.color.nota_boa;
            case RECUSADO:
                return R.color.nota_ruim;
            default:
                return R.color.cinza_descricao;
        }
    }

    @ColorRes
    public static int corFundo(Situacao situacao) {
        switch (situacao) {
            case EM_ANALISE:
                return R.color.nota_media_fundo;
            case APROVADO:
                return R.color.nota_boa_fundo;
            case RECUSADO:
                return R.color.nota_ruim_fundo;
            default:
                return R.color.cinza_fechar;
        }
    }

    /**
     * A frase do detalhe. Aprovado sem produtoId e o caso em que a equipe
     * aprovou mas o produto ainda nao entrou no catalogo (ou a entrada falhou
     * e vai ser tentada de novo).
     */
    @StringRes
    public static int explicacao(Situacao situacao, @Nullable Long produtoId) {
        switch (situacao) {
            case EM_ANALISE:
                return R.string.situacao_em_analise_explicacao;
            case APROVADO:
                return produtoId != null
                        ? R.string.situacao_aprovado_explicacao
                        : R.string.situacao_aprovado_aguardando_catalogo_explicacao;
            case RECUSADO:
                return R.string.situacao_recusado_explicacao;
            default:
                return R.string.situacao_aguardando_explicacao;
        }
    }
}
