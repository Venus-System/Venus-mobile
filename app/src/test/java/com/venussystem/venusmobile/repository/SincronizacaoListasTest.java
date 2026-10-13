package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.venussystem.venusmobile.R;
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

import java.io.File;
import java.io.FileOutputStream;
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
    private static final String CAPA_7 = "POST /api/user-lists/7/cover";
    private static final String LINK_CAPA_7 = "https://res.cloudinary.com/venus/user-lists/7/cover/a.jpg";

    // Ids das 3 listas de exemplo numa conta antiga (ver contaComAsListasDeExemplo).
    private static final long FAVORITOS = 1L;
    private static final long ESCANEADOS = 2L;
    private static final long SKINCARE = 3L;

    private Context context;
    private MockWebServer server;
    private ApiPorRota api;
    private ColecaoRepository listas;
    private ListaItemRepository itens;
    private UsuarioApiRepository usuarios;
    private SincronizacaoListas sincronizacao;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        context = ApplicationProvider.getApplicationContext();
        listas = new ColecaoRepository(context, "uid-ana");
        itens = new ListaItemRepository(context, "uid-ana");
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        usuarios = new UsuarioApiRepository(context, venusApi, new SessaoFalsa());
        // Nos testes a capa vai sem reduzir: o ImageDecoder nao abre os bytes de mentira.
        sincronizacao = new SincronizacaoListas(listas, itens, usuarios, venusApi,
                original -> original, Runnable::run);

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

    /**
     * Conta de antes desta versao em que as 3 listas de exemplo ficaram no
     * aparelho, ja revistas (ver ColecaoRepository.tirarExemplosIntocados).
     */
    private void contaComAsListasDeExemplo() {
        DadosDaConta.prefs(context, ColecaoRepository.ARQUIVO, "uid-ana").edit()
                .putString("minhas_listas", "["
                        + "{\"id\":1,\"nome\":\"Produtos favoritados\",\"chaveImagem\":\"favoritos\"},"
                        + "{\"id\":2,\"nome\":\"Produtos escaneados\",\"chaveImagem\":\"escaneados\"},"
                        + "{\"id\":3,\"nome\":\"Rotina de skincare\",\"chaveImagem\":\"skincare\"}]")
                .putBoolean(ColecaoRepository.CHAVE_EXEMPLOS_REVISTOS, true)
                .commit();
    }

    /** Uma foto de capa ja copiada para o aparelho, como a tela deixa. */
    private String capaNoAparelho(String nome) throws IOException {
        File pasta = new File(context.getFilesDir(), "capas_lista");
        pasta.mkdirs();
        File arquivo = new File(pasta, nome);
        try (FileOutputStream saida = new FileOutputStream(arquivo)) {
            saida.write(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3});
        }
        return arquivo.toURI().toString();
    }

    // ---- Quando nao tem nada para mandar ----

    @Test
    public void contaSemListas_nemVaiNaRede() {
        listas.minhasListas();

        assertFalse(sincronizacao.sincronizarAgora());

        assertTrue(api.pedidos.isEmpty());
    }

    @Test
    public void soAsListasDeExemploVazias_nemVaiNaRede() {
        contaComAsListasDeExemplo();
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
    }

    @Test
    public void listaCriadaPelaPessoa_sobeComADescricaoESemCapaPadrao() {
        listas.criar("Viagem", "Para levar na mala", null);

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(POST_LISTA);
        assertEquals("Para levar na mala", corpo.get("description").getAsString());
        assertFalse(corpo.has("coverKey"));
    }

    @Test
    public void listaSemDescricao_naoMandaDescricaoEmBranco() {
        listas.criar("Viagem", "  ", null);

        assertTrue(sincronizacao.sincronizarAgora());

        assertFalse(api.corpo(POST_LISTA).has("description"));
    }

    @Test
    public void listaDeExemplo_sobeComACapaPadraoEmMaiusculo() {
        contaComAsListasDeExemplo();
        itens.adicionar(FAVORITOS, 100L);
        itens.adicionar(ESCANEADOS, 100L);
        itens.adicionar(SKINCARE, 100L);

        sincronizacao.sincronizarAgora();

        Set<String> capas = new HashSet<>();
        for (String corpo : api.corposEm(POST_LISTA)) {
            capas.add(JsonParser.parseString(corpo).getAsJsonObject().get("coverKey").getAsString());
        }
        // A API so aceita em maiusculo: "favoritos" da 400.
        assertEquals(new HashSet<>(Arrays.asList("FAVORITOS", "ESCANEADOS", "SKINCARE")), capas);
    }

    @Test
    public void listaDeExemplo_sobeQuandoGanhaOPrimeiroProduto_comOTipoDela() {
        contaComAsListasDeExemplo();
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
        assertFalse("sem o campo, a API mantem a descricao", corpo.has("description"));
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

    // ---- Descricao e capa padrao ----

    @Test
    public void descricaoEditada_mandaSoADescricao() {
        long viagem = viagemJaNaApi();
        listas.atualizarDescricao(viagem, "Para levar na mala");
        api.em(PATCH_7, 200, "{}");

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(PATCH_7);
        assertEquals("Para levar na mala", corpo.get("description").getAsString());
        assertFalse(corpo.has("name"));
    }

    @Test
    public void descricaoApagada_mandaNullParaAApiApagar() {
        long viagem = listas.criar("Viagem", "Para levar na mala", null).getId();
        assertTrue(sincronizacao.sincronizarAgora());
        listas.atualizarDescricao(viagem, "");
        api.em(PATCH_7, 200, "{}");

        assertTrue(sincronizacao.sincronizarAgora());

        // No PATCH, campo omitido mantem o valor; so o null apaga.
        JsonObject corpo = api.corpo(PATCH_7);
        assertTrue(corpo.has("description"));
        assertTrue(corpo.get("description").isJsonNull());
    }

    @Test
    public void descricaoEditadaDuranteOEnvio_continuaPendente() {
        long viagem = viagemJaNaApi();
        listas.atualizarDescricao(viagem, "Praia");
        api.em(PATCH_7, 200, "{}");
        api.aoReceber(PATCH_7, () -> listas.atualizarDescricao(viagem, "Serra"));

        sincronizacao.sincronizarAgora();
        sincronizacao.sincronizarAgora();

        assertEquals("Serra", api.corpo(PATCH_7).get("description").getAsString());
    }

    @Test
    public void listaDeExemploQueSubiuSemCapaPadrao_recebeACapaDepois() {
        // Gravado pela versao que ja mandava as listas, mas ainda sem a capa.
        DadosDaConta.prefs(context, ColecaoRepository.ARQUIVO, "uid-ana").edit()
                .putString("minhas_listas", "[{\"id\":1,\"nome\":\"Produtos favoritados\","
                        + "\"chaveImagem\":\"favoritos\",\"idApi\":7,"
                        + "\"nomeNaApi\":\"Produtos favoritados\"}]")
                .commit();
        api.em(PATCH_7, 200, "{}");

        assertTrue(sincronizacao.sincronizarAgora());

        JsonObject corpo = api.corpo(PATCH_7);
        assertEquals("FAVORITOS", corpo.get("coverKey").getAsString());
        assertFalse(corpo.has("name"));
        assertFalse(corpo.has("description"));
    }

    // ---- Foto de capa ----

    @Test
    public void capaEscolhidaAoCriar_vaiNoCampoFileDepoisDaLista() throws IOException {
        listas.criar("Viagem", "", capaNoAparelho("capa_a.jpg"));
        api.em(CAPA_7, 201, "{\"url\":\"" + LINK_CAPA_7 + "\"}");

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(Arrays.asList(POST_LISTA, CAPA_7), api.pedidosEm(POST_LISTA, CAPA_7));
        String corpo = api.corposEm(CAPA_7).get(0);
        assertTrue(corpo.contains("name=\"file\"; filename=\"capa_a.jpg\""));
        assertTrue(corpo.contains("Content-Type: image/jpeg"));
    }

    @Test
    public void capaTrocadaNumaListaQueJaEstaNaApi_vaiSemMexerNoResto() throws IOException {
        long viagem = viagemJaNaApi();
        listas.atualizarImagem(viagem, capaNoAparelho("capa_b.jpg"));
        api.em(CAPA_7, 201, "{\"url\":\"" + LINK_CAPA_7 + "\"}");

        assertTrue(sincronizacao.sincronizarAgora());

        assertEquals(1, api.corposEm(CAPA_7).size());
        assertTrue(api.pedidosEm(PATCH_7).isEmpty());
    }

    @Test
    public void capaJaEnviada_naoMandaDeNovo() throws IOException {
        listas.criar("Viagem", "", capaNoAparelho("capa_a.jpg"));
        api.em(CAPA_7, 201, "{\"url\":\"" + LINK_CAPA_7 + "\"}");
        sincronizacao.sincronizarAgora();
        int antes = api.pedidos.size();

        assertFalse(sincronizacao.sincronizarAgora());

        assertEquals(antes, api.pedidos.size());
    }

    @Test
    public void falhaAoEnviarACapa_ficaPendente() throws IOException {
        listas.criar("Viagem", "", capaNoAparelho("capa_a.jpg"));
        api.em(CAPA_7, 503, "{\"status\":503}");

        assertFalse(sincronizacao.sincronizarAgora());

        api.em(CAPA_7, 201, "{\"url\":\"" + LINK_CAPA_7 + "\"}");
        assertTrue(sincronizacao.sincronizarAgora());
        assertEquals(2, api.corposEm(CAPA_7).size());
    }

    @Test
    public void capaRecusadaPelaApi_ficaSoNoAparelho() throws IOException {
        listas.criar("Viagem", "", capaNoAparelho("capa_a.jpg"));
        api.em(CAPA_7, 413, "{\"status\":413}");

        assertTrue(sincronizacao.sincronizarAgora());
        sincronizacao.sincronizarAgora();

        // Mandar a mesma imagem de novo nao adianta.
        assertEquals(1, api.corposEm(CAPA_7).size());
    }

    @Test
    public void capaTrocadaDuranteOEnvio_continuaPendente() throws IOException {
        long viagem = viagemJaNaApi();
        String capaB = capaNoAparelho("capa_b.jpg");
        listas.atualizarImagem(viagem, capaNoAparelho("capa_a.jpg"));
        api.em(CAPA_7, 201, "{\"url\":\"" + LINK_CAPA_7 + "\"}");
        api.aoReceber(CAPA_7, () -> listas.atualizarImagem(viagem, capaB));

        sincronizacao.sincronizarAgora();
        sincronizacao.sincronizarAgora();

        List<String> enviadas = api.corposEm(CAPA_7);
        assertEquals(2, enviadas.size());
        assertTrue(enviadas.get(1).contains("filename=\"capa_b.jpg\""));
    }

    @Test
    public void arquivoDaCapaSumiu_naoTravaALista() throws IOException {
        String capa = capaNoAparelho("capa_a.jpg");
        listas.criar("Viagem", "", capa);
        new File(java.net.URI.create(capa)).delete();

        assertTrue(sincronizacao.sincronizarAgora());

        assertTrue(api.pedidosEm(CAPA_7).isEmpty());
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
    public void listaApagadaNaApi_saiDoAparelhoSemSerRecriada() {
        long viagem = viagemJaNaApi();
        itens.adicionar(viagem, 100L);
        listas.renomear(viagem, "Férias");
        // PATCH sem resposta configurada cai no 404 do ApiPorRota.

        sincronizacao.sincronizarAgora();
        sincronizacao.sincronizarAgora();

        assertEquals(1, api.corposEm(POST_LISTA).size());
        assertTrue(listas.minhasListas().getValue().isEmpty());
        assertTrue(itens.getProdutoIds(viagem).isEmpty());
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

        assertEquals(1, naTela.size());
        assertEquals(viagem, (long) naTela.get(0).getId());
        assertEquals("Viagem", naTela.get(0).getName());
    }

    // ---- Ler do servidor ----

    private void naApi(String... listas) {
        api.em(LISTAS_DA_ANA, 200,
                "{\"content\":[" + String.join(",", listas) + "],\"last\":true}");
    }

    private Colecao naTela(long id) {
        for (Colecao colecao : listas.minhasListas().getValue()) {
            if (colecao.getId() == id) {
                return colecao;
            }
        }
        throw new AssertionError("a lista " + id + " nao esta na tela");
    }

    @Test
    public void listaFeitaNoSite_apareceNoAparelhoComOsProdutos() {
        naApi("{\"id\":9,\"name\":\"Do site\",\"description\":\"Feita no site\"}");
        api.em(ITENS_9, 200, "[{\"productId\":100,\"positionOrder\":1},"
                + "{\"productId\":200,\"positionOrder\":2}]");

        assertTrue(sincronizacao.atualizarAgora());

        List<Colecao> telaToda = listas.minhasListas().getValue();
        assertEquals(1, telaToda.size());
        Colecao doSite = telaToda.get(0);
        assertEquals("Do site", doSite.getName());
        assertEquals("Feita no site", doSite.getDescricao());
        // Na tela o mais recente vem primeiro: a maior posicao.
        assertEquals(Arrays.asList(200L, 100L), itens.getProdutoIds(doSite.getId()));
        assertFalse("o que veio da API nao volta para ela", sincronizacao.sincronizarAgora());
    }

    @Test
    public void listasDoSite_entramNoTopoDaMaisNovaParaAMaisAntiga() {
        naApi("{\"id\":9,\"name\":\"Antiga\"}", "{\"id\":10,\"name\":\"Nova\"}");
        api.em(ITENS_9, 200, "[]");
        api.em("GET /api/user-list-items/user-list/10", 200, "[]");

        sincronizacao.atualizarAgora();

        List<Colecao> telaToda = listas.minhasListas().getValue();
        assertEquals("Nova", telaToda.get(0).getName());
        assertEquals("Antiga", telaToda.get(1).getName());
    }

    @Test
    public void listaComCapaPadraoNaApi_entraNaContaSemListas() {
        // Celular novo: a conta ja tem favoritos no servidor.
        naApi("{\"id\":9,\"name\":\"Produtos favoritados\",\"coverKey\":\"FAVORITOS\"}");
        api.em(ITENS_9, 200, "[{\"productId\":100,\"positionOrder\":1}]");

        assertTrue(sincronizacao.atualizarAgora());

        List<Colecao> telaToda = listas.minhasListas().getValue();
        assertEquals(1, telaToda.size());
        assertEquals("Produtos favoritados", telaToda.get(0).getName());
        assertEquals(R.drawable.capa_favoritos, telaToda.get(0).getImagemLocal());
        assertEquals(Arrays.asList(100L), itens.getProdutoIds(telaToda.get(0).getId()));
    }

    @Test
    public void listaDeExemploNaApi_eAMesmaDoAparelho() {
        // Celular com as listas de exemplo antigas; a conta ja tem favoritos no servidor.
        contaComAsListasDeExemplo();
        naApi("{\"id\":9,\"name\":\"Produtos favoritados\",\"coverKey\":\"FAVORITOS\"}");
        api.em(ITENS_9, 200, "[{\"productId\":100,\"positionOrder\":1}]");

        assertTrue(sincronizacao.atualizarAgora());

        assertEquals(3, listas.minhasListas().getValue().size());
        assertEquals(Arrays.asList(100L), itens.getProdutoIds(FAVORITOS));
    }

    @Test
    public void nomeEDescricaoMudadosNoSite_chegamNoAparelho() {
        long viagem = viagemJaNaApi();
        naApi("{\"id\":7,\"name\":\"Férias\",\"description\":\"Praia\"}");

        assertTrue(sincronizacao.atualizarAgora());

        assertEquals("Férias", naTela(viagem).getName());
        assertEquals("Praia", naTela(viagem).getDescricao());
        assertFalse(sincronizacao.sincronizarAgora());
    }

    @Test
    public void nomeMudadoNoAparelhoQueAindaNaoSubiu_naoEDesfeito() {
        long viagem = viagemJaNaApi();
        listas.renomear(viagem, "Férias");
        api.em(PATCH_7, 503, "{\"status\":503}");
        naApi("{\"id\":7,\"name\":\"Viagem\"}");

        sincronizacao.atualizarAgora();

        assertEquals("Férias", naTela(viagem).getName());
    }

    @Test
    public void produtosMudadosNoSite_chegamNoAparelho() {
        long viagem = viagemJaNaApi();
        naApi("{\"id\":7,\"name\":\"Viagem\"}");
        api.em(ITENS_7, 200, "[{\"productId\":300,\"positionOrder\":1}]");

        assertTrue(sincronizacao.atualizarAgora());

        assertEquals(Arrays.asList(300L), itens.getProdutoIds(viagem));
    }

    @Test
    public void produtosQueAindaNaoSubiram_naoSaoTrocados() {
        long viagem = viagemJaNaApi();
        itens.adicionar(viagem, 100L);
        api.em(POST_ITEM, 503, "{\"status\":503}");
        naApi("{\"id\":7,\"name\":\"Viagem\"}");

        sincronizacao.atualizarAgora();

        assertEquals(Arrays.asList(100L), itens.getProdutoIds(viagem));
    }

    @Test
    public void listaApagadaNoSite_saiDoAparelho() {
        long viagem = viagemJaNaApi();
        naApi();

        assertTrue(sincronizacao.atualizarAgora());

        assertTrue(listas.minhasListas().getValue().isEmpty());
        assertTrue("nao manda apagar o que ja nao existe", api.pedidosEm(DELETE_7).isEmpty());
        assertTrue(listas.idsParaApagarNaApi().isEmpty());
    }

    @Test
    public void listaExcluidaNoAparelhoQueAindaEstaNoServidor_naoVolta() {
        long viagem = viagemJaNaApi();
        listas.excluir(viagem);
        api.em(DELETE_7, 503, "{\"status\":503}");
        naApi("{\"id\":7,\"name\":\"Viagem\"}");

        sincronizacao.atualizarAgora();

        assertTrue(listas.minhasListas().getValue().isEmpty());
    }

    @Test
    public void capaDoSite_apareceNaLista() {
        naApi("{\"id\":9,\"name\":\"Do site\",\"coverUrl\":\"" + LINK_CAPA_7 + "\"}");
        api.em(ITENS_9, 200, "[]");

        sincronizacao.atualizarAgora();

        assertEquals(LINK_CAPA_7, listas.minhasListas().getValue().get(0).getImageUrl());
    }

    @Test
    public void capaEnviadaPeloAparelho_continuaComOArquivoLocal() throws IOException {
        String capa = capaNoAparelho("capa_a.jpg");
        long viagem = listas.criar("Viagem", "", capa).getId();
        api.em(CAPA_7, 201, "{\"url\":\"" + LINK_CAPA_7 + "\"}");
        naApi("{\"id\":7,\"name\":\"Viagem\",\"coverUrl\":\"" + LINK_CAPA_7 + "\"}");

        sincronizacao.atualizarAgora();

        assertEquals(capa, naTela(viagem).getImageUrl());
    }

    @Test
    public void falhaAoLerAsListas_naoApagaNada() {
        viagemJaNaApi();
        api.em(LISTAS_DA_ANA, 503, "{\"status\":503}");

        assertFalse(sincronizacao.atualizarAgora());

        assertEquals(1, listas.minhasListas().getValue().size());
    }

    @Test
    public void nadaMudouNoServidor_naoAvisaATela() {
        viagemJaNaApi();
        naApi("{\"id\":7,\"name\":\"Viagem\"}");

        assertFalse(sincronizacao.atualizarAgora());
    }

    @Test
    public void bancoRecriadoComOutroIdDaPessoa_recriaAsListasEmVezDeApagar() {
        long viagem = viagemJaNaApi();
        // O banco foi recriado: a pessoa agora e a 43 e nao tem lista nenhuma.
        usuarios.esquecerId();
        api.em(BUSCA, 200, "{\"content\":[{\"id\":43,\"firebaseUid\":\"uid-ana\"}]}");
        api.em(POST_LISTA, 201, "{\"id\":8,\"name\":\"Viagem\"}");
        api.em(ITENS_8, 200, "[]");
        api.em("GET /api/user-lists/user/43?page=0&size=100", 200,
                "{\"content\":[{\"id\":8,\"name\":\"Viagem\"}],\"last\":true}");

        sincronizacao.atualizarAgora();

        assertEquals("Viagem", naTela(viagem).getName());
        assertEquals(43, api.corpo(POST_LISTA).get("userId").getAsLong());
    }
}
