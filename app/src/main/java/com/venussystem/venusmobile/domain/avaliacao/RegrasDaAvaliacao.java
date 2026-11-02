package com.venussystem.venusmobile.domain.avaliacao;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class RegrasDaAvaliacao {

    public static final int MAXIMO_DO_TITULO = 100;
    public static final int MAXIMO_DO_COMENTARIO = 500;
    public static final int MAXIMO_DA_DENUNCIA = 500;
    public static final int DIAS_RELATIVOS = 7;

    public enum Problema {
        SEM_NOTA, SEM_TITULO, TITULO_LONGO, SEM_COMENTARIO, COMENTARIO_LONGO
    }

    private RegrasDaAvaliacao() {
    }

    @Nullable
    public static Problema conferir(int nota, String titulo, String comentario) {
        if (nota < 1 || nota > 5) {
            return Problema.SEM_NOTA;
        }
        String tituloLimpo = titulo.trim();
        if (tituloLimpo.isEmpty()) {
            return Problema.SEM_TITULO;
        }
        if (tituloLimpo.length() > MAXIMO_DO_TITULO) {
            return Problema.TITULO_LONGO;
        }
        String comentarioLimpo = comentario.trim();
        if (comentarioLimpo.isEmpty()) {
            return Problema.SEM_COMENTARIO;
        }
        if (comentarioLimpo.length() > MAXIMO_DO_COMENTARIO) {
            return Problema.COMENTARIO_LONGO;
        }
        return null;
    }

    public static String motivoDaDenuncia(String rotulo, String comentario) {
        String limpo = comentario.trim();
        return limpo.isEmpty() ? rotulo : rotulo + ". " + limpo;
    }

    public static long diasDesde(LocalDate dia, LocalDate hoje) {
        return Math.max(ChronoUnit.DAYS.between(dia, hoje), 0);
    }
}
