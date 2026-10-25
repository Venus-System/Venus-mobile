package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContaDaNotaTest {

    @Test
    public void exemploDoReadmeDaClassificacao() {
        // 35 x 74% = 25,9 e 65 x 23,53% = 15,3: nota 41.
        ContaDaNota conta = ContaDaNota.de(74, 23.53, 41);

        assertEquals(Integer.valueOf(74), conta.qualidade);
        assertEquals(Integer.valueOf(24), conta.combinacao);
        assertFalse(conta.limitada);
    }

    @Test
    public void notaBemAbaixoDaSomaFoiLimitada() {
        // A soma daria 72, mas uma alergia alta prende a nota em 15.
        assertTrue(ContaDaNota.de(74, 71.0, 15).limitada);
    }

    @Test
    public void semCombinacaoANotaEAQualidade() {
        ContaDaNota conta = ContaDaNota.de(74, null, 74);

        assertNull(conta.combinacao);
        assertFalse(conta.limitada);
    }

    @Test
    public void semQualidadeNaoDaParaSaberSeFoiLimitada() {
        ContaDaNota conta = ContaDaNota.de(null, 71.0, 10);

        assertFalse(conta.limitada);
        assertFalse(conta.vazia());
        assertTrue(ContaDaNota.de(null, null, 10).vazia());
    }
}
