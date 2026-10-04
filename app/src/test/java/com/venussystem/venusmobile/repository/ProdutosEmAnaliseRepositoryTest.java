package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockWebServer;

import static com.venussystem.venusmobile.testutil.LiveDataEspera.aguardarLatch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ProdutosEmAnaliseRepositoryTest {

    private static final String BUSCA_USUARIO = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String SCANS = "GET /api/scan-sessions/user/42?page=0&size=50";

    private MockWebServer server;
    private ApiPorRota api;
    private SessaoFalsa sessao;
    private ProdutosEmAnaliseRepository repositorio;
    private TimeZone fusoOriginal;

    @Before
    public void setUp() throws IOException {
        fusoOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));

        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        sessao = new SessaoFalsa();
        UsuarioApiRepository usuarios = new UsuarioApiRepository(context, venusApi, sessao);
        usuarios.esquecerId();
        repositorio = new ProdutosEmAnaliseRepository(usuarios, venusApi, Runnable::run);

        api.em(BUSCA_USUARIO, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        TimeZone.setDefault(fusoOriginal);
    }

    // ---- Montagem do JSON que a API devolve ----

    private static JsonObject scan(String id, String status) {
        JsonObject scan = new JsonObject();
        scan.addProperty("id", id);
        scan.addProperty("scanId", "scan-" + id);
        scan.addProperty("status", status);
        scan.addProperty("createdAt", "2026-10-02T14:30:00-03:00");
        return scan;
    }

    private static JsonObject comNomeEMarca(JsonObject scan, String nome, String marca) {
        JsonObject extraido = new JsonObject();
        extraido.addProperty("productName", nome);
        extraido.addProperty("brand", marca);
        JsonObject frente = new JsonObject();
        frente.add("extracted", extraido);
        JsonObject ocr = new JsonObject();
        ocr.add("front", frente);
        scan.add("ocr", ocr);
        return scan;
    }

    private static JsonObject comFotos(JsonObject scan, String frente, String verso) {
        JsonObject imagens = new JsonObject();
        imagens.add("front", foto(frente));
        imagens.add("back", foto(verso));
        scan.add("images", imagens);
        return scan;
    }

    private static JsonObject foto(String link) {
        JsonObject foto = new JsonObject();
        foto.addProperty("secureUrl", link);
        return foto;
    }

    private static JsonObject comDecisao(JsonObject scan, String quando, String motivo) {
        JsonObject decisao = new JsonObject();
        decisao.addProperty("decidedAt", quando);
        decisao.addProperty("reason", motivo);
        scan.add("review", decisao);
        return scan;
    }

    private static JsonObject comProduto(JsonObject scan, long produtoId) {
        JsonObject sync = new JsonObject();
        sync.addProperty("productId", produtoId);
        scan.add("sync", sync);
        return scan;
    }

    private void apiDevolve(JsonObject... scans) {
        JsonArray conteudo = new JsonArray();
        for (JsonObject scan : scans) {
            conteudo.add(scan);
        }
        JsonObject fatia = new JsonObject();
        fatia.add("content", conteudo);
        fatia.addProperty("last", true);
        api.em(SCANS, 200, fatia.toString());
    }

    private List<Situacao> situacoes(List<ProdutoEmAnalise> produtos) {
        List<Situacao> situacoes = new ArrayList<>();
        for (ProdutoEmAnalise produto : produtos) {
            situacoes.add(produto.getSituacao());
        }
        return situacoes;
    }

    // ---- Status ----

    @Test
    public void osSeisStatusDaApi_viramOsQuatroSelos() {
        apiDevolve(scan("1", "PENDING_REVIEW"), scan("2", "IN_REVIEW"), scan("3", "APPROVED"),
                scan("4", "SYNCED"), scan("5", "SYNC_FAILED"), scan("6", "REJECTED"));

        List<ProdutoEmAnalise> produtos = repositorio.buscarAgora();

        assertEquals(Arrays.asList(Situacao.AGUARDANDO, Situacao.EM_ANALISE, Situacao.APROVADO,
                Situacao.APROVADO, Situacao.APROVADO, Situacao.RECUSADO), situacoes(produtos));
    }

    @Test
    public void statusQueOAppNaoConhece_ficaAguardando() {
        apiDevolve(scan("1", "ON_HOLD"));

        assertEquals(Situacao.AGUARDANDO, repositorio.buscarAgora().get(0).getSituacao());
    }

    @Test
    public void mantemAOrdemDaApi_doMaisRecenteParaOMaisAntigo() {
        apiDevolve(scan("b", "PENDING_REVIEW"), scan("a", "REJECTED"));

        List<ProdutoEmAnalise> produtos = repositorio.buscarAgora();

        assertEquals("b", produtos.get(0).getId());
        assertEquals("a", produtos.get(1).getId());
    }

    // ---- O que aparece no cartao ----

    @Test
    public void nomeEMarca_vemDoOcrDaFrente() {
        apiDevolve(comNomeEMarca(scan("1", "PENDING_REVIEW"), "Cuide-se Bem Rosa", "oBoticario"));

        ProdutoEmAnalise produto = repositorio.buscarAgora().get(0);

        assertEquals("Cuide-se Bem Rosa", produto.getNome());
        assertEquals("oBoticario", produto.getMarca());
    }

    @Test
    public void ocrSemNomeOuEmBranco_ficaSemNome() {
        apiDevolve(scan("1", "PENDING_REVIEW"),
                comNomeEMarca(scan("2", "PENDING_REVIEW"), "  ", ""));

        List<ProdutoEmAnalise> produtos = repositorio.buscarAgora();

        assertNull(produtos.get(0).getNome());
        assertNull(produtos.get(0).getMarca());
        assertNull(produtos.get(1).getNome());
        assertNull(produtos.get(1).getMarca());
    }

    @Test
    public void fotosDaFrenteEDoVerso_vemDasImagensDoScan() {
        apiDevolve(comFotos(scan("1", "PENDING_REVIEW"),
                "https://res.cloudinary.com/venus/scans/1/front",
                "https://res.cloudinary.com/venus/scans/1/back"));

        ProdutoEmAnalise produto = repositorio.buscarAgora().get(0);

        assertEquals("https://res.cloudinary.com/venus/scans/1/front", produto.getFotoFrente());
        assertEquals("https://res.cloudinary.com/venus/scans/1/back", produto.getFotoVerso());
    }

    @Test
    public void dataDeEnvio_eODiaNoFusoDoAparelho() {
        JsonObject comFusoDoBrasil = scan("1", "PENDING_REVIEW");
        comFusoDoBrasil.addProperty("createdAt", "2026-10-02T23:30:00-03:00");
        // Mesmo instante em UTC: ja e dia 3 la, mas no aparelho ainda e dia 2.
        JsonObject emUtc = scan("2", "PENDING_REVIEW");
        emUtc.addProperty("createdAt", "2026-10-03T02:30:00Z");

        apiDevolve(comFusoDoBrasil, emUtc);
        List<ProdutoEmAnalise> produtos = repositorio.buscarAgora();

        assertEquals(LocalDate.of(2026, 10, 2), produtos.get(0).getEnviadoEm());
        assertEquals(LocalDate.of(2026, 10, 2), produtos.get(1).getEnviadoEm());
    }

    @Test
    public void dataQueNaoDaParaLer_ficaSemData() {
        JsonObject semData = scan("1", "PENDING_REVIEW");
        semData.remove("createdAt");
        JsonObject estragada = scan("2", "PENDING_REVIEW");
        estragada.addProperty("createdAt", "ontem");

        apiDevolve(semData, estragada);
        List<ProdutoEmAnalise> produtos = repositorio.buscarAgora();

        assertNull(produtos.get(0).getEnviadoEm());
        assertNull(produtos.get(1).getEnviadoEm());
    }

    // ---- Decisao ----

    @Test
    public void recusado_trazOMotivoEODiaDaDecisao() {
        apiDevolve(comDecisao(scan("1", "REJECTED"),
                "2026-10-03T10:15:00-03:00", "Foto do verso ilegível"));

        ProdutoEmAnalise produto = repositorio.buscarAgora().get(0);

        assertEquals("Foto do verso ilegível", produto.getMotivo());
        assertEquals(LocalDate.of(2026, 10, 3), produto.getDecididoEm());
    }

    @Test
    public void sincronizado_trazOProdutoDoCatalogo() {
        apiDevolve(comProduto(scan("1", "SYNCED"), 57L));

        assertEquals(Long.valueOf(57L), repositorio.buscarAgora().get(0).getProdutoId());
    }

    @Test
    public void aprovadoQueAindaNaoEntrouNoCatalogo_naoTemProduto() {
        // Sem SYNCED o produto pode nao existir no catalogo: abrir a tela dele
        // daria "produto nao encontrado".
        apiDevolve(comProduto(scan("1", "APPROVED"), 57L),
                comProduto(scan("2", "SYNC_FAILED"), 58L));

        List<ProdutoEmAnalise> produtos = repositorio.buscarAgora();

        assertNull(produtos.get(0).getProdutoId());
        assertNull(produtos.get(1).getProdutoId());
    }

    // ---- Falhas ----

    @Test
    public void semNenhumEnvio_devolveListaVazia() {
        apiDevolve();

        List<ProdutoEmAnalise> produtos = repositorio.buscarAgora();

        assertNotNull(produtos);
        assertTrue(produtos.isEmpty());
    }

    @Test
    public void apiComErro_devolveNull() {
        api.em(SCANS, 500, "{}");

        assertNull(repositorio.buscarAgora());
    }

    @Test
    public void semNinguemLogado_naoVaiNaRede() {
        sessao.uid = null;

        assertNull(repositorio.buscarAgora());
        assertTrue(api.pedidos.isEmpty());
    }

    @Test
    public void naoAchouOIdDaPessoa_naoPedeOsScans() {
        api.em(BUSCA_USUARIO, 500, "{}");

        assertNull(repositorio.buscarAgora());
        assertTrue(api.pedidosEm(SCANS).isEmpty());
    }

    @Test
    public void proibido_esqueceOIdParaBuscarDeNovoNaProximaVez() {
        // 403 com id guardado: o id pode ser de outra conta (banco recriado).
        api.em(SCANS, 403, "{}");
        repositorio.buscarAgora();
        apiDevolve();

        repositorio.buscarAgora();

        assertEquals(2, api.pedidosEm(BUSCA_USUARIO).size());
    }

    @Test
    public void usuarioNaoEncontrado_tambemEsqueceOId() {
        api.em(SCANS, 404, "{}");
        repositorio.buscarAgora();
        apiDevolve();

        repositorio.buscarAgora();

        assertEquals(2, api.pedidosEm(BUSCA_USUARIO).size());
    }

    @Test
    public void erroDoServidor_naoEsqueceOId() {
        api.em(SCANS, 500, "{}");
        repositorio.buscarAgora();
        apiDevolve();

        repositorio.buscarAgora();

        assertEquals(1, api.pedidosEm(BUSCA_USUARIO).size());
    }

    // ---- Segundo plano ----

    @Test
    public void buscar_entregaOResultadoPeloExecutorPrincipal() {
        apiDevolve(scan("1", "PENDING_REVIEW"));
        AtomicReference<List<ProdutoEmAnalise>> recebido = new AtomicReference<>();
        CountDownLatch chegou = new CountDownLatch(1);

        repositorio.buscar(produtos -> {
            recebido.set(produtos);
            chegou.countDown();
        });
        aguardarLatch(chegou, 5000);

        assertEquals(1, recebido.get().size());
    }
}
