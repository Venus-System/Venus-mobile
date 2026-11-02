package com.venussystem.venusmobile.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.time.ZoneId;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class LembreteRepositoriesTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private static long instante(int dia, int hora) {
        return LocalDateTime.of(2026, 10, dia, hora, 0).atZone(SAO_PAULO).toInstant().toEpochMilli();
    }

    @Test
    public void questionario_comecaDesligado() {
        LembreteQuestionarioRepository repositorio = new LembreteQuestionarioRepository(context, "uid-ana");

        assertFalse(repositorio.ativo());
        assertEquals(-1L, repositorio.proximoEm(SAO_PAULO));
    }

    @Test
    public void questionario_aoAtivar_oPrimeiroSaiUmDiaDepoisAs10h() {
        LembreteQuestionarioRepository repositorio = new LembreteQuestionarioRepository(context, "uid-ana");

        repositorio.ativar(instante(17, 23));

        assertTrue(repositorio.ativo());
        assertEquals(instante(18, 10), repositorio.proximoEm(SAO_PAULO));
    }

    @Test
    public void questionario_cadaEnvioAvancaAAgendaAteAcabar() {
        LembreteQuestionarioRepository repositorio = new LembreteQuestionarioRepository(context, "uid-ana");
        repositorio.ativar(instante(17, 23));

        repositorio.registrarEnvio(instante(18, 10));
        assertEquals(1, repositorio.enviados());
        assertEquals(instante(21, 10), repositorio.proximoEm(SAO_PAULO));

        repositorio.registrarEnvio(instante(21, 10));
        assertEquals(instante(28, 10), repositorio.proximoEm(SAO_PAULO));

        repositorio.registrarEnvio(instante(28, 10));
        assertEquals(-1L, repositorio.proximoEm(SAO_PAULO));
    }

    @Test
    public void questionario_desativarApagaTudo() {
        LembreteQuestionarioRepository repositorio = new LembreteQuestionarioRepository(context, "uid-ana");
        repositorio.ativar(instante(17, 23));
        repositorio.registrarEnvio(instante(18, 10));

        repositorio.desativar();

        assertFalse(repositorio.ativo());
        assertEquals(0, repositorio.enviados());
        assertEquals(-1L, repositorio.proximoEm(SAO_PAULO));
    }

    @Test
    public void questionario_eUmPorConta() {
        new LembreteQuestionarioRepository(context, "uid-ana").ativar(instante(17, 23));

        assertFalse(new LembreteQuestionarioRepository(context, "uid-bia").ativo());
        assertTrue(new LembreteQuestionarioRepository(context, "uid-ana").ativo());
    }
}
