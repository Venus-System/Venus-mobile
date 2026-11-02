package com.venussystem.venusmobile.domain.avaliacao;

import com.venussystem.venusmobile.model.Avaliacao;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RegrasDaAvaliacaoTest {

    private static String repetir(int vezes) {
        return String.join("", Collections.nCopies(vezes, "a"));
    }

    private static Avaliacao comNota(double nota) {
        return new Avaliacao(1, null, false, nota, "t", "c", false, 0, null);
    }

    @Test
    public void exigeNotaTituloEComentarioNessaOrdem() {
        assertEquals(RegrasDaAvaliacao.Problema.SEM_NOTA,
                RegrasDaAvaliacao.conferir(0, "", ""));
        assertEquals(RegrasDaAvaliacao.Problema.SEM_TITULO,
                RegrasDaAvaliacao.conferir(3, "   ", ""));
        assertEquals(RegrasDaAvaliacao.Problema.SEM_COMENTARIO,
                RegrasDaAvaliacao.conferir(3, "Bom", " "));
        assertNull(RegrasDaAvaliacao.conferir(5, "Bom", "Gostei"));
    }

    @Test
    public void limitaTituloECometarioSemContarEspacosDasPontas() {
        assertEquals(RegrasDaAvaliacao.Problema.TITULO_LONGO,
                RegrasDaAvaliacao.conferir(3, repetir(101), "c"));
        assertNull(RegrasDaAvaliacao.conferir(3, " " + repetir(100) + " ", "c"));
        assertEquals(RegrasDaAvaliacao.Problema.COMENTARIO_LONGO,
                RegrasDaAvaliacao.conferir(3, "t", repetir(501)));
    }

    @Test
    public void montaOMotivoDaDenunciaComOComentarioOpcional() {
        assertEquals("Spam ou propaganda",
                RegrasDaAvaliacao.motivoDaDenuncia("Spam ou propaganda", "  "));
        assertEquals("Outro motivo. Fala de outro produto",
                RegrasDaAvaliacao.motivoDaDenuncia("Outro motivo", " Fala de outro produto "));
    }

    @Test
    public void contaOsDiasSemFicarNegativo() {
        LocalDate hoje = LocalDate.of(2026, 11, 2);
        assertEquals(0, RegrasDaAvaliacao.diasDesde(hoje, hoje));
        assertEquals(1, RegrasDaAvaliacao.diasDesde(hoje.minusDays(1), hoje));
        assertEquals(0, RegrasDaAvaliacao.diasDesde(hoje.plusDays(1), hoje));
    }

    @Test
    public void resumeMediaComUmaCasaEContaPorEstrela() {
        ResumoDasAvaliacoes resumo = ResumoDasAvaliacoes.de(Arrays.asList(
                comNota(5), comNota(4), comNota(4), comNota(1.4)));

        assertEquals(3.6, resumo.getMedia(), 0.0001);
        assertEquals(4, resumo.getTotal());
        assertEquals(1, resumo.comEstrelas(5));
        assertEquals(2, resumo.comEstrelas(4));
        assertEquals(1, resumo.comEstrelas(1));
        assertEquals(0, resumo.comEstrelas(3));
    }

    @Test
    public void resumoVazioNaoTemMediaENotaZeroContaComoUmaEstrela() {
        assertNull(ResumoDasAvaliacoes.de(Collections.emptyList()).getMedia());
        assertEquals(1, ResumoDasAvaliacoes.estrelas(0));
        assertEquals(5, ResumoDasAvaliacoes.estrelas(4.5));
    }
}
