package com.venussystem.venusmobile.domain.lembrete;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Quando lembrar a pessoa de terminar o questionario: tres lembretes, 1, 3 e 7
 * dias depois do anterior (o primeiro conta de quando ela escolheu "responder
 * depois"), sempre as 10h, para nao chegar de madrugada. Depois disso para: o
 * lembrete nao pode virar insistencia.
 */
public final class AgendaQuestionario {

    public static final int HORA = 10;

    private static final int[] DIAS_ENTRE_LEMBRETES = {1, 3, 7};

    private AgendaQuestionario() {
    }

    /** Quantos lembretes no maximo. */
    public static int total() {
        return DIAS_ENTRE_LEMBRETES.length;
    }

    /**
     * @param enviados quantos lembretes ja sairam
     * @param ancoraMs quando saiu o ultimo (ou quando a pessoa ligou o lembrete, se
     *                 ainda nao saiu nenhum)
     * @return o instante do proximo lembrete, ou -1 se ja sairam todos
     */
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
