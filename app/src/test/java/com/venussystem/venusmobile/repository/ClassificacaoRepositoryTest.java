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
        repositorio = new ClassificacaoRepository(classificacao, crud, usuarios, perfil);

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

    @Test
    public void analiseSalvaRecenteNaoGravaOutra() throws IOException {
        api.em(SALVA, 200, analise(41, "2099-01-01T00:00:00Z"));

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO);

        assertEquals(Integer.valueOf(41), r.finalScore);
        assertEquals("ACCEPTABLE", r.recommendationLevel);
        assertTrue(api.pedidosEm(CALCULAR).isEmpty());
    }

    @Test
    public void semAnaliseSalvaCalculaComAVersaoAtual() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"ANALYSIS_NOT_FOUND\"}");
        api.em(CALCULAR, 201, analise(63, "2026-10-22T10:00:00Z"));

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO);

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

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO);

        assertEquals(Integer.valueOf(70), r.finalScore);
        assertEquals(1, api.pedidosEm(CALCULAR).size());
    }

    @Test
    public void mudancaAindaNaoEnviadaFicaComASalva() throws IOException {
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "DRY", "Seca");
        api.em(SALVA, 200, analise(41, "2000-01-01T00:00:00Z"));

        ClassificationResponse r = repositorio.classificarAgora(PRODUTO);

        assertEquals(Integer.valueOf(41), r.finalScore);
        assertTrue(api.pedidosEm(CALCULAR).isEmpty());
    }

    @Test
    public void perfilForaDaApiNaoTentaCalcular() throws IOException {
        api.em(SALVA, 404, "{\"status\":404,\"code\":\"PROFILE_NOT_FOUND\"}");

        assertNull(repositorio.classificarAgora(PRODUTO));
        assertTrue(api.pedidosEm(CALCULAR).isEmpty());
    }

    @Test
    public void calculoRecusadoFicaComASalva() throws IOException {
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "DRY", "Seca");
        perfil.marcarEnviada(perfil.versaoAtual());
        api.em(SALVA, 200, analise(41, "2000-01-01T00:00:00Z"));
        api.em(CALCULAR, 422, "{\"status\":422,\"code\":\"VERSION_UNDER_REVIEW\"}");

        assertEquals(Integer.valueOf(41), repositorio.classificarAgora(PRODUTO).finalScore);
    }

    @Test
    public void semQuestionarioNaoChamaAClassificacao() throws IOException {
        perfil.limpar();

        assertNull(repositorio.classificarAgora(PRODUTO));
        assertTrue(api.pedidosEm(SALVA, CALCULAR).isEmpty());
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
