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
public class SincronizacaoCurtidosTest {

    private static final String BUSCA = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String CURTIR = "POST /api/favorites";
    private static final String DESCURTIR_100 = "DELETE /api/favorites/user/42/product/100";
    private static final String CURTIDOS_DA_ANA = "GET /api/favorites/user/42?page=0&size=100";

    private MockWebServer server;
    private ApiPorRota api;
    private CurtidosRepository curtidos;
    private UsuarioApiRepository usuarios;
    private SincronizacaoCurtidos sincronizacao;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        curtidos = new CurtidosRepository(context, "uid-ana");
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        usuarios = new UsuarioApiRepository(context, venusApi, new SessaoFalsa());
        sincronizacao = new SincronizacaoCurtidos(curtidos, usuarios, venusApi, Runnable::run);

        api.em(BUSCA, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
        api.em(CURTIR, 201, "{}");
        api.em(DESCURTIR_100, 204, "");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void nadaNaFila_nemVaiNaRede() {
        assertFalse(sincronizacao.sincronizarAgora());

        assertTrue(api.pedidos.isEmpty());
    }

    @Test
    public void curtir_mandaOPostComAPessoaEOProduto() {
        curtidos.curtir(100L);

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(CURTIR);
        assertEquals(42L, corpo.get("userId").getAsLong());
        assertEquals(100L, corpo.get("productId").getAsLong());
        assertFalse(curtidos.temAlgoParaEnviar());
    }

    @Test
    public void jaCurtidoNaApi_409_contaComoEnviado() {
        api.em(CURTIR, 409, "{\"message\":\"ja esta favoritado\"}");
        curtidos.curtir(100L);

        assertTrue(sincronizacao.sincronizarAgora());

        assertFalse(curtidos.temAlgoParaEnviar());
    }

    @Test
    public void descurtir_mandaODelete() {
        curtidos.curtir(100L);
        sincronizacao.sincronizarAgora();
        curtidos.descurtir(100L);

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Collections.singletonList(DESCURTIR_100), api.pedidosEm(DESCURTIR_100));
        assertFalse(curtidos.temAlgoParaEnviar());
    }

    @Test
    public void descurtirOQueNaoEstavaNaApi_404_contaComoEnviado() {
        api.em(DESCURTIR_100, 404, "{\"message\":\"nao esta favoritado\"}");
        curtidos.descurtir(100L);

        assertTrue(sincronizacao.sincronizarAgora());

        assertFalse(curtidos.temAlgoParaEnviar());
    }

    @Test
    public void servidorComErro_ficaNaFilaParaAProxima() {
        api.em(CURTIR, 500, "{}");
        curtidos.curtir(100L);

        assertFalse(sincronizacao.sincronizarAgora());

        assertEquals(Collections.singletonList(100L), curtidos.paraCurtirNaApi());
        assertTrue(curtidos.contem(100L));
    }

    @Test
    public void proibido_esqueceOIdDaPessoa() {
        api.em(CURTIR, 403, "{}");
        curtidos.curtir(100L);

        assertFalse(sincronizacao.sincronizarAgora());

        assertNull(usuarios.idEmCache());
        assertTrue(curtidos.temAlgoParaEnviar());
    }

    @Test
    public void atualizar_trazOsCurtidosDoSite_doMaisRecenteParaOMaisAntigo() {
        api.em(CURTIDOS_DA_ANA, 200, "{\"content\":["
                + "{\"id\":1,\"productId\":100},"
                + "{\"id\":5,\"productId\":200}],\"last\":true}");

        assertTrue(sincronizacao.atualizarAgora());

        assertEquals(Arrays.asList(200L, 100L), curtidos.getProdutoIds());
    }

    @Test
    public void atualizar_naoPerdeOCurtidoQueAindaNaoSubiu() {
        api.em(CURTIR, 500, "{}");
        api.em(CURTIDOS_DA_ANA, 200, "{\"content\":[{\"id\":1,\"productId\":100}],\"last\":true}");
        curtidos.curtir(300L);

        sincronizacao.atualizarAgora();

        assertEquals(Arrays.asList(300L, 100L), curtidos.getProdutoIds());
        assertEquals(Collections.singletonList(300L), curtidos.paraCurtirNaApi());
    }

    @Test
    public void atualizar_descurtidoPeloSite_saiDoAparelho() {
        curtidos.curtir(100L);
        sincronizacao.sincronizarAgora();
        api.em(CURTIDOS_DA_ANA, 200, "{\"content\":[],\"last\":true}");

        assertTrue(sincronizacao.atualizarAgora());

        assertTrue(curtidos.getProdutoIds().isEmpty());
    }

    @Test
    public void atualizar_comFalhaNaLeitura_naoMexeNoAparelho() {
        curtidos.curtir(100L);
        sincronizacao.sincronizarAgora();
        api.em(CURTIDOS_DA_ANA, 500, "{}");

        assertFalse(sincronizacao.atualizarAgora());

        assertEquals(Collections.singletonList(100L), curtidos.getProdutoIds());
    }
}
