package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ListaItemRepositoryTest {

    private Context context;
    private ListaItemRepository itens;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        itens = new ListaItemRepository(context, "uid-ana");
    }

    @Test
    public void contem_soDepoisDeAdicionar() {
        assertFalse(itens.contem(1L, 42L));

        itens.adicionar(1L, 42L);

        assertTrue(itens.contem(1L, 42L));
        // O produto entrou so nesta lista.
        assertFalse(itens.contem(2L, 42L));
    }

    @Test
    public void contem_falsoDepoisDeRemover() {
        itens.adicionar(1L, 42L);

        itens.remover(1L, 42L);

        assertFalse(itens.contem(1L, 42L));
    }

    @Test
    public void adicionar_poeOProdutoNoTopoEUmaVezSo() {
        itens.adicionar(1L, 10L);
        itens.adicionar(1L, 20L);
        itens.adicionar(1L, 10L);

        assertEquals(Arrays.asList(20L, 10L), itens.getProdutoIds(1L));
    }

    @Test
    public void contem_naoMisturaContas() {
        itens.adicionar(1L, 42L);

        ListaItemRepository outraConta = new ListaItemRepository(context, "uid-bia");

        assertFalse(outraConta.contem(1L, 42L));
    }
}
