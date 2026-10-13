package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

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
import java.net.URI;
import java.util.Collections;

import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class FotoPerfilRepositoryTest {

    private static final String BUSCA = "GET /api/users/search?firebaseUid=uid-ana";
    private static final String ENVIAR = "POST /api/users/42/avatar";
    private static final String BUSCAR = "GET /api/users/42/avatar";
    private static final String LINK = "https://res.cloudinary.com/venus/avatar_42.jpg";

    private MockWebServer server;
    private ApiPorRota api;
    private Context context;
    private FotoPerfilRepository foto;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        api = new ApiPorRota();
        server.setDispatcher(api);
        server.start();

        context = ApplicationProvider.getApplicationContext();
        VenusApi venusApi = ApiDeTeste.criar(server, VenusApi.class);
        UsuarioApiRepository usuarios =
                new UsuarioApiRepository(context, venusApi, new SessaoFalsa());
        foto = new FotoPerfilRepository(context, "uid-ana", usuarios, venusApi, Runnable::run);

        api.em(BUSCA, 200, "{\"content\":[{\"id\":42,\"firebaseUid\":\"uid-ana\"}]}");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /** Uma "foto" ja reduzida no aparelho, como a que o trocar() deixa. */
    private String fotoNoAparelho(String nome) throws IOException {
        File pasta = new File(context.getFilesDir(), "fotos_perfil");
        pasta.mkdirs();
        File arquivo = new File(pasta, nome);
        try (FileOutputStream saida = new FileOutputStream(arquivo)) {
            saida.write(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3});
        }
        return arquivo.toURI().toString();
    }

    // ---- Enviar ----

    @Test
    public void fotoNova_vaiNoCampoFileComoJpeg() throws IOException {
        foto.guardarNova(fotoNoAparelho("foto_a.jpg"));
        api.em(ENVIAR, 201, "{\"url\":\"" + LINK + "\"}");

        assertTrue(foto.enviarAgora());

        String corpo = api.corposEm(ENVIAR).get(0);
        assertTrue(corpo.contains("name=\"file\"; filename=\"foto_a.jpg\""));
        assertTrue(corpo.contains("Content-Type: image/jpeg"));
    }

    @Test
    public void jaEnviada_naoMandaDeNovo() throws IOException {
        foto.guardarNova(fotoNoAparelho("foto_a.jpg"));
        api.em(ENVIAR, 201, "{}");
        foto.enviarAgora();
        int antes = api.pedidos.size();

        assertFalse(foto.enviarAgora());

        assertEquals(antes, api.pedidos.size());
    }

    @Test
    public void semFotoEscolhida_nemVaiNaRede() {
        assertFalse(foto.enviarAgora());

        assertTrue(api.pedidos.isEmpty());
    }

    @Test
    public void falhaDoServidor_ficaPendenteParaAProxima() throws IOException {
        foto.guardarNova(fotoNoAparelho("foto_a.jpg"));
        api.em(ENVIAR, 503, "{\"status\":503}");

        assertFalse(foto.enviarAgora());

        api.em(ENVIAR, 201, "{}");
        assertTrue(foto.enviarAgora());
    }

    @Test
    public void imagemRecusadaPelaApi_naoInsiste() throws IOException {
        foto.guardarNova(fotoNoAparelho("foto_a.jpg"));
        api.em(ENVIAR, 413, "{\"status\":413}");

        assertFalse(foto.enviarAgora());
        assertFalse(foto.enviarAgora());

        assertEquals(1, api.corposEm(ENVIAR).size());
    }

    @Test
    public void fotoTrocadaDuranteOEnvio_continuaPendente() throws IOException {
        foto.guardarNova(fotoNoAparelho("foto_a.jpg"));
        String segunda = fotoNoAparelho("foto_b.jpg");
        api.em(ENVIAR, 201, "{}");
        api.aoReceber(ENVIAR, () -> foto.guardarNova(segunda));

        foto.enviarAgora();

        // A segunda foto ainda nao foi: a proxima chamada manda ela.
        assertTrue(foto.enviarAgora());
        assertTrue(api.corposEm(ENVIAR).get(1).contains("foto_b.jpg"));
    }

    // ---- Trocar ----

    @Test
    public void trocarDeFoto_apagaOArquivoDaAnterior() throws IOException {
        String primeira = fotoNoAparelho("foto_a.jpg");
        foto.guardarNova(primeira);

        assertTrue(new File(URI.create(primeira)).exists());

        foto.guardarNova(fotoNoAparelho("foto_b.jpg"));

        assertFalse(new File(URI.create(primeira)).exists());
    }

    @Test
    public void cadaContaTemASuaFoto() throws IOException {
        foto.guardarNova(fotoNoAparelho("foto_a.jpg"));

        FotoPerfilRepository daOutraConta = new FotoPerfilRepository(context, "uid-bia",
                new UsuarioApiRepository(context, ApiDeTeste.criar(server, VenusApi.class),
                        new SessaoFalsa()),
                ApiDeTeste.criar(server, VenusApi.class), Runnable::run);

        assertNull(daOutraConta.caminho());
    }

    // ---- Buscar na API ----

    @Test
    public void semFotoNoAparelho_usaOLinkDaApi() {
        api.em(BUSCAR, 200, "{\"purpose\":\"AVATAR\",\"url\":\"" + LINK + "\"}");

        assertEquals(LINK, foto.buscarDaApiAgora());
        assertEquals(LINK, foto.caminho());
    }

    @Test
    public void comFotoEscolhidaNoAparelho_nemPerguntaAApi() throws IOException {
        foto.guardarNova(fotoNoAparelho("foto_a.jpg"));

        assertNull(foto.buscarDaApiAgora());

        assertTrue(api.pedidosEm(BUSCAR).isEmpty());
    }

    @Test
    public void semFotoNaApi_continuaSemFoto() {
        // GET sem resposta configurada cai no 404 do ApiPorRota.
        assertNull(foto.buscarDaApiAgora());
        assertNull(foto.caminho());
    }

    @Test
    public void mesmoLinkDeAntes_naoAvisaDeNovo() {
        api.em(BUSCAR, 200, "{\"url\":\"" + LINK + "\"}");
        foto.buscarDaApiAgora();

        assertNull(foto.buscarDaApiAgora());
        assertEquals(Collections.nCopies(2, BUSCAR), api.pedidosEm(BUSCAR));
    }
}
