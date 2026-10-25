package com.venussystem.venusmobile.view.adapter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConversaAdapterTest {

    @Test
    public void pontosDoDigitando_vaoDeZeroATresEVoltam() {
        assertEquals(0, ConversaAdapter.pontosVisiveis(0));
        assertEquals(1, ConversaAdapter.pontosVisiveis(1));
        assertEquals(2, ConversaAdapter.pontosVisiveis(2));
        assertEquals(3, ConversaAdapter.pontosVisiveis(3));
        assertEquals(0, ConversaAdapter.pontosVisiveis(4));
        assertEquals(2, ConversaAdapter.pontosVisiveis(10));
    }
}
