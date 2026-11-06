package com.venussystem.venusmobile.view;

import android.view.LayoutInflater;
import android.view.View;

import com.venussystem.venusmobile.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class PerguntaCabeloLayoutTest {
    @Test
    public void sheetTemAsTresOpcoesCrespasComTagsDaTelaPrincipal() {
        PerguntaCabeloActivity atividade = Robolectric.buildActivity(PerguntaCabeloActivity.class)
                .setup().get();
        View raiz = LayoutInflater.from(atividade)
                .inflate(R.layout.sheet_tipo_cabelo, null, false);

        assertNotNull(raiz.findViewWithTag("TYPE_4A"));
        assertNotNull(raiz.findViewWithTag("TYPE_4B"));
        assertNotNull(raiz.findViewWithTag("TYPE_4C"));
    }

    @Test
    public void selecaoDaTelaPrincipalAceita4A4B4C() {
        PerguntaCabeloActivity atividade = Robolectric
                .buildActivity(PerguntaCabeloActivity.class).setup().get();
        View opcoes = atividade.findViewById(R.id.listaOpcoes);

        opcoes.findViewWithTag("TYPE_4A").performClick();
        org.junit.Assert.assertEquals("TYPE_4A", atividade.getRespostaSelecionada());
        opcoes.findViewWithTag("TYPE_4B").performClick();
        org.junit.Assert.assertEquals("TYPE_4B", atividade.getRespostaSelecionada());
        opcoes.findViewWithTag("TYPE_4C").performClick();
        org.junit.Assert.assertEquals("TYPE_4C", atividade.getRespostaSelecionada());
    }
}
