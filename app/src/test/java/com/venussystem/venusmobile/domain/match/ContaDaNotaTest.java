package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContaDaNotaTest {

    @Test
    public void usaOsPontosDaApiSemRefazerAConta() {
        ContaDaNota conta = ContaDaNota.de(74, 25.9, 15.3, 41);

        assertEquals(25.9, conta.pontosQualidade, 0.0);
        assertEquals(15.3, conta.pontosPerfil, 0.0);
        assertFalse(conta.limitada);
    }

    @Test
    public void notaBemAbaixoDaSomaFoiLimitada() {
        assertTrue(ContaDaNota.de(74, 25.9, 46.2, 15).limitada);
    }

    @Test
    public void semPerfilANotaEAQualidade() {
        ContaDaNota conta = ContaDaNota.de(74, 25.9, null, 74);

        assertNull(conta.pontosPerfil);
        assertFalse(conta.limitada);
        assertTrue(ContaDaNota.de(74, 25.9, null, 40).limitada);
    }

    @Test
    public void semPontosDaApiFicaVazia() {
        ContaDaNota conta = ContaDaNota.de(null, null, null, 10);

        assertTrue(conta.vazia());
        assertFalse(conta.limitada);
    }
}
