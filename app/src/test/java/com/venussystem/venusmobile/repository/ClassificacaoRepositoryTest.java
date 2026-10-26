package com.venussystem.venusmobile.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.VenusClassificacaoApi;
import com.venussystem.venusmobile.repository.api.dto.ClassificationResponse;
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

import okhttp3.mockwebserver.MockWebServer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ClassificacaoRepositoryTest {

    private static final long PRODUTO = 7L;
    private static final String BUSCA_USUARIO = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String SALVA = "GET /api/classifications/user/42/product/7";
    private static final String CALCULAR = "POST /api/classifications";
    private static final String COMPLETO = "GET /api/products/7/full";

    private MockWebServer server;
    private ApiPorRota api;
    private PerfilRepository perfil;
    private ClassificacaoRepository repositorio;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        VenusApi crud = ApiDeTeste.criar(server, VenusApi.class);
        VenusClassificacaoApi classificacao = ApiDeTeste.criar(server, VenusClassificacaoApi.class);
        UsuarioApiRepository usuarios =
                new UsuarioApiRepository(context, crud, new SessaoFalsa());
        perfil = new PerfilRepository(context, "uid-ana");
        perfil.marcarQuestionarioRespondido();
        perfil.marcarEnviada(perfil.versaoAtual());
        repositorio = new ClassificacaoRepository(classificacao, crud, usuarios, perfil,
                new AnaliseDoPostRepository(context, "uid-ana"));

        api.em(BUSCA_USUARIO, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
        api.em(COMPLETO, 200, "{\"id\":7,\"currentVersion\":{\"id\":118}}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static String analise(int nota, String calculadaEm) {
        return "{\"userId\":42,\"productVersionId\":118,\"finalScore\":" + nota
                + ",\"recommendationLevel\":\"ACCEPTABLE\",\"calculatedAt\":\"" + calculadaEm + "\"}";
    }

    /** Resposta do POST com o motivo de alergia e os pontos, que o GET nao traz. */
    private static String analiseComAlergia(String calculadaEm) {
        return "{\"userId\":42,\"productVersionId\":118,\"finalScore\":49,"
                + "\"recommendationLevel\":\"NOT_RECOMMENDED\",\"calculatedAt\":\"" + calculadaEm + "\","
                + "\"breakdown\":{\"qualityScore\":74,\"qualityPoints\":25.9,\"profilePoints\":15.3},"
                + "\"reasons\":[{\"source\":\"ALLERGY\",\"warning\":true,"
                + "\"text\":\"Tem LIMONENO e LINALOL, que estão na sua alergia a Fragrância.\","
                + "\"ingredientNames\":[\"LIMONENO\",\"LINALOL\"]},"
                + "{\"source\":\"INGREDIENT_RULE\",\"warning\":false,\"ingredientName\":\"GLYCERIN\","
                + "\"ingredientNames\":null,\"profileTagSlug\":\"hidratacao\"}]}";
    }

    @Test
    public void analiseSalvaRecenteNaoGravaOutra() throws IOException {
        api.em(SALVA, 200, analise(41, "2099-01-01T00:00:00Z"));

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO).classificacao;

        assertEquals(Integer.valueOf(41), r.finalScore);
        assertEquals("ACCEPTABLE", r.recommendationLevel);
        assertTrue(api.pedidosEm(CALCULAR).isEmpty());
    }

    @Test
    public void semAnaliseSalvaCalculaComAVersaoAtual() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        api.em(CALCULAR, 201, analise(63, "2026-10-22T10:00:00Z"));

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO).classificacao;

        assertEquals(Integer.valueOf(63), r.finalScore);
        JsonObject corpo = api.corpo(CALCULAR);
        assertEquals(42L, corpo.get("userId").getAsLong());
        assertEquals(118L, corpo.get("productVersionId").getAsLong());
    }

    @Test
    public void perfilMudouDepoisDaAnaliseCalculaDeNovo() throws IOException {
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "DRY", "Seca");
        perfil.marcarEnviada(perfil.versaoAtual());
        api.em(SALVA, 200, analise(41, "2000-01-01T00:00:00Z"));
        api.em(CALCULAR, 201, analise(70, "2026-10-22T10:00:00Z"));

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO).classificacao;

        assertEquals(Integer.valueOf(70), r.finalScore);
        assertEquals(1, api.pedidosEm(CALCULAR).size());
    }

    @Test
    public void mudancaAindaNaoEnviadaFicaComASalva() throws IOException {
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "DRY", "Seca");
        api.em(SALVA, 200, analise(41, "2000-01-01T00:00:00Z"));

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO).classificacao;

        assertEquals(Integer.valueOf(41), r.finalScore);
        assertTrue(api.pedidosEm(CALCULAR).isEmpty());
    }

    @Test
    public void perfilForaDaApiDevolveOCodigo() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        api.em(CALCULAR, 404, "{\"status\":404,\"code\":\"PROFILE_NOT_FOUND\"}");

        ClassificacaoRepository.Resultado r = repositorio.classificarAgora(PRODUTO);

        assertNull(r.classificacao);
        assertEquals("PROFILE_NOT_FOUND", r.codigoDoErro);
    }

    @Test
    public void outro404DaSalvaNaoTentaCalcular() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"VERSION_NOT_FOUND\"}");

        ClassificacaoRepository.Resultado r = repositorio.classificarAgora(PRODUTO);

        assertNull(r.classificacao);
        assertEquals("VERSION_NOT_FOUND", r.codigoDoErro);
        assertTrue(api.pedidosEm(CALCULAR).isEmpty());
    }

    @Test
    public void calculoRecusadoSemSalvaDevolveOCodigo() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        api.em(CALCULAR, 422, "{\"status\":422,\"code\":\"VERSION_UNDER_REVIEW\"}");

        ClassificacaoRepository.Resultado r = repositorio.classificarAgora(PRODUTO);

        assertNull(r.classificacao);
        assertEquals("VERSION_UNDER_REVIEW", r.codigoDoErro);
    }

    @Test
    public void erroDoServidorNaoTemCodigo() throws IOException {
        api.em(SALVA, 502, "<html>502 Bad Gateway</html>");

        ClassificacaoRepository.Resultado r = repositorio.classificarAgora(PRODUTO);

        assertNull(r.classificacao);
        assertNull(r.codigoDoErro);
        assertTrue(api.pedidosEm(CALCULAR).isEmpty());
    }

    @Test
    public void calculoRecusadoFicaComASalva() throws IOException {
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "DRY", "Seca");
        perfil.marcarEnviada(perfil.versaoAtual());
        api.em(SALVA, 200, analise(41, "2000-01-01T00:00:00Z"));
        api.em(CALCULAR, 422, "{\"status\":422,\"code\":\"VERSION_UNDER_REVIEW\"}");

        ClassificacaoRepository.Resultado r = repositorio.classificarAgora(PRODUTO);

        assertEquals(Integer.valueOf(41), r.classificacao.finalScore);
        assertNull(r.codigoDoErro);
    }

    @Test
    public void semQuestionarioNaoChamaAClassificacao() throws IOException {
        perfil.limpar();

        ClassificacaoRepository.Resultado r = repositorio.classificarAgora(PRODUTO);

        assertNull(r.classificacao);
        assertNull(r.codigoDoErro);
        assertTrue(api.pedidosEm(SALVA, CALCULAR).isEmpty());
    }

    @Test
    public void alergiaDoPostVoltaNoGetDaMesmaAnalise() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        // O POST manda nanossegundos; o banco devolve microssegundos no GET.
        api.em(CALCULAR, 201, analiseComAlergia("2099-01-01T00:00:00.123456789Z"));

        assertEquals(Arrays.asList("LIMONENO", "LINALOL"),
                repositorio.classificarAgora(PRODUTO).classificacao.ingredientesDeAlergia());

        api.em(SALVA, 200, analise(49, "2099-01-01T00:00:00.123457Z"));
        ClassificationResponse salva = repositorio.classificarAgora(PRODUTO).classificacao;

        assertEquals(Arrays.asList("LIMONENO", "LINALOL"), salva.ingredientesDeAlergia());
        assertEquals(1, api.pedidosEm(CALCULAR).size());
    }

    @Test
    public void pontosDoPostVoltamNoGetDaMesmaAnalise() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        api.em(CALCULAR, 201, analiseComAlergia("2099-01-01T00:00:00Z"));
        repositorio.classificarAgora(PRODUTO);

        // O GET manda o breakdown com os pontos nulos.
        api.em(SALVA, 200, "{\"productVersionId\":118,\"finalScore\":41,"
                + "\"recommendationLevel\":\"ACCEPTABLE\",\"calculatedAt\":\"2099-01-01T00:00:00Z\","
                + "\"breakdown\":{\"qualityScore\":null,\"qualityPoints\":null,\"profilePoints\":null,"
                + "\"healthScore\":89}}");
        ClassificationResponse.Breakdown partes =
                repositorio.classificarAgora(PRODUTO).classificacao.breakdown;

        assertEquals(Integer.valueOf(74), partes.qualityScore);
        assertEquals(25.9, partes.qualityPoints, 0.0);
        assertEquals(15.3, partes.profilePoints, 0.0);
    }

    @Test
    public void analiseDeOutroLugarFicaSemPontos() throws IOException {
        api.em(SALVA, 200, analise(41, "2099-01-01T00:00:00Z"));

        assertNull(repositorio.classificarAgora(PRODUTO).classificacao.breakdown);
    }

    @Test
    public void alergiaGuardadaNaoVaiParaOutraAnalise() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        api.em(CALCULAR, 201, analiseComAlergia("2099-01-01T00:00:00Z"));
        repositorio.classificarAgora(PRODUTO);

        // Calculada depois, na web: o perfil pode ter mudado.
        api.em(SALVA, 200, analise(70, "2099-02-01T00:00:00Z"));

        assertTrue(repositorio.classificarAgora(PRODUTO).classificacao
                .ingredientesDeAlergia().isEmpty());
    }

    @Test
    public void apiSemIngredientNamesNaoTemAlergia() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        api.em(CALCULAR, 201, "{\"productVersionId\":118,\"finalScore\":49,"
                + "\"recommendationLevel\":\"NOT_RECOMMENDED\",\"calculatedAt\":\"2099-01-01T00:00:00Z\","
                + "\"reasons\":[{\"source\":\"ALLERGY\",\"warning\":true,"
                + "\"text\":\"Contém LIMONENE, ligado à sua alergia a Fragrância (gravidade alta).\"}]}");

        assertTrue(repositorio.classificarAgora(PRODUTO).classificacao
                .ingredientesDeAlergia().isEmpty());
    }

    @Test
    public void leOCodigoDoErroDaClassificacao() {
        assertEquals("PROFILE_NOT_FOUND", ClassificacaoRepository.codigoDoErro(
                "{\"status\":404,\"error\":\"Not Found\",\"code\":\"PROFILE_NOT_FOUND\"}"));
        assertNull(ClassificacaoRepository.codigoDoErro("{\"status\":401,\"code\":null}"));
        assertNull(ClassificacaoRepository.codigoDoErro("<html>502 Bad Gateway</html>"));
        assertNull(ClassificacaoRepository.codigoDoErro(""));
    }
}
