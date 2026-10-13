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
        assertEquals("https://venus-ai.onrender.com/",
                ClienteIa.normalizar("  https://venus-ai.onrender.com/ "));
    }
}
