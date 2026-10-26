package com.venussystem.venusmobile.view.util;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.venussystem.venusmobile.R;

/**
 * Aviso curto na base da tela, no lugar do Toast: uma barra por cima do
 * conteudo que some sozinha, sem botao. sucesso() e verde com o check (produto
 * salvo numa lista); neutra() e a mesma barra em cinza escuro, para o que nao
 * e um "deu certo" (produto tirado da lista).
 */
public final class MensagemRapida {

    private MensagemRapida() {
    }

    @NonNull
    public static Snackbar sucesso(@NonNull View ancora, @NonNull CharSequence texto) {
        return mostrar(ancora, texto, R.color.verde_sucesso, R.drawable.ic_check);
    }

    @NonNull
    public static Snackbar neutra(@NonNull View ancora, @NonNull CharSequence texto) {
        return mostrar(ancora, texto, R.color.preto_texto, 0);
    }

    private static Snackbar mostrar(View ancora, CharSequence texto,
                                    @ColorRes int fundo, @DrawableRes int icone) {
        Context contexto = ancora.getContext();
        Snackbar barra = Snackbar.make(ancora, texto, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(ContextCompat.getColor(contexto, fundo))
                .setTextColor(ContextCompat.getColor(contexto, R.color.white));

        if (icone != 0) {
            TextView campo = barra.getView()
                    .findViewById(com.google.android.material.R.id.snackbar_text);
            campo.setCompoundDrawablesRelativeWithIntrinsicBounds(icone, 0, 0, 0);
            campo.setCompoundDrawablePadding(
                    contexto.getResources().getDimensionPixelSize(R.dimen.espaco_pequeno));
        }

        barra.show();
        return barra;
    }
}
