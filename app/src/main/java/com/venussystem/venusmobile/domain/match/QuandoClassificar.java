package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Decide se a analise salva na Venus-Classificacao ainda vale. Cada POST grava
 * uma analise nova no banco, entao o app le a salva e so calcula de novo
 * quando o perfil mudou depois dela.
 */
public final class QuandoClassificar {

    private QuandoClassificar() {
    }

    /**
     * @param calculadaEm calculatedAt da analise salva (ISO-8601 com fuso)
     * @param perfilAlteradoEm ultima mudanca de resposta no aparelho, em ms
     * @param perfilPendente true quando essa mudanca ainda nao chegou na API
     */
    public static boolean precisaRecalcular(@Nullable String calculadaEm, long perfilAlteradoEm,
                                            boolean perfilPendente) {
        // A Classificacao le o perfil do banco: enquanto a mudanca nao subir,
        // calcular de novo daria a mesma nota e so gravaria outra linha.
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
