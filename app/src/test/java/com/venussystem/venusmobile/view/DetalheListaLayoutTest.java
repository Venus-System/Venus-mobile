package com.venussystem.venusmobile.view;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class DetalheListaLayoutTest {
    @Test
    public void listaNaoRegistraNotaNemBotaoDeEscanear() {
        String pacote = ApplicationProvider.getApplicationContext().getPackageName();

        assertEquals(0, ApplicationProvider.getApplicationContext().getResources()
                .getIdentifier("btnVerNota", "id", pacote));
        assertEquals(0, ApplicationProvider.getApplicationContext().getResources()
                .getIdentifier("btnEscanearVazio", "id", pacote));
    }
}
