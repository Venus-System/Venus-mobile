package com.venussystem.venusmobile.domain.lembrete;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class AgendaQuestionarioTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static long instante(int ano, int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(ano, mes, dia, hora, minuto).atZone(SAO_PAULO).toInstant().toEpochMilli();
    }

    @Test
    public void saiTresLembretes() {
        assertEquals(3, AgendaQuestionario.total());
    }

    @Test
    public void primeiroLembrete_umDiaDepoisAs10h() {
        long escolheuDepois = instante(2026, 10, 17, 23, 40);

        long proximo = AgendaQuestionario.proximo(0, escolheuDepois, SAO_PAULO);

        assertEquals(instante(2026, 10, 18, 10, 0), proximo);
    }

    @Test
    public void segundoLembrete_tresDiasDepoisDoPrimeiro() {
        long saiuOPrimeiro = instante(2026, 10, 18, 10, 0);

        long proximo = AgendaQuestionario.proximo(1, saiuOPrimeiro, SAO_PAULO);

        assertEquals(instante(2026, 10, 21, 10, 0), proximo);
    }

    @Test
    public void terceiroLembrete_seteDiasDepoisDoSegundo() {
        long saiuOSegundo = instante(2026, 10, 21, 10, 5);

        long proximo = AgendaQuestionario.proximo(2, saiuOSegundo, SAO_PAULO);

        assertEquals(instante(2026, 10, 28, 10, 0), proximo);
    }

    @Test
    public void depoisDoTerceiro_naoHaMais() {
        assertEquals(-1L, AgendaQuestionario.proximo(3, instante(2026, 10, 28, 10, 0), SAO_PAULO));
        assertEquals(-1L, AgendaQuestionario.proximo(-1, instante(2026, 10, 28, 10, 0), SAO_PAULO));
    }

    @Test
    public void viradaDeMes_naoQuebra() {
        long proximo = AgendaQuestionario.proximo(2, instante(2026, 10, 28, 10, 0), SAO_PAULO);

        assertEquals(instante(2026, 11, 4, 10, 0), proximo);
    }
}
