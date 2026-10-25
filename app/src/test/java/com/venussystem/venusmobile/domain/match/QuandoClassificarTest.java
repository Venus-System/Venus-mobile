package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.OffsetDateTime;

public class QuandoClassificarTest {

    private static final String CALCULADA = "2026-10-22T10:00:00Z";
    private static final long ANTES = OffsetDateTime.parse("2026-10-21T10:00:00Z")
            .toInstant().toEpochMilli();
    private static final long DEPOIS = OffsetDateTime.parse("2026-10-22T11:00:00Z")
            .toInstant().toEpochMilli();

    @Test
    public void analiseMaisNovaQueOPerfilVale() {
        assertFalse(QuandoClassificar.precisaRecalcular(CALCULADA, ANTES, false));
    }

    @Test
    public void perfilMudouDepoisDaAnalisePedeOutra() {
        assertTrue(QuandoClassificar.precisaRecalcular(CALCULADA, DEPOIS, false));
    }

    @Test
    public void perfilAindaNaoEnviadoNaoRecalcula() {
        assertFalse(QuandoClassificar.precisaRecalcular(CALCULADA, DEPOIS, true));
    }

    @Test
    public void semDataPedeCalculo() {
        assertTrue(QuandoClassificar.precisaRecalcular(null, ANTES, false));
    }

    @Test
    public void dataComFracaoEFusoTambemServe() {
        assertFalse(QuandoClassificar.precisaRecalcular("2026-10-22T07:00:00.123456-03:00",
                ANTES, false));
    }

    @Test
    public void dataQueNaoDaParaLerFicaComASalva() {
        assertFalse(QuandoClassificar.precisaRecalcular("ontem", DEPOIS, false));
    }
}
