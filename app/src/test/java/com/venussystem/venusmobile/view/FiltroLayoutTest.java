package com.venussystem.venusmobile.view;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class FiltroLayoutTest {
    @Test
    public void grupoDeNotaUsaORotuloMatch() {
        assertEquals("Match", ApplicationProvider.getApplicationContext()
                .getString(R.string.filtro_grupo_nota));
    }
}
