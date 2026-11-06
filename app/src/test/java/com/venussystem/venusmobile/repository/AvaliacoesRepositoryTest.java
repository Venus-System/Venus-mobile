package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.venussystem.venusmobile.model.Avaliacao;
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
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class AvaliacoesRepositoryTest {

    private static final String BUSCA_USUARIO = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String VERSAO = "GET /api/product-versions/product/7/current";
    private static final String PAGINA_0 =
            "GET /api/reviews/product-version/31?page=0&size=50&sort=createdAt%2Cdesc";
    private static final String PAGINA_1 =
            "GET /api/reviews/product-version/31?page=1&size=50&sort=createdAt%2Cdesc";
    private static final String PUBLICAR = "POST /api/reviews";
    private static final String MEU_VOTO = "GET /api/review-votes/review/88/user/42";
    private static final String VOTAR = "POST /api/review-votes";
    private static final String TROCAR_VOTO = "PATCH /api/review-votes/review/88/user/42";
    private static final String APAGAR_VOTO = "DELETE /api/review-votes/review/88/user/42";
    private static final String DENUNCIAR = "POST /api/reports";

    private MockWebServer server;
    private ApiPorRota api;
    private SessaoFalsa sessao;
    private AvaliacoesRepository repositorio;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        sessao = new SessaoFalsa();
        UsuarioApiRepository usuarios = new UsuarioApiRepository(context, venusApi, sessao);
        usuarios.esquecerId();
        AvaliacoesRepository.esquecerVersoes();
        repositorio = new AvaliacoesRepository(usuarios, venusApi, Runnable::run, Runnable::run);

        api.em(BUSCA_USUARIO, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
        api.em(VERSAO, 200, "{\"id\":31,\"productId\":7}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static String avaliacao(long id, long userId, double nota) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("userId", userId);
        json.addProperty("authorName", "Felipe A.");
        json.addProperty("rating", nota);
        json.addProperty("title", " Ótimo ");
        json.addProperty("comment", "Gostei");
        json.addProperty("verifiedUse", true);
        json.addProperty("usefulVotes", 3);
        json.addProperty("createdAt", "2026-10-30T14:30:00-03:00");
        return json.toString();
    }

    @Test
    public void buscaAsAvaliacoesDaVersaoAtualEMarcaAMinha() {
        api.em(PAGINA_0, 200, "{\"content\":[" + avaliacao(1, 42, 4.0) + ","
                + avaliacao(2, 9, 2.0) + "],\"last\":true}");

        AtomicReference<AvaliacoesRepository.Lidas> lidas = new AtomicReference<>();
        repositorio.buscar(7, lidas::set);

        assertEquals(2, lidas.get().getAvaliacoes().size());
        Avaliacao minha = lidas.get().getAvaliacoes().get(0);
        assertTrue(minha.isMinha());
        assertEquals("Ótimo", minha.getTitulo());
        assertEquals(3L, minha.getVotosUteis());
        assertTrue(minha.isUsoVerificado());
        assertFalse(lidas.get().getAvaliacoes().get(1).isMinha());
        assertFalse(lidas.get().isTruncada());
    }

    @Test
    public void ignoraNotaForaDoIntervaloENormalizaVotosNegativos() {
        String valida = avaliacao(1, 9, 4.0).replace("\"usefulVotes\":3", "\"usefulVotes\":-2");
        String invalida = avaliacao(2, 9, 6.0);
        api.em(PAGINA_0, 200, "{\"content\":[" + valida + "," + invalida + "],\"last\":true}");

        AtomicReference<AvaliacoesRepository.Lidas> lidas = new AtomicReference<>();
        repositorio.buscar(7, lidas::set);

        assertEquals(1, lidas.get().getAvaliacoes().size());
        assertEquals(0L, lidas.get().getAvaliacoes().get(0).getVotosUteis());
    }

    @Test
    public void segueParaAProximaPaginaQuandoAAtualVemCheia() {
        StringBuilder cheia = new StringBuilder("{\"content\":[");
        for (int i = 0; i < 50; i++) {
            cheia.append(i == 0 ? "" : ",").append(avaliacao(100 + i, 9, 5.0));
        }
        cheia.append("],\"last\":false}");
        api.em(PAGINA_0, 200, cheia.toString());
        api.em(PAGINA_1, 200, "{\"content\":[" + avaliacao(1, 9, 3.0) + "],\"last\":true}");

        AtomicReference<AvaliacoesRepository.Lidas> lidas = new AtomicReference<>();
        repositorio.buscar(7, lidas::set);

        assertEquals(51, lidas.get().getAvaliacoes().size());
    }

    @Test
    public void devolveNuloQuandoAListaFalha() {
        api.em(PAGINA_0, 500, "{}");

        AtomicReference<AvaliacoesRepository.Lidas> lidas = new AtomicReference<>();
        lidas.set(new AvaliacoesRepository.Lidas(java.util.Collections.emptyList(), false));
        repositorio.buscar(7, lidas::set);

        assertNull(lidas.get());
    }

    @Test
    public void publicaComOIdDaContaEAVersaoAtual() {
        api.em(PUBLICAR, 201, avaliacao(5, 42, 4.0));

        AtomicReference<AvaliacoesRepository.Envio> resultado = new AtomicReference<>();
        repositorio.publicar(7, 4, " Bom ", " Usei um mês ", true, resultado::set);

        assertEquals(AvaliacoesRepository.Envio.OK, resultado.get());
        JsonObject corpo = api.corpo(PUBLICAR);
        assertEquals(42, corpo.get("userId").getAsLong());
        assertEquals(31, corpo.get("productVersionId").getAsLong());
        assertEquals(4, corpo.get("rating").getAsInt());
        assertEquals("Bom", corpo.get("title").getAsString());
        assertEquals("Usei um mês", corpo.get("comment").getAsString());
        assertTrue(corpo.get("verifiedUse").getAsBoolean());
    }

    @Test
    public void avisaQuandoAPessoaJaAvaliou() {
        api.em(PUBLICAR, 409, "{}");

        AtomicReference<AvaliacoesRepository.Envio> resultado = new AtomicReference<>();
        repositorio.publicar(7, 4, "Bom", "Bom", false, resultado::set);

        assertEquals(AvaliacoesRepository.Envio.DUPLICADA, resultado.get());
    }

    @Test
    public void naoPublicaSemConta() {
        sessao.uid = null;

        AtomicReference<AvaliacoesRepository.Envio> resultado = new AtomicReference<>();
        repositorio.publicar(7, 4, "Bom", "Bom", false, resultado::set);

        assertEquals(AvaliacoesRepository.Envio.SEM_CONTA, resultado.get());
        assertTrue(api.pedidosEm(PUBLICAR).isEmpty());
    }

    @Test
    public void leOVotoDaPessoaETrataAusenciaComoSemVoto() {
        api.em(MEU_VOTO, 200, "{\"reviewId\":88,\"userId\":42,\"voteType\":\"NOT_USEFUL\"}");
        AtomicReference<Avaliacao.Voto> voto = new AtomicReference<>();
        AtomicReference<Boolean> leu = new AtomicReference<>();
        repositorio.meuVoto(88, (l, v) -> {
            leu.set(l);
            voto.set(v);
        });
        assertTrue(leu.get());
        assertEquals(Avaliacao.Voto.NAO_UTIL, voto.get());

        api.em(MEU_VOTO, 404, "{}");
        repositorio.meuVoto(88, (l, v) -> {
            leu.set(l);
            voto.set(v);
        });
        assertTrue(leu.get());
        assertNull(voto.get());
    }

    @Test
    public void votaPelaPrimeiraVezComPostETrocaComPatch() {
        api.em(VOTAR, 201, "{}");
        api.em(TROCAR_VOTO, 200, "{}");
        AtomicReference<AvaliacoesRepository.Envio> resultado = new AtomicReference<>();

        repositorio.votar(88, Avaliacao.Voto.UTIL, null, resultado::set);
        assertEquals(AvaliacoesRepository.Envio.OK, resultado.get());
        assertEquals("USEFUL", api.corpo(VOTAR).get("voteType").getAsString());
        assertEquals(42, api.corpo(VOTAR).get("userId").getAsLong());

        repositorio.votar(88, Avaliacao.Voto.NAO_UTIL, Avaliacao.Voto.UTIL, resultado::set);
        assertEquals(AvaliacoesRepository.Envio.OK, resultado.get());
        assertEquals("NOT_USEFUL", api.corpo(TROCAR_VOTO).get("voteType").getAsString());
    }

    @Test
    public void tirarOVotoApagaEAceitaVotoQueJaNaoExiste() {
        AtomicReference<AvaliacoesRepository.Envio> resultado = new AtomicReference<>();

        repositorio.votar(88, null, Avaliacao.Voto.UTIL, resultado::set);

        assertEquals(AvaliacoesRepository.Envio.OK, resultado.get());
        assertEquals(1, api.pedidosEm(APAGAR_VOTO).size());
    }

    @Test
    public void denunciaComoDenunciaAbertaDeAvaliacao() {
        api.em(DENUNCIAR, 201, "{}");
        AtomicReference<AvaliacoesRepository.Envio> resultado = new AtomicReference<>();

        repositorio.denunciar(88, "Spam ou propaganda. Link estranho", resultado::set);

        assertEquals(AvaliacoesRepository.Envio.OK, resultado.get());
        JsonObject corpo = api.corpo(DENUNCIAR);
        assertEquals(42, corpo.get("userId").getAsLong());
        assertEquals("REVIEW", corpo.get("targetType").getAsString());
        assertEquals(88, corpo.get("targetId").getAsLong());
        assertEquals("OPEN", corpo.get("status").getAsString());
        assertEquals("Spam ou propaganda. Link estranho", corpo.get("reason").getAsString());
        assertFalse(corpo.has("adminUserId"));
    }
}
