package com.venussystem.venusmobile.view.componente;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.perfil.ProgressoPerfil;
import com.venussystem.venusmobile.view.PesquisaInicialActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class CartaoPerfilViewTest {

    private static final List<String> NOVE = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i");

    private Application app;
    private CartaoPerfilView cartao;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        cartao = new CartaoPerfilView(app);
    }

    private ProgressoPerfil progresso(boolean respondeu, int respondidos) {
        List<String> feitos = NOVE.subList(0, respondidos);
        return ProgressoPerfil.calcular(respondeu, NOVE, feitos::contains);
    }

    private TextView subtitulo() {
        return cartao.findViewById(R.id.subtituloCartaoPerfil);
    }

    private Button botao() {
        return cartao.findViewById(R.id.botaoCartaoPerfil);
    }

    private ProgressBar barra() {
        return cartao.findViewById(R.id.barraCartaoPerfil);
    }

    @Test
    public void titulo_eOSeuPerfilVenus() {
        TextView titulo = cartao.findViewById(R.id.tituloCartaoPerfil);

        assertEquals("Seu perfil Venus", titulo.getText().toString());
    }

    @Test
    public void semQuestionario_soTemTituloSubtituloEOBotaoResponderAgoraSemBarra() {
        cartao.atualizar(progresso(false, 0));

        assertTrue(cartao.estaVisivel());
        assertEquals(View.GONE, barra().getVisibility());
        assertEquals("Você precisa responder as perguntas de análise.", subtitulo().getText().toString());
        assertEquals("Responder agora", botao().getText().toString());
    }

    @Test
    public void incompleto_mostraABarraEOQueFaltaComOBotaoCompletarAgora() {
        cartao.atualizar(progresso(false, 5));

        assertTrue(cartao.estaVisivel());
        assertEquals(View.VISIBLE, barra().getVisibility());
        assertEquals(56, barra().getProgress());
        assertEquals("Perfil 56% completo", barra().getContentDescription().toString());
        assertEquals("Faltam 44% para completar as perguntas de análise.", subtitulo().getText().toString());
        assertEquals("Completar agora", botao().getText().toString());
    }

    @Test
    public void voltandoParaSemQuestionario_osubtituloVoltaAoTextoDeResponder() {
        cartao.atualizar(progresso(false, 5));
        cartao.atualizar(progresso(false, 0));

        assertEquals("Você precisa responder as perguntas de análise.", subtitulo().getText().toString());
    }

    @Test
    public void completo_ocartaoSome() {
        cartao.atualizar(progresso(true, 9));

        assertFalse(cartao.estaVisivel());
    }

    @Test
    public void voltaAAparecerSeOPerfilDeixaDeEstarCompleto() {
        cartao.atualizar(progresso(true, 9));
        cartao.atualizar(progresso(true, 7));

        assertTrue(cartao.estaVisivel());
        assertEquals(78, barra().getProgress());
    }

    @Test
    public void tocarNoBotao_abreOQuestionario() {
        cartao.atualizar(progresso(false, 0));

        botao().performClick();

        Intent aberta = shadowOf(app).getNextStartedActivity();
        assertNotNull(aberta);
        assertEquals(PesquisaInicialActivity.class.getName(), aberta.getComponent().getClassName());
        assertTrue((aberta.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }
}
