package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.venussystem.venusmobile.model.ItemHistorico;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.testutil.ApiDeTeste;
import com.venussystem.venusmobile.testutil.ApiPorRota;
import com.venussystem.venusmobile.testutil.SessaoFalsa;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class HistoricoRepositoryTest {

    private static final String BUSCA_USUARIO = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String SCANS =
            "GET /api/scan-sessions/user/42?page=0&size=20&sort=createdAt%2Cdesc";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private MockWebServer server;
    private ApiPorRota api;
    private Context context;
    private final Map<Long, Produto> catalogo = new HashMap<>();
    private HistoricoRepository repositorio;
    private TimeZone fusoOriginal;

    @Before
    public void setUp() throws IOException {
        fusoOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(FUSO));

        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        context = ApplicationProvider.getApplicationContext();
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        UsuarioApiRepository usuarios = new UsuarioApiRepository(context, venusApi, new SessaoFalsa());
        usuarios.esquecerId();
        repositorio = new HistoricoRepository(venusApi, usuarios, context,
                id -> catalogo.get(id), Runnable::run);

        api.em(BUSCA_USUARIO, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
        apiDevolve();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        TimeZone.setDefault(fusoOriginal);
    }

    private static long momento(int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, mes, dia, hora, minuto)
                .atZone(FUSO).toInstant().toEpochMilli();
    }

    private void noCatalogo(long id, String nome, Integer nota) {
        catalogo.put(id, new Produto(id, nome, "Marca", nota, null, null, null));
    }

    private void acessouEm(String uid, long produtoId, long quando) {
        new AcessosRecentesRepository(context, uid, () -> quando).registrar(produtoId);
    }

    private static JsonObject scan(String id, String status, String nome, String criadoEm) {
        JsonObject extraido = new JsonObject();
        extraido.addProperty("productName", nome);
        JsonObject frente = new JsonObject();
        frente.add("extracted", extraido);
        JsonObject ocr = new JsonObject();
        ocr.add("front", frente);

        JsonObject scan = new JsonObject();
        scan.addProperty("id", id);
        scan.addProperty("scanId", "scan-" + id);
        scan.addProperty("status", status);
        scan.addProperty("createdAt", criadoEm);
        scan.add("ocr", ocr);
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

    private List<ItemHistorico> carregar() {
        repositorio.carregar();
        return repositorio.getHistorico().getValue();
    }

    private static List<String> nomes(List<ItemHistorico> itens) {
        List<String> nomes = new ArrayList<>();
        for (ItemHistorico item : itens) {
            nomes.add(item.getProduto().getName());
        }
        return nomes;
    }

    @Test
    public void produtoAcessado_apareceNoHistoricoSemSeloDeStatus() {
        noCatalogo(7L, "Hidratante X", 82);
        acessouEm("uid-ana", 7L, momento(10, 10, 12, 0));

        List<ItemHistorico> itens = carregar();

        assertEquals(1, itens.size());
        ItemHistorico item = itens.get(0);
        assertEquals("Hidratante X", item.getProduto().getName());
        assertEquals(Long.valueOf(7L), item.getProduto().getId());
        assertEquals(Integer.valueOf(82), item.getProduto().getOverallScore());
        assertEquals(LocalDate.of(2026, 10, 10), item.getData());
        assertNull(item.getStatus());
        assertNull(item.getScanId());
    }

    @Test
    public void acessosEScans_vemEmOrdemDecrescenteDeDataEHora() {
        noCatalogo(7L, "Hidratante X", 82);
        noCatalogo(8L, "Protetor Y", 55);
        acessouEm("uid-ana", 8L, momento(10, 2, 20, 0));
        acessouEm("uid-ana", 7L, momento(10, 10, 18, 0));
        apiDevolve(
                scan("1", "PENDING_REVIEW", "Serum A", "2026-10-10T09:00:00-03:00"),
                scan("2", "APPROVED", "Batom B", "2026-10-02T14:30:00-03:00"));

        List<ItemHistorico> itens = carregar();

        assertEquals(Arrays.asList("Hidratante X", "Serum A", "Protetor Y", "Batom B"),
                nomes(itens));
        assertNull(itens.get(0).getStatus());
        assertEquals("PENDING_REVIEW", itens.get(1).getStatus());
        assertNull(itens.get(2).getStatus());
        assertEquals("APPROVED", itens.get(3).getStatus());
    }

    @Test
    public void produtoEscaneadoEDepoisAcessado_apareceUmaVezComOSeloDoScan() {
        noCatalogo(7L, "Hidratante X", 82);
        acessouEm("uid-ana", 7L, momento(10, 10, 12, 0));
        apiDevolve(comProduto(
                scan("1", "APPROVED", "Hidratante do rotulo", "2026-10-02T14:30:00-03:00"), 7L));

        List<ItemHistorico> itens = carregar();

        assertEquals(1, itens.size());
        ItemHistorico item = itens.get(0);
        assertEquals(LocalDate.of(2026, 10, 10), item.getData());
        assertEquals("APPROVED", item.getStatus());
        assertEquals("scan-1", item.getScanId());
        assertEquals("Hidratante X", item.getProduto().getName());
        assertEquals(Integer.valueOf(82), item.getProduto().getOverallScore());
    }

    @Test
    public void acessoMaisAntigoQueOScan_naoMexeNaLinhaDoScan() {
        noCatalogo(7L, "Hidratante X", 82);
        acessouEm("uid-ana", 7L, momento(10, 2, 12, 0));
        apiDevolve(comProduto(
                scan("1", "APPROVED", "Hidratante do rotulo", "2026-10-10T14:30:00-03:00"), 7L));

        List<ItemHistorico> itens = carregar();

        assertEquals(1, itens.size());
        assertEquals("Hidratante do rotulo", itens.get(0).getProduto().getName());
        assertEquals(LocalDate.of(2026, 10, 10), itens.get(0).getData());
        assertEquals("APPROVED", itens.get(0).getStatus());
    }

    @Test
    public void produtoQueNaoEstaNoCatalogo_naoApareceNoHistorico() {
        noCatalogo(7L, "Hidratante X", 82);
        acessouEm("uid-ana", 99L, momento(10, 10, 11, 0));
        acessouEm("uid-ana", 7L, momento(10, 10, 12, 0));

        assertEquals(Arrays.asList("Hidratante X"), nomes(carregar()));
    }

    @Test
    public void soCabemOsVinteMaisRecentes() {
        for (long id = 1; id <= 20; id++) {
            noCatalogo(id, "Produto " + id, null);
            acessouEm("uid-ana", id, momento(10, 5, 0, 0) + id * 60_000L);
        }
        apiDevolve(
                scan("1", "APPROVED", "Scan 1", "2026-10-10T10:00:00-03:00"),
                scan("2", "APPROVED", "Scan 2", "2026-10-10T11:00:00-03:00"),
                scan("3", "APPROVED", "Scan 3", "2026-10-10T12:00:00-03:00"));

        List<String> nomes = nomes(carregar());

        assertEquals(20, nomes.size());
        assertEquals(Arrays.asList("Scan 3", "Scan 2", "Scan 1"), nomes.subList(0, 3));
        assertEquals("Produto 20", nomes.get(3));
        assertEquals("Produto 4", nomes.get(19));
    }

    @Test
    public void acessoDeOutraConta_naoApareceNoHistorico() {
        noCatalogo(7L, "Hidratante X", 82);
        acessouEm("uid-bia", 7L, momento(10, 10, 12, 0));

        List<ItemHistorico> itens = carregar();

        assertNotNull(itens);
        assertTrue(itens.isEmpty());
    }

    @Test
    public void semRespostaDaApi_osAcessosAindaAparecem() {
        noCatalogo(7L, "Hidratante X", 82);
        acessouEm("uid-ana", 7L, momento(10, 10, 12, 0));
        api.em(BUSCA_USUARIO, 500, "{}");

        assertEquals(Arrays.asList("Hidratante X"), nomes(carregar()));
    }

    @Test
    public void catalogoQueChegaDepois_encaixaOsAcessosSemPerderOsScans() {
        acessouEm("uid-ana", 7L, momento(10, 10, 12, 0));
        apiDevolve(scan("1", "APPROVED", "Serum A", "2026-10-02T14:30:00-03:00"));

        assertEquals(Arrays.asList("Serum A"), nomes(carregar()));

        noCatalogo(7L, "Hidratante X", 82);
        repositorio.recombinar();

        assertEquals(Arrays.asList("Hidratante X", "Serum A"),
                nomes(repositorio.getHistorico().getValue()));
    }
}
