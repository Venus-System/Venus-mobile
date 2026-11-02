package com.venussystem.venusmobile.domain.lembrete;

import java.time.Instant;
import java.time.ZoneId;

public final class AgendaQuestionario {

    public static final int HORA = 10;

    private static final int[] DIAS_ENTRE_LEMBRETES = {1, 3, 7};

    private AgendaQuestionario() {
    }

    public static int total() {
        return DIAS_ENTRE_LEMBRETES.length;
    }

    public static long proximo(int enviados, long ancoraMs, ZoneId zona) {
        if (enviados < 0 || enviados >= DIAS_ENTRE_LEMBRETES.length) {
            return -1L;
        }
        return Instant.ofEpochMilli(ancoraMs).atZone(zona).toLocalDate()
                .plusDays(DIAS_ENTRE_LEMBRETES[enviados])
                .atTime(HORA, 0)
                .atZone(zona)
                .toInstant()
                .toEpochMilli();
    }
}
