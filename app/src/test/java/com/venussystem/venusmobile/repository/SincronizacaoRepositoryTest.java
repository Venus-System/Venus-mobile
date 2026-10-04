package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.testutil.ApiDeTeste;
import com.venussystem.venusmobile.testutil.ApiPorRota;
import com.venussystem.venusmobile.testutil.SessaoFalsa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class SincronizacaoRepositoryTest {

    private static final String BUSCA = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String PUT_PERFIL = "PUT /api/user-profiles/42";
    private static final String POST_PERFIL = "POST /api/user-profiles";
    private static final String PUT_PREFERENCIAS = "PUT /api/user-preferences/42";
    private static final String POST_PREFERENCIAS = "POST /api/user-preferences";

    private static final String CATALOGO_ALERGIAS = "GET /api/allergies";
    private static final String ALERGIAS_DA_ANA = "GET /api/user-allergies/user/42?page=0&size=100";
    private static final String POST_ALERGIA = "POST /api/user-allergies";
    private static final String DELETE_LATEX = "DELETE /api/user-allergies/user/42/allergy/9";
    private static final String DELETE_SOJA = "DELETE /api/user-allergies/user/42/allergy/12";

    private static final String CATALOGO_ETIQUETAS = "GET /api/profile-tags/preferences";
    private static final String ETIQUETAS_DA_ANA =
            "GET /api/user-profile-tags/user/42?page=0&size=100";
    private static final String POST_ETIQUETA = "POST /api/user-profile-tags";

    private static final String NADA_SALVO = "{\"content\":[],\"last\":true}";

    private MockWebServer server;
    private ApiPorRota api;
    private PerfilRepository perfil;
    private SincronizacaoRepository sincronizacao;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        perfil = new PerfilRepository(context, "uid-ana");
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        UsuarioApiRepository usuarios =
                new UsuarioApiRepository(context, venusApi, new SessaoFalsa());
        sincronizacao = new SincronizacaoRepository(perfil, usuarios, venusApi);

        api.em(BUSCA, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");

        // Catalogos e listas da Ana: por padrao ela ainda nao tem nada na API.
        api.em(CATALOGO_ALERGIAS, 200,
                "[{\"id\":9,\"allergyName\":\"Látex\"},{\"id\":12,\"allergyName\":\"Soja\"}]");
        api.em(ALERGIAS_DA_ANA, 200, NADA_SALVO);
        api.em(POST_ALERGIA, 201, "{}");
        api.em(CATALOGO_ETIQUETAS, 200,
                "[{\"id\":28,\"slug\":\"vegano\",\"name\":\"Vegano\"},"
                        + "{\"id\":37,\"slug\":\"ingredientes-naturais\","
                        + "\"name\":\"Ingredientes Naturais\"}]");
        api.em(ETIQUETAS_DA_ANA, 200, NADA_SALVO);
        api.em(POST_ETIQUETA, 201, "{}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private void responderQuestionarioCompleto() {
        perfil.salvarResposta(PerfilRepository.GENERO, "FEMALE", "Feminino");
        perfil.salvarResposta(PerfilRepository.TIPO_CABELO, "TYPE_1", "Tipo 1");
        perfil.salvarResposta(PerfilRepository.COURO_CABELUDO, "NORMAL", "Normal");
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "DRY", "Seca");
        perfil.salvarResposta(PerfilRepository.SENSIBILIDADE, "HIGH", "Alta");
        perfil.salvarResposta(PerfilRepository.FOTOTIPO, "III", "III");
        perfil.salvarResposta(PerfilRepository.FAIXA_ETARIA, "AGE_18_24", "18 a 24");
        perfil.salvarLista(PerfilRepository.CONDICOES_PELE, Arrays.asList("hasRosacea"));
        perfil.salvarLista(PerfilRepository.PREFERENCIAS, Arrays.asList("Vegano"));
    }

    private void apiSemPerfilAinda() {
        // PUT sem resposta configurada ja cai no 404 do ApiPorRota.
        api.em(POST_PERFIL, 201, "{}");
        api.em(POST_PREFERENCIAS, 201, "{}");
    }

    private void apiComPerfil() {
        api.em(PUT_PERFIL, 200, "{}");
        api.em(PUT_PREFERENCIAS, 200, "{}");
    }

    // ---- Primeiro envio ----

    @Test
    public void primeiroEnvio_putDa404EEntaoCriaComPost() {
        responderQuestionarioCompleto();
        apiSemPerfilAinda();

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Arrays.asList(BUSCA, PUT_PERFIL, POST_PERFIL, PUT_PREFERENCIAS,
                POST_PREFERENCIAS, CATALOGO_ALERGIAS, ALERGIAS_DA_ANA, CATALOGO_ETIQUETAS,
                ETIQUETAS_DA_ANA), api.pedidos);
        assertFalse(perfil.temAlteracaoParaEnviar());
    }

    @Test
    public void corpoDoPerfil_levaOIdEAsTagsTraduzidas() {
        responderQuestionarioCompleto();
        apiSemPerfilAinda();

        sincronizacao.sincronizarAgora();

        JsonObject corpo = api.corpo(POST_PERFIL);
        assertEquals(42, corpo.get("userId").getAsLong());
        assertEquals("TYPE_1A", corpo.get("hairPattern").getAsString());
        assertEquals("DRY", corpo.get("skinType").getAsString());
        assertTrue(corpo.get("hasRosacea").getAsBoolean());
        assertFalse("nao respondeu gestacao: o campo nem vai", corpo.has("isPregnant"));

        JsonObject preferencias = api.corpo(POST_PREFERENCIAS);
        assertTrue(preferencias.get("preferVegan").getAsBoolean());
        assertFalse(preferencias.get("preferCrueltyFree").getAsBoolean());
        assertFalse(preferencias.get("preferFragranceFree").getAsBoolean());
        assertFalse(preferencias.get("preferSustainable").getAsBoolean());
    }

    @Test
    public void perfilQueJaExiste_soAtualizaComPut() {
        responderQuestionarioCompleto();
        apiComPerfil();

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Arrays.asList(PUT_PERFIL, PUT_PREFERENCIAS),
                api.pedidosEm(PUT_PERFIL, POST_PERFIL, PUT_PREFERENCIAS, POST_PREFERENCIAS));
    }

    // ---- Alergias ----

    @Test
    public void alergiaEscolhida_vaiComOIdDoCatalogoEGravidadeHigh() {
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Látex"));
        apiComPerfil();

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(POST_ALERGIA);
        assertEquals(42, corpo.get("userId").getAsLong());
        assertEquals(9, corpo.get("allergyId").getAsLong());
        assertEquals("HIGH", corpo.get("severity").getAsString());
    }

    @Test
    public void alergiaForaDoCatalogo_ficaSoNoAparelhoENaoTravaOEnvio() {
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Níquel"));
        apiComPerfil();

        assertTrue(sincronizacao.sincronizarAgora());

        assertTrue(api.corposEm(POST_ALERGIA).isEmpty());
        assertFalse(perfil.temAlteracaoParaEnviar());
    }

    @Test
    public void alergiaQueJaEstaNaApi_naoEGravadaDeNovo() {
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Látex"));
        apiComPerfil();
        api.em(ALERGIAS_DA_ANA, 200, "{\"content\":[{\"allergyId\":9}],\"last\":true}");

        assertTrue(sincronizacao.sincronizarAgora());

        assertTrue(api.corposEm(POST_ALERGIA).isEmpty());
    }

    @Test
    public void alergiaTiradaNoApp_eApagadaDaApi() {
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Látex"));
        apiComPerfil();
        sincronizacao.sincronizarAgora();

        perfil.salvarLista(PerfilRepository.ALERGIAS, Collections.emptyList());
        api.em(ALERGIAS_DA_ANA, 200, "{\"content\":[{\"allergyId\":9}],\"last\":true}");
        api.em(DELETE_LATEX, 204, "");

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Collections.singletonList(DELETE_LATEX), api.pedidosEm(DELETE_LATEX));
    }

    @Test
    public void alergiaCadastradaPeloSite_naoEApagada() {
        // Soja foi cadastrada no site. O app nao le isso de volta, entao a Ana
        // nunca viu Soja aqui - apagar seria tirar uma alergia sem ela saber.
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Látex"));
        apiComPerfil();
        api.em(ALERGIAS_DA_ANA, 200, "{\"content\":[{\"allergyId\":12}],\"last\":true}");

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(1, api.corposEm(POST_ALERGIA).size());
        assertTrue(api.pedidosEm(DELETE_SOJA).isEmpty());
    }

    @Test
    public void refazerQuestionario_aindaApagaAlergiaQueOAppGravou() {
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Látex"));
        apiComPerfil();
        assertTrue(sincronizacao.sincronizarAgora());

        // "Refazer questionario" apaga as respostas; a Ana responde de novo
        // sem Latex, e a API ainda tem o Latex que o app gravou antes.
        perfil.limpar();
        assertNull(perfil.getTag(PerfilRepository.GENERO));
        responderQuestionarioCompleto();
        api.em(ALERGIAS_DA_ANA, 200, "{\"content\":[{\"allergyId\":9}],\"last\":true}");
        api.em(DELETE_LATEX, 204, "");

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Collections.singletonList(DELETE_LATEX), api.pedidosEm(DELETE_LATEX));
    }

    @Test
    public void falhaAoGravarAlergia_continuaPendente() {
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.ALERGIAS, Arrays.asList("Látex"));
        apiComPerfil();
        api.em(POST_ALERGIA, 503, "{\"status\":503}");

        assertFalse(sincronizacao.sincronizarAgora());

        assertTrue(perfil.temAlteracaoParaEnviar());
        assertTrue("parou antes das etiquetas", api.pedidosEm(CATALOGO_ETIQUETAS).isEmpty());
    }

    // ---- Etiquetas ----

    @Test
    public void preferenciaSemColuna_vaiComoEtiqueta() {
        responderQuestionarioCompleto();
        perfil.salvarLista(PerfilRepository.PREFERENCIAS, Arrays.asList("Vegano", "Natural"));
        apiComPerfil();

        assertTrue(sincronizacao.sincronizarAgora());

        // Vegano vai pela coluna preferVegan, e so Natural vira etiqueta.
        assertEquals(1, api.corposEm(POST_ETIQUETA).size());
        JsonObject corpo = api.corpo(POST_ETIQUETA);
        assertEquals(42, corpo.get("userId").getAsLong());
        assertEquals(37, corpo.get("profileTagId").getAsLong());
    }

    @Test
    public void semCatalogoDeEtiquetas_continuaPendente() {
        responderQuestionarioCompleto();
        apiComPerfil();
        api.em(CATALOGO_ETIQUETAS, 503, "{\"status\":503}");

        assertFalse(sincronizacao.sincronizarAgora());

        assertTrue(perfil.temAlteracaoParaEnviar());
    }

    // ---- Quando nao envia ----

    @Test
    public void jaEnviado_naoMandaDeNovo() {
        responderQuestionarioCompleto();
        apiSemPerfilAinda();
        sincronizacao.sincronizarAgora();
        int antes = api.pedidos.size();

        assertFalse(sincronizacao.sincronizarAgora());

        // O id ja esta guardado e nada mudou: nem a busca de usuario sai.
        assertEquals(antes, api.pedidos.size());
    }

    @Test
    public void questionarioIncompleto_soCadastraOUsuario() {
        perfil.salvarResposta(PerfilRepository.GENERO, "FEMALE", "Feminino");

        assertFalse(sincronizacao.sincronizarAgora());

        assertEquals(Arrays.asList(BUSCA), api.pedidos);
        assertTrue(perfil.temAlteracaoParaEnviar());
    }

    @Test
    public void menorDeIdade_perfilFicaSoNoAparelho() {
        responderQuestionarioCompleto();
        perfil.salvarResposta(PerfilRepository.FAIXA_ETARIA, "AGE_13_17", "13 a 17");

        assertFalse(sincronizacao.sincronizarAgora());

        assertEquals(Arrays.asList(BUSCA), api.pedidos);
    }

    @Test
    public void respostasAntigasSemContador_tambemSaoEnviadas() {
        // Quem respondeu antes desta versao do app nao tem contador salvo.
        assertTrue(perfil.temAlteracaoParaEnviar());
    }

    // ---- Falhas ----

    @Test
    public void edicaoDuranteOEnvio_continuaPendente() {
        responderQuestionarioCompleto();
        apiSemPerfilAinda();
        api.aoReceber(PUT_PERFIL, () ->
                perfil.salvarResposta(PerfilRepository.TIPO_PELE, "OILY", "Oleosa"));

        assertTrue(sincronizacao.sincronizarAgora());

        assertTrue(perfil.temAlteracaoParaEnviar());
    }

    @Test
    public void falhaNoEnvio_continuaPendente() {
        responderQuestionarioCompleto();
        api.em(PUT_PERFIL, 503, "{\"status\":503}");

        assertFalse(sincronizacao.sincronizarAgora());

        assertTrue(perfil.temAlteracaoParaEnviar());
    }

    @Test
    public void idGuardadoQueNaoExisteMais_eBuscadoDeNovoNaProxima() {
        responderQuestionarioCompleto();
        api.em(POST_PERFIL, 404, "{\"status\":404,\"message\":\"Usuario nao encontrado\"}");

        assertFalse(sincronizacao.sincronizarAgora());
        assertFalse(sincronizacao.sincronizarAgora());

        long buscas = api.pedidos.stream().filter(BUSCA::equals).count();
        assertEquals(2, buscas);
    }

    @Test
    public void idGuardadoDeOutraPessoa_403_eBuscadoDeNovoNaProxima() {
        // Banco recriado: o id 42 guardado agora e de outra conta, e a API
        // recusa o token da Ana nele.
        responderQuestionarioCompleto();
        api.em(PUT_PERFIL, 403, "{\"status\":403,\"message\":\"Acesso negado\"}");

        assertFalse(sincronizacao.sincronizarAgora());
        assertFalse(sincronizacao.sincronizarAgora());

        long buscas = api.pedidos.stream().filter(BUSCA::equals).count();
        assertEquals(2, buscas);
        assertTrue("403 nao e 'nao existe': nao tenta criar", api.pedidosEm(POST_PERFIL).isEmpty());
        assertTrue(perfil.temAlteracaoParaEnviar());
    }
}
