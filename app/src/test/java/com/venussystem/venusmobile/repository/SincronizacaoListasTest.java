package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.venussystem.venusmobile.model.Colecao;
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
import java.util.List;
import java.util.Set;

import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class SincronizacaoListasTest {

    private static final String BUSCA = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String POST_LISTA = "POST /api/user-lists";
    private static final String LISTAS_DA_ANA = "GET /api/user-lists/user/42?page=0&size=100";
    private static final String PATCH_7 = "PATCH /api/user-lists/7";
    private static final String DELETE_7 = "DELETE /api/user-lists/7";
    private static final String ITENS_7 = "GET /api/user-list-items/user-list/7";
    private static final String ITENS_8 = "GET /api/user-list-items/user-list/8";
    private static final String ITENS_9 = "GET /api/user-list-items/user-list/9";
    private static final String POST_ITEM = "POST /api/user-list-items";
    private static final String TIRAR_100_DA_7 = "DELETE /api/user-list-items/user-list/7/product/100";

    // As 3 de exemplo nascem com os ids 1, 2 e 3; a primeira criada e a 4.
    private static final long FAVORITOS = 1L;
    private static final long ESCANEADOS = 2L;
    private static final long SKINCARE = 3L;

    private MockWebServer server;
    private ApiPorRota api;
    private ColecaoRepository listas;
    private ListaItemRepository itens;
    private SincronizacaoListas sincronizacao;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        listas = new ColecaoRepository(context, "uid-ana");
        itens = new ListaItemRepository(context, "uid-ana");
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        UsuarioApiRepository usuarios =
                new UsuarioApiRepository(context, venusApi, new SessaoFalsa());
        sincronizacao = new SincronizacaoListas(listas, itens, usuarios, venusApi);

        api.em(BUSCA, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
        api.em(POST_LISTA, 201, "{\"id\":7,\"name\":\"Viagem\"}");
        api.em(ITENS_7, 200, "[]");
        api.em(POST_ITEM, 201, "{}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /** Cria a "Viagem" no aparelho e ja manda para a API, que devolve o id 7. */
    private long viagemJaNaApi() {
        long id = listas.criar("Viagem", "", null).getId();
        assertTrue(sincronizacao.sincronizarAgora());
        return id;
    }

    // ---- Quando nao tem nada para mandar ----

    @Test
    public void soAsListasDeExemploVazias_nemVaiNaRede() {
        listas.minhasListas();

        assertFalse(sincronizacao.sincronizarAgora());

        assertTrue(api.pedidos.isEmpty());
    }

    @Test
    public void jaEnviada_naoMandaDeNovo() {
        viagemJaNaApi();
        int antes = api.pedidos.size();

        assertFalse(sincronizacao.sincronizarAgora());

        assertEquals(antes, api.pedidos.size());
    }

    // ---- Criar ----

    @Test
    public void listaCriadaPelaPessoa_sobeComoPersonalizadaMesmoVazia() {
        listas.criar("Viagem", "Para levar na mala", null);

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(POST_LISTA);
        assertEquals(42, corpo.get("userId").getAsLong());
        assertEquals("Viagem", corpo.get("name").getAsString());
        assertEquals("CUSTOM", corpo.get("listType").getAsString());
        assertFalse("descricao ainda nao tem onde ir", corpo.has("description"));
    }

    @Test
    public void listaDeExemplo_sobeQuandoGanhaOPrimeiroProduto_comOTipoDela() {
        itens.adicionar(FAVORITOS, 100L);
        itens.adicionar(ESCANEADOS, 100L);
        itens.adicionar(SKINCARE, 100L);

        sincronizacao.sincronizarAgora();

        Set<String> tipos = new HashSet<>();
        for (String corpo : api.corposEm(POST_LISTA)) {
            tipos.add(JsonParser.parseString(corpo).getAsJsonObject().get("listType").getAsString());
        }
        assertEquals(new HashSet<>(Arrays.asList("FAVORITES", "CUSTOM", "ROUTINE")), tipos);
    }

    @Test
    public void produtosDaLista_vaoDoMaisAntigoParaOMaisRecente() {
        long viagem = listas.criar("Viagem", "", null).getId();
        itens.adicionar(viagem, 100L);
        itens.adicionar(viagem, 200L);

        assertTrue(sincronizacao.sincronizarAgora());

        // Na tela o 200 vem primeiro (mais recente); na API ele fica com a
        // maior posicao, e a ordem da maior para a menor e a mesma da tela.
        List<String> corpos = api.corposEm(POST_ITEM);
        assertEquals(2, corpos.size());
        assertEquals("{\"userListId\":7,\"productId\":100,\"positionOrder\":1}", corpos.get(0));
        assertEquals("{\"userListId\":7,\"productId\":200,\"positionOrder\":2}", corpos.get(1));
    }

    @Test
    public void produtoNovo_entraDepoisDaMaiorPosicaoQueJaEstaNaApi() {
        long viagem = viagemJaNaApi();
        api.em(ITENS_7, 200, "[{\"productId\":50,\"positionOrder\":5}]");
        itens.adicionar(viagem, 50L);
        itens.adicionar(viagem, 60L);

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(POST_ITEM);
        assertEquals(60, corpo.get("productId").getAsLong());
        assertEquals(6, corpo.get("positionOrder").getAsInt());
        assertEquals(1, api.corposEm(POST_ITEM).size());
    }

    // ---- Renomear e apagar ----

    @Test
    public void renomear_mandaSoONome() {
        long viagem = viagemJaNaApi();
        listas.renomear(viagem, "Férias");
        api.em(PATCH_7, 200, "{}");

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(PATCH_7);
        assertEquals("Férias", corpo.get("name").getAsString());
        assertFalse(corpo.has("listType"));
    }

    @Test
    public void excluirNoApp_apagaNaApi() {
        long viagem = viagemJaNaApi();
        listas.excluir(viagem);
        itens.excluirTodos(viagem);
        api.em(DELETE_7, 204, "");

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Collections.singletonList(DELETE_7), api.pedidosEm(DELETE_7));
        assertTrue(listas.idsParaApagarNaApi().isEmpty());
    }

    @Test
    public void produtoTiradoNoApp_saiDaListaNaApi() {
        long viagem = listas.criar("Viagem", "", null).getId();
        itens.adicionar(viagem, 100L);
        sincronizacao.sincronizarAgora();

        itens.remover(viagem, 100L);
        api.em(ITENS_7, 200, "[{\"productId\":100,\"positionOrder\":1}]");
        api.em(TIRAR_100_DA_7, 204, "");

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Collections.singletonList(TIRAR_100_DA_7), api.pedidosEm(TIRAR_100_DA_7));
    }

    // ---- Quando a API discorda ----

    @Test
    public void nomeQueJaExisteNaApi_assumeAListaDeLa() {
        // Uma tentativa anterior criou a lista mas perdeu a resposta.
        api.em(POST_LISTA, 409, "{\"status\":409}");
        api.em(LISTAS_DA_ANA, 200, "{\"content\":[{\"id\":9,\"name\":\"Viagem\"}],\"last\":true}");
        api.em(ITENS_9, 200, "[]");
        listas.criar("Viagem", "", null);

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Long.valueOf(9L), listas.paraSincronizar().get(0).idApi);
    }

    @Test
    public void listaApagadaNaApi_eCriadaDeNovo() {
        long viagem = viagemJaNaApi();
        listas.renomear(viagem, "Férias");
        // PATCH sem resposta configurada cai no 404 do ApiPorRota.

        assertFalse(sincronizacao.sincronizarAgora());

        api.em(POST_LISTA, 201, "{\"id\":8,\"name\":\"Férias\"}");
        api.em(ITENS_8, 200, "[]");
        assertTrue(sincronizacao.sincronizarAgora());
        assertEquals("Férias", api.corpo(POST_LISTA).get("name").getAsString());
    }

    @Test
    public void falhaDeRede_ficaPendenteETentaDeNovo() {
        api.em(POST_LISTA, 503, "{\"status\":503}");
        listas.criar("Viagem", "", null);

        assertFalse(sincronizacao.sincronizarAgora());

        api.em(POST_LISTA, 201, "{\"id\":7,\"name\":\"Viagem\"}");
        assertTrue(sincronizacao.sincronizarAgora());
    }

    @Test
    public void idDeOutraPessoa_403_buscaAPessoaERecriaAsListas() {
        long viagem = viagemJaNaApi();
        listas.renomear(viagem, "Férias");
        api.em(PATCH_7, 403, "{\"status\":403}");

        assertFalse(sincronizacao.sincronizarAgora());
        sincronizacao.sincronizarAgora();

        long buscas = api.pedidos.stream().filter(BUSCA::equals).count();
        assertEquals(2, buscas);
        assertEquals(2, api.corposEm(POST_LISTA).size());
    }

    // ---- Mudancas no meio do envio ----

    @Test
    public void produtoAdicionadoDuranteOEnvio_continuaPendente() {
        long viagem = listas.criar("Viagem", "", null).getId();
        itens.adicionar(viagem, 100L);
        api.aoReceber(POST_ITEM, () -> itens.adicionar(viagem, 999L));

        sincronizacao.sincronizarAgora();

        assertTrue(itens.temItensParaEnviar(viagem));
    }

    @Test
    public void listaExcluidaEnquantoEraCriada_eApagadaNaApiDepois() {
        long viagem = listas.criar("Viagem", "", null).getId();
        api.aoReceber(POST_LISTA, () -> listas.excluir(viagem));
        api.em(DELETE_7, 204, "");

        sincronizacao.sincronizarAgora();
        sincronizacao.sincronizarAgora();

        assertEquals(Collections.singletonList(DELETE_7), api.pedidosEm(DELETE_7));
    }

    @Test
    public void listasNaTela_continuamIguaisDepoisDoEnvio() {
        long viagem = viagemJaNaApi();

        List<Colecao> naTela = listas.minhasListas().getValue();

        assertEquals(4, naTela.size());
        assertEquals(viagem, (long) naTela.get(0).getId());
        assertEquals("Viagem", naTela.get(0).getName());
    }
}
