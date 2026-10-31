package com.venussystem.venusmobile.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.domain.perfil.ProgressoPerfil;
import com.venussystem.venusmobile.domain.perfil.ProgressoPerfil.Estado;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class PerfilProgressoTest {

    private PerfilRepository perfil;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        perfil = new PerfilRepository(context, "uid-ana");
    }

    private void responderOsSeteDeEscolhaUnica() {
        perfil.salvarResposta(PerfilRepository.FAIXA_ETARIA, "AGE_25_34", "25 a 34");
        perfil.salvarResposta(PerfilRepository.GENERO, "FEMALE", "Feminino");
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "OILY", "Oleosa");
        perfil.salvarResposta(PerfilRepository.FOTOTIPO, "III", "III");
        perfil.salvarResposta(PerfilRepository.SENSIBILIDADE, "MEDIUM", "Média");
        perfil.salvarResposta(PerfilRepository.TIPO_CABELO, "TYPE_2A", "2A");
        perfil.salvarResposta(PerfilRepository.COURO_CABELUDO, "NORMAL", "Normal");
    }

    @Test
    public void contaNova_semQuestionario() {
        ProgressoPerfil progresso = perfil.progresso();

        assertEquals(Estado.SEM_QUESTIONARIO, progresso.estado);
        assertEquals(0, progresso.porcentagem);
    }

    @Test
    public void respondeuAlgumasPerguntasEParou_incompleto() {
        perfil.salvarResposta(PerfilRepository.GENERO, "FEMALE", "Feminino");
        perfil.salvarResposta(PerfilRepository.FAIXA_ETARIA, "AGE_25_34", "25 a 34");
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "OILY", "Oleosa");
        perfil.salvarResposta(PerfilRepository.FOTOTIPO, "III", "III");

        ProgressoPerfil progresso = perfil.progresso();

        assertEquals(Estado.INCOMPLETO, progresso.estado);
        assertEquals(44, progresso.porcentagem);
        assertEquals(5, progresso.faltando.size());
    }

    @Test
    public void asDuasQueAceitamVarias_contamQuandoTemAlgoEscolhido() {
        responderOsSeteDeEscolhaUnica();
        perfil.salvarLista(PerfilRepository.GESTACAO, Collections.singletonList(PerfilRepository.NENHUMA));

        ProgressoPerfil progresso = perfil.progresso();

        assertEquals(Estado.INCOMPLETO, progresso.estado);
        assertEquals(89, progresso.porcentagem);
        assertEquals(Collections.singletonList(PerfilRepository.CONDICOES_PELE), progresso.faltando);
    }

    @Test
    public void listaVaziaNaoContaComoResposta() {
        perfil.salvarLista(PerfilRepository.CONDICOES_PELE, Collections.emptyList());

        assertEquals(Estado.SEM_QUESTIONARIO, perfil.progresso().estado);
    }

    @Test
    public void tudoRespondido_completo() {
        responderOsSeteDeEscolhaUnica();
        perfil.salvarLista(PerfilRepository.GESTACAO, Collections.singletonList(PerfilRepository.PREFIRO_NAO_DIZER));
        perfil.salvarLista(PerfilRepository.CONDICOES_PELE, Arrays.asList("ACNE", "ROSACEA"));

        ProgressoPerfil progresso = perfil.progresso();

        assertEquals(Estado.COMPLETO, progresso.estado);
        assertEquals(100, progresso.porcentagem);
        assertTrue(progresso.faltando.isEmpty());
    }

    @Test
    public void homem_naoTemPerguntaDeGestacao_eNaoPrecisaResponderParaCompletar() {
        responderOsSeteDeEscolhaUnica();
        perfil.salvarResposta(PerfilRepository.GENERO, "MALE", "Masculino");
        perfil.salvarLista(PerfilRepository.CONDICOES_PELE, Collections.singletonList(PerfilRepository.NENHUMA));

        assertEquals(false, perfil.perguntaGestacao());
        assertEquals(Estado.COMPLETO, perfil.progresso().estado);
        assertEquals(100, perfil.progresso().porcentagem);
    }

    @Test
    public void quemNaoEhHomem_continuaComAPerguntaDeGestacao() {
        perfil.salvarResposta(PerfilRepository.GENERO, "FEMALE", "Feminino");
        assertTrue(perfil.perguntaGestacao());

        perfil.salvarResposta(PerfilRepository.GENERO, "NON_BINARY", "Não binário");
        assertTrue(perfil.perguntaGestacao());
    }

    @Test
    public void virarHomem_limpaGravidezQueJaTinhaSidoMarcada() {
        perfil.salvarResposta(PerfilRepository.GENERO, "FEMALE", "Feminino");
        perfil.salvarLista(PerfilRepository.GESTACAO, Collections.singletonList("isPregnant"));

        perfil.salvarResposta(PerfilRepository.GENERO, "MALE", "Masculino");

        assertEquals(Collections.singletonList(PerfilRepository.NENHUMA),
                perfil.getLista(PerfilRepository.GESTACAO));
    }

    @Test
    public void prefiroNaoDizerContaComoRespondido() {
        perfil.salvarResposta(PerfilRepository.GENERO, "PREFER_NOT_SAY", "Prefiro não dizer");

        assertEquals(11, perfil.progresso().porcentagem);
    }

    @Test
    public void alergiasEPreferenciasNaoEntramNaConta() {
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Látex", "Fragrância"));
        perfil.salvarLista(PerfilRepository.PREFERENCIAS, Collections.singletonList("Vegano"));

        assertEquals(0, perfil.progresso().porcentagem);
        assertEquals(Estado.SEM_QUESTIONARIO, perfil.progresso().estado);
    }

    @Test
    public void chegouAoFimDoQuestionarioPulandoTudo_ficaIncompletoEnaoSemQuestionario() {
        perfil.marcarQuestionarioRespondido();

        assertEquals(Estado.INCOMPLETO, perfil.progresso().estado);
    }

    @Test
    public void cadaContaTemOSeuProgresso() {
        responderOsSeteDeEscolhaUnica();

        PerfilRepository outra = new PerfilRepository(ApplicationProvider.getApplicationContext(), "uid-bia");

        assertEquals(Estado.SEM_QUESTIONARIO, outra.progresso().estado);
    }
}
