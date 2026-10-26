package com.venussystem.venusmobile.view.util;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.snackbar.Snackbar;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;
import com.venussystem.venusmobile.view.DetalheProdutoEmAnaliseActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class MensagemRapidaTest {

    // Qualquer tela do app serve de ancora; esta abre sem ir na rede.
    private DetalheProdutoEmAnaliseActivity tela;

    @Before
    public void abrirTela() {
        ProdutoEmAnalise produto = new ProdutoEmAnalise("1", Situacao.AGUARDANDO, "Hidratante X",
                "Marca Y", null, null, LocalDate.of(2026, 10, 2), null, null, null);
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                DetalheProdutoEmAnaliseActivity.class);
        intent.putExtra(DetalheProdutoEmAnaliseActivity.EXTRA_PRODUTO, produto);
        tela = Robolectric.buildActivity(DetalheProdutoEmAnaliseActivity.class, intent)
                .setup().get();
    }

    private static TextView texto(Snackbar barra) {
        return barra.getView().findViewById(com.google.android.material.R.id.snackbar_text);
    }

    private static Drawable iconeInicial(Snackbar barra) {
        return texto(barra).getCompoundDrawablesRelative()[0];
    }

    // A duracao nao entra: o Snackbar.getDuration() passa pelo
    // AccessibilityManager, que no Robolectric devolve 0.

    @Test
    public void sucesso_mostraOTextoComOCheck() {
        Snackbar barra = MensagemRapida.sucesso(tela.findViewById(R.id.main), "Salvo em Skincare");

        assertEquals("Salvo em Skincare", texto(barra).getText().toString());
        assertNotNull(iconeInicial(barra));
        assertEquals(tela.getColor(R.color.white), texto(barra).getCurrentTextColor());
    }

    @Test
    public void neutra_mostraOTextoSemOCheck() {
        Snackbar barra = MensagemRapida.neutra(tela.findViewById(R.id.main), "Removido de Skincare");

        assertEquals("Removido de Skincare", texto(barra).getText().toString());
        assertNull(iconeInicial(barra));
        assertEquals(tela.getColor(R.color.white), texto(barra).getCurrentTextColor());
    }
}
