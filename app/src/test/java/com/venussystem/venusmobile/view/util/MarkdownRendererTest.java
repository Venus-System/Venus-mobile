package com.venussystem.venusmobile.view.util;

import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import com.venussystem.venusmobile.model.Mensagem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class MarkdownRendererTest {
    @Test
    public void renderizaTituloNegritoItalicoListaLinkECodigo() {
        Spanned renderizado = MarkdownRenderer.render(
                "# Título\n**forte** e *ênfase*\n- item\n[site](https://venus.example)\n`codigo`");

        assertTrue(renderizado.toString().contains("Título"));
        assertTrue(renderizado.toString().contains("• item"));
        assertTrue(renderizado.toString().contains("site"));
        assertTrue(renderizado.toString().contains("codigo"));
        assertTrue(renderizado.getSpans(0, renderizado.length(), StyleSpan.class).length >= 1);
        assertTrue(renderizado.getSpans(0, renderizado.length(), URLSpan.class).length == 1);
    }

    @Test
    public void htmlBrutoEMarkdownMalformadoVirAmTextoSeguro() {
        String texto = MarkdownRenderer.textoAcessivel("<script>alert(1)</script> **sem fechar");

        assertEquals("<script>alert(1)</script> **sem fechar", texto);
    }

    @Test
    public void mensagemDigitandoContinuaMarcadaParaAnimacao() {
        Mensagem mensagem = Mensagem.digitando("Digitando…");

        assertTrue(mensagem.isDigitando());
        assertEquals(Mensagem.DO_ASSISTENTE, mensagem.getAutor());
    }
}
