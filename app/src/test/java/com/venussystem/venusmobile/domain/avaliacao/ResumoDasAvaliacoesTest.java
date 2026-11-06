package com.venussystem.venusmobile.domain.avaliacao;

import com.venussystem.venusmobile.model.Avaliacao;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ResumoDasAvaliacoesTest {
    @Test
    public void resumoIgnoraNotasForaDoIntervalo() {
        ResumoDasAvaliacoes resumo = ResumoDasAvaliacoes.de(Arrays.asList(
                new Avaliacao(1, "A", false, 4, "t", "c", false, 0, null),
                new Avaliacao(2, "B", false, 8, "t", "c", false, 0, null)));

        assertEquals(1, resumo.getTotal());
        assertEquals(Double.valueOf(4.0), resumo.getMedia());
        assertEquals(1, resumo.comEstrelas(4));
    }
}
