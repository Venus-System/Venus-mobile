package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public final class QuandoClassificar {

    private QuandoClassificar() {
    }

    public static boolean precisaRecalcular(@Nullable String calculadaEm, long perfilAlteradoEm,
                                            boolean perfilPendente) {
        if (perfilPendente) return false;
        if (calculadaEm == null || calculadaEm.trim().isEmpty()) return true;
        try {
            long calculada = OffsetDateTime.parse(calculadaEm).toInstant().toEpochMilli();
            return calculada < perfilAlteradoEm;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
