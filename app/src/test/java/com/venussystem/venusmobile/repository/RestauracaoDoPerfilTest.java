package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.repository.api.PerfilDaApi;
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
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class RestauracaoDoPerfilTest {

    private static final String BUSCA = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String PERFIL_COMPLETO = "GET /api/users/42/full-profile";

    private static final String PERFIL_DO_WEB = "{"
            + "\"user\":{\"id\":42,\"firebaseUid\":\"uid-ana\"},"
            + "\"profile\":{\"gender\":\"FEMALE\",\"skinType\":\"DRY\",\"skinPhototype\":\"III\","
            + "\"hairPattern\":\"TYPE_1B\",\"scalpType\":\"NORMAL\",\"skinSensitivity\":\"HIGH\","
            + "\"ageRange\":\"AGE_18_24\",\"acneProne\":false,\"hasRosacea\":true,"
            + "\"hasEczema\":false,\"hasHyperpigmentation\":false,\"hasMelasma\":false,"
            + "\"isPregnant\":false,\"isBreastfeeding\":false},"
            + "\"preferences\":{\"preferVegan\":true,\"preferCrueltyFree\":false},"
            + "\"tags\":[{\"id\":37,\"slug\":\"ingredientes-naturais\","
            + "\"name\":\"Ingredientes Naturais\",\"category\":\"VALUES\"}],"
            + "\"allergies\":[{\"allergy\":{\"id\":9,\"allergyName\":\"Látex\"},"
            + "\"severity\":\"MEDIUM\"}],"
            + "\"favorites\":[],\"lists\":[]}";

    private MockWebServer server;
    private ApiPorRota api;
    private PerfilRepository perfil;
    private UsuarioApiRepository usuarios;
    private RestauracaoDoPerfil restauracao;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        perfil = new PerfilRepository(context, "uid-ana");
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        usuarios = new UsuarioApiRepository(context, venusApi, new SessaoFalsa());
        restauracao = new RestauracaoDoPerfil(usuarios, venusApi,
                Arrays.asList("Vegano", "Cruelty free", "Natural"), Runnable::run);

        api.em(BUSCA, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void contaRespondidaNoWeb_entraComOPerfilPronto() {
        api.em(PERFIL_COMPLETO, 200, PERFIL_DO_WEB);

        perfil.restaurar(restauracao.buscarAgora());

        assertTrue(perfil.jaRespondeuQuestionario());
        assertEquals("FEMALE", perfil.getTag(PerfilRepository.GENERO));
        assertEquals("DRY", perfil.getTag(PerfilRepository.TIPO_PELE));
        assertEquals("TYPE_1B", perfil.getTag(PerfilRepository.TIPO_CABELO));
        assertEquals("AGE_18_24", perfil.getTag(PerfilRepository.FAIXA_ETARIA));
        assertEquals(Collections.singletonList("hasRosacea"),
                perfil.getLista(PerfilRepository.CONDICOES_PELE));
        assertEquals(Collections.singletonList(PerfilRepository.NENHUMA),
                perfil.getLista(PerfilRepository.GESTACAO));
        assertEquals(Arrays.asList("Vegano", "Natural"),
                perfil.getLista(PerfilRepository.PREFERENCIAS));
        assertEquals(Collections.singletonList("Látex"),
                perfil.getLista(PerfilRepository.ALERGIAS));
        assertEquals(new HashSet<>(Collections.singletonList(9L)),
                perfil.getIdsNaApi(PerfilRepository.ALERGIAS_NA_API));
        assertEquals(new HashSet<>(Collections.singletonList(37L)),
                perfil.getIdsNaApi(PerfilRepository.ETIQUETAS_NA_API));
    }

    @Test
    public void perfilRestaurado_naoEReenviadoParaAApi() {
        api.em(PERFIL_COMPLETO, 200, PERFIL_DO_WEB);

        perfil.restaurar(restauracao.buscarAgora());

        assertFalse(perfil.temAlteracaoParaEnviar());
    }

    @Test
    public void rotuloAntigoDaResposta_saiQuandoATagMuda() {
        perfil.salvarResposta(PerfilRepository.TIPO_PELE, "OILY", "Oleoso");
        api.em(PERFIL_COMPLETO, 200, PERFIL_DO_WEB);

        perfil.restaurar(restauracao.buscarAgora());

        assertEquals("DRY", perfil.getTag(PerfilRepository.TIPO_PELE));
        assertNull(perfil.getRotulo(PerfilRepository.TIPO_PELE));
    }

    @Test
    public void contaSemPerfilNaApi_segueParaOQuestionario() {
        api.em(PERFIL_COMPLETO, 200, "{\"user\":{\"id\":42},\"profile\":null}");

        assertNull(restauracao.buscarAgora());
    }

    @Test
    public void apiFora_segueParaOQuestionario() {
        api.em(PERFIL_COMPLETO, 503, "{}");

        assertNull(restauracao.buscarAgora());
    }

    @Test
    public void respostaQuebrada_naoDerrubaOLogin() {
        api.em(PERFIL_COMPLETO, 200, "{\"profile\":[1,2]}");

        assertNull(restauracao.buscarAgora());
    }

    @Test
    public void tokenRecusado_esqueceOIdGuardado() {
        api.em(PERFIL_COMPLETO, 403, "{}");

        assertNull(restauracao.buscarAgora());
        assertNull(usuarios.idEmCache());
    }

    @Test
    public void buscaEmSegundoPlano_entregaOResultado() throws InterruptedException {
        api.em(PERFIL_COMPLETO, 200, PERFIL_DO_WEB);
        PerfilDaApi[] entregue = new PerfilDaApi[1];
        CountDownLatch terminou = new CountDownLatch(1);

        restauracao.buscarEmSegundoPlano(daApi -> {
            entregue[0] = daApi;
            terminou.countDown();
        });

        assertTrue(terminou.await(5, TimeUnit.SECONDS));
        assertEquals("FEMALE", entregue[0].tags.get(PerfilRepository.GENERO));
    }
}
