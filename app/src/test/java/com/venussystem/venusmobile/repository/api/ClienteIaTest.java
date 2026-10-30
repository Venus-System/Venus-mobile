package com.venussystem.venusmobile.repository.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClienteIaTest {

    @Test
    public void urlSemBarraNoFim_ganhaABarraQueORetrofitExige() {
        assertEquals("http://localhost:8080/", ClienteIa.normalizar("http://localhost:8080"));
    }

    @Test
    public void urlComBarraEEspacos_ficaComUmaBarraSo() {
        assertEquals("http://13.222.7.185/",
                ClienteIa.normalizar("  http://13.222.7.185/ "));
    }

    @Test
    public void urlComCaminhoSemBarraNoFim_mantemOCaminhoEGanhaABarra() {
        assertEquals("https://34-192-194-32.sslip.io/classificacao/",
                ClienteIa.normalizar("https://34-192-194-32.sslip.io/classificacao"));
    }
}
