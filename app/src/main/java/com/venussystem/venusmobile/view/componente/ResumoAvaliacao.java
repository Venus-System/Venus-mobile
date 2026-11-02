package com.venussystem.venusmobile.view.componente;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.avaliacao.ResumoDasAvaliacoes;

public final class ResumoAvaliacao {

    private ResumoAvaliacao() {
    }

    public static void preencher(View bloco, ResumoDasAvaliacoes resumo, boolean truncada) {
        Context contexto = bloco.getContext();
        double media = resumo.getMedia() == null ? 0 : resumo.getMedia();

        ((TextView) bloco.findViewById(R.id.textMediaAvaliacoes))
                .setText(CartaoAvaliacao.nota(media));
        ((RatingBar) bloco.findViewById(R.id.estrelasMediaAvaliacoes)).setRating((float) media);
        ((TextView) bloco.findViewById(R.id.textTotalAvaliacoes)).setText(total(contexto,
                resumo.getTotal(), truncada));
        bloco.findViewById(R.id.blocoMediaAvaliacoes).setContentDescription(
                contexto.getString(R.string.avaliacoes_media_descricao, CartaoAvaliacao.nota(media))
                        + ", " + total(contexto, resumo.getTotal(), truncada));

        LinearLayout barras = bloco.findViewById(R.id.listaBarrasAvaliacoes);
        barras.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(contexto);
        for (int estrelas = ResumoDasAvaliacoes.ESTRELAS; estrelas >= 1; estrelas--) {
            int quantas = resumo.comEstrelas(estrelas);
            View linha = inflater.inflate(R.layout.item_barra_avaliacao, barras, false);
            ((TextView) linha.findViewById(R.id.textEstrelasBarra)).setText(estrelas + "★");
            ((TextView) linha.findViewById(R.id.textQuantosBarra)).setText(String.valueOf(quantas));
            LinearProgressIndicator barra = linha.findViewById(R.id.barraAvaliacao);
            barra.setProgress(resumo.getTotal() == 0 ? 0
                    : Math.round(quantas * 100f / resumo.getTotal()));
            linha.setContentDescription(contexto.getResources().getQuantityString(
                    R.plurals.avaliacoes_barra_descricao, quantas, estrelas, quantas));
            linha.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            for (int i = 0; i < ((LinearLayout) linha).getChildCount(); i++) {
                ((LinearLayout) linha).getChildAt(i)
                        .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            barras.addView(linha);
        }
    }

    public static String total(Context contexto, int total, boolean truncada) {
        if (truncada) {
            return contexto.getString(R.string.avaliacoes_total_truncado, total);
        }
        return contexto.getResources().getQuantityString(R.plurals.avaliacoes_total, total, total);
    }
}
