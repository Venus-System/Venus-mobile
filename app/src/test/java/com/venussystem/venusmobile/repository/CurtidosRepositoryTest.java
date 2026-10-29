package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class CurtidosRepositoryTest {

    private Context context;
    private CurtidosRepository curtidos;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        curtidos = new CurtidosRepository(context, "uid-ana");
    }

    @Test
    public void curtir_poeNoTopo_eNaFilaDaApi() {
        curtidos.curtir(100L);
        curtidos.curtir(200L);

        assertEquals(Arrays.asList(200L, 100L), curtidos.getProdutoIds());
        assertTrue(curtidos.contem(100L));
        assertEquals(Arrays.asList(100L, 200L), curtidos.paraCurtirNaApi());
        assertTrue(curtidos.paraDescurtirNaApi().isEmpty());
    }

    @Test
    public void curtirDuasVezes_naoRepete() {
        curtidos.curtir(100L);
        curtidos.curtir(100L);

        assertEquals(Collections.singletonList(100L), curtidos.getProdutoIds());
        assertEquals(Collections.singletonList(100L), curtidos.paraCurtirNaApi());
    }

    @Test
    public void descurtir_tiraDaTela_eTrocaDeFila() {
        curtidos.curtir(100L);
        curtidos.descurtir(100L);

        assertFalse(curtidos.contem(100L));
        assertTrue(curtidos.paraCurtirNaApi().isEmpty());
        assertEquals(Collections.singletonList(100L), curtidos.paraDescurtirNaApi());
    }

    @Test
    public void mudarDeIdeia_soValeAUltimaVontade() {
        curtidos.curtir(100L);
        curtidos.descurtir(100L);
        curtidos.curtir(100L);

        assertTrue(curtidos.contem(100L));
        assertEquals(Collections.singletonList(100L), curtidos.paraCurtirNaApi());
        assertTrue(curtidos.paraDescurtirNaApi().isEmpty());
    }

    @Test
    public void envioQueTerminaDepoisDeDescurtir_naoApagaADescurtida() {
        curtidos.curtir(100L);
        curtidos.descurtir(100L);
        curtidos.marcarCurtidaEnviada(100L);

        assertEquals(Collections.singletonList(100L), curtidos.paraDescurtirNaApi());
        assertTrue(curtidos.temAlgoParaEnviar());
    }

    @Test
    public void trazerDaApi_oServidorVence_masOQueEstaNaFilaFica() {
        curtidos.curtir(100L);
        curtidos.marcarCurtidaEnviada(100L);
        curtidos.curtir(300L);
        curtidos.curtir(400L);
        curtidos.marcarCurtidaEnviada(400L);
        curtidos.descurtir(400L);

        boolean mudou = curtidos.trocarPelosDaApi(Arrays.asList(200L, 400L));

        assertTrue(mudou);
        assertEquals(Arrays.asList(300L, 200L), curtidos.getProdutoIds());
    }

    @Test
    public void trazerDaApi_semDiferenca_naoMuda() {
        curtidos.curtir(100L);
        curtidos.marcarCurtidaEnviada(100L);

        assertFalse(curtidos.trocarPelosDaApi(Collections.singletonList(100L)));
    }

    @Test
    public void cadaContaTemOsSeusCurtidos() {
        curtidos.curtir(100L);

        CurtidosRepository daBia = new CurtidosRepository(context, "uid-bia");

        assertTrue(daBia.getProdutoIds().isEmpty());
        assertFalse(daBia.temAlgoParaEnviar());
    }

    @Test
    public void curtidosDoArquivoAntigo_vemParaOBanco() {
        DadosDaConta.prefs(context, CurtidosRepository.ARQUIVO, "uid-bia").edit()
                .putString("produtos", "[300,200,100]")
                .putString("para_curtir", "[300]")
                .putString("para_descurtir", "[400]")
                .commit();

        CurtidosRepository daBia = new CurtidosRepository(context, "uid-bia");

        assertEquals(Arrays.asList(300L, 200L, 100L), daBia.getProdutoIds());
        assertEquals(Collections.singletonList(300L), daBia.paraCurtirNaApi());
        assertEquals(Collections.singletonList(400L), daBia.paraDescurtirNaApi());
        assertTrue(DadosDaConta.prefs(context, CurtidosRepository.ARQUIVO, "uid-bia")
                .getAll().isEmpty());
    }
}
