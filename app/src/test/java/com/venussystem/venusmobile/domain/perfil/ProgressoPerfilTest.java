package com.venussystem.venusmobile.domain.perfil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.venussystem.venusmobile.domain.perfil.ProgressoPerfil.Estado;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProgressoPerfilTest {

    private static final List<String> NOVE = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i");

    private static Set<String> respondidos(String... campos) {
        return new HashSet<>(Arrays.asList(campos));
    }

    @Test
    public void nadaRespondidoESemTerChegadoAoFim_chamaParaOQuestionario() {
        ProgressoPerfil progresso = ProgressoPerfil.calcular(false, NOVE, respondidos()::contains);

        assertEquals(Estado.SEM_QUESTIONARIO, progresso.estado);
        assertEquals(0, progresso.porcentagem);
        assertEquals(9, progresso.faltando.size());
    }

    @Test
    public void respondeuParte_mostraAPorcentagemEOQueFalta() {
        ProgressoPerfil progresso = ProgressoPerfil.calcular(false, NOVE, respondidos("a", "b", "c")::contains);

        assertEquals(Estado.INCOMPLETO, progresso.estado);
        assertEquals(33, progresso.porcentagem);
        assertEquals(Arrays.asList("d", "e", "f", "g", "h", "i"), progresso.faltando);
    }

    @Test
    public void respondeuTudo_completo() {
        ProgressoPerfil progresso = ProgressoPerfil.calcular(true, NOVE, campo -> true);

        assertEquals(Estado.COMPLETO, progresso.estado);
        assertEquals(100, progresso.porcentagem);
        assertTrue(progresso.faltando.isEmpty());
    }

    @Test
    public void respondeuTudoMesmoSemOMarcadorDeFim_completo() {
        assertEquals(Estado.COMPLETO, ProgressoPerfil.calcular(false, NOVE, campo -> true).estado);
    }

    @Test
    public void chegouAoFimMasNadaRespondido_incompletoZero() {
        ProgressoPerfil progresso = ProgressoPerfil.calcular(true, NOVE, campo -> false);

        assertEquals(Estado.INCOMPLETO, progresso.estado);
        assertEquals(0, progresso.porcentagem);
    }

    @Test
    public void arredondaComoOSite() {
        List<String> tres = Arrays.asList("a", "b", "c");

        assertEquals(33, ProgressoPerfil.calcular(false, tres, respondidos("a")::contains).porcentagem);
        assertEquals(67, ProgressoPerfil.calcular(false, tres, respondidos("a", "b")::contains).porcentagem);
        assertEquals(56, ProgressoPerfil.calcular(false, NOVE, respondidos("a", "b", "c", "d", "e")::contains).porcentagem);
    }

    @Test
    public void semCamposParaContar_completo() {
        ProgressoPerfil progresso = ProgressoPerfil.calcular(false, Collections.emptyList(), campo -> false);

        assertEquals(Estado.COMPLETO, progresso.estado);
        assertEquals(100, progresso.porcentagem);
    }
}
