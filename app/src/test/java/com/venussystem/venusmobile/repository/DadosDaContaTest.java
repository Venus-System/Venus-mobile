package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.model.Colecao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Um celular pode ter mais de uma conta. O que uma conta guarda no aparelho
 * (listas, itens, buscas, questionario) nao pode aparecer para outra.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class DadosDaContaTest {

    private static final String ANA = "uid-ana";
    private static final String BIA = "uid-bia";

    // Nomes dos arquivos de antes da separacao por conta.
    private static final String LISTAS_ANTIGAS = "venus_listas";
    private static final String ITENS_ANTIGOS = "venus_lista_itens";
    private static final String BUSCAS_ANTIGAS = "venus_busca";
    private static final String PERFIL_ANTIGO = "venus_perfil";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private static List<String> nomes(List<Colecao> listas) {
        List<String> nomes = new ArrayList<>();
        for (Colecao lista : listas) {
            nomes.add(lista.getName());
        }
        return nomes;
    }

    private SharedPreferences antigo(String arquivo) {
        return context.getSharedPreferences(arquivo, Context.MODE_PRIVATE);
    }

    // ---- Cada conta ve so o que e seu ----

    @Test
    public void listaCriadaPorUmaConta_naoApareceParaOutra() {
        new ColecaoRepository(context, ANA).criar("Rotina da Ana", "", null);

        List<String> daBia = nomes(new ColecaoRepository(context, BIA).minhasListas().getValue());
        List<String> daAna = nomes(new ColecaoRepository(context, ANA).minhasListas().getValue());

        assertFalse(daBia.contains("Rotina da Ana"));
        assertTrue(daAna.contains("Rotina da Ana"));
    }

    @Test
    public void contaNova_comecaSoComAsListasDeExemplo() {
        new ColecaoRepository(context, ANA).criar("Rotina da Ana", "", null);

        assertEquals(Arrays.asList("Produtos favoritados", "Produtos escaneados", "Rotina de skincare"),
                nomes(new ColecaoRepository(context, BIA).minhasListas().getValue()));
    }

    @Test
    public void produtosDaLista_saoPorConta() {
        new ListaItemRepository(context, ANA).adicionar(1L, 10L);

        assertTrue(new ListaItemRepository(context, BIA).getProdutoIds(1L).isEmpty());
        assertEquals(Arrays.asList(10L), new ListaItemRepository(context, ANA).getProdutoIds(1L));
    }

    @Test
    public void buscasRecentes_saoPorConta() {
        new BuscaRecenteRepository(context, ANA).registrar("protetor solar");

        assertTrue(new BuscaRecenteRepository(context, BIA).getRecentes().isEmpty());
    }

    @Test
    public void questionario_ePorConta() {
        PerfilRepository daAna = new PerfilRepository(context, ANA);
        daAna.salvarResposta(PerfilRepository.TIPO_PELE, "OILY", "Oleosa");
        daAna.marcarQuestionarioRespondido();

        PerfilRepository daBia = new PerfilRepository(context, BIA);
        assertFalse(daBia.jaRespondeuQuestionario());
        assertNull(daBia.getTag(PerfilRepository.TIPO_PELE));
    }

    // ---- Dados de antes da separacao ----

    @Test
    public void listasAntigas_saoApagadasComAsCapas() throws IOException {
        File capa = new File(context.getFilesDir(), "capas_lista/capa_antiga.jpg");
        capa.getParentFile().mkdirs();
        try (FileOutputStream saida = new FileOutputStream(capa)) {
            saida.write(new byte[]{1, 2, 3});
        }
        // No aparelho o ImagemLocalUtil grava "file:///data/user/0/...". O
        // Uri.fromFile nao serve aqui porque, com o teste rodando no Windows, o
        // caminho comeca com "C:\"; montado assim fica no mesmo formato do
        // aparelho nos dois sistemas.
        String caminho = capa.getAbsolutePath().replace('\\', '/');
        String caminhoDaCapa = "file://" + (caminho.startsWith("/") ? "" : "/") + caminho;
        antigo(LISTAS_ANTIGAS).edit().putString("minhas_listas",
                "[{\"id\":8,\"nome\":\"abebe\",\"descricao\":\"\",\"caminhoImagem\":\""
                        + caminhoDaCapa + "\"}]").commit();
        antigo(ITENS_ANTIGOS).edit().putString("8", "[10,11]").commit();

        DadosDaConta.migrarDadosAntigos(context, ANA);

        assertFalse("a capa antiga nao pode ficar orfa", capa.exists());
        assertTrue(antigo(LISTAS_ANTIGAS).getAll().isEmpty());
        assertTrue(antigo(ITENS_ANTIGOS).getAll().isEmpty());
        assertFalse(nomes(new ColecaoRepository(context, ANA).minhasListas().getValue())
                .contains("abebe"));
        assertTrue(new ListaItemRepository(context, ANA).getProdutoIds(8L).isEmpty());
    }

    @Test
    public void buscasAntigas_saoApagadas() {
        antigo(BUSCAS_ANTIGAS).edit().putString("buscas_recentes", "shampoo").commit();

        DadosDaConta.migrarDadosAntigos(context, ANA);

        assertTrue(antigo(BUSCAS_ANTIGAS).getAll().isEmpty());
        assertTrue(new BuscaRecenteRepository(context, ANA).getRecentes().isEmpty());
    }

    @Test
    public void questionarioAntigo_ficaComQuemEstaLogado() {
        // O unico jeito de trocar de conta ("reiniciar teste") apagava o
        // questionario junto, entao o que sobrou e da conta logada.
        antigo(PERFIL_ANTIGO).edit()
                .putString("tipo_pele_tag", "DRY")
                .putBoolean("respondeu_questionario", true)
                .putLong("versao_perfil", 5L)
                .putLong("versao_enviada", 5L)
                .commit();

        DadosDaConta.migrarDadosAntigos(context, ANA);

        PerfilRepository daAna = new PerfilRepository(context, ANA);
        assertEquals("DRY", daAna.getTag(PerfilRepository.TIPO_PELE));
        assertTrue(daAna.jaRespondeuQuestionario());
        assertFalse("o que ja tinha ido pra API nao vai de novo", daAna.temAlteracaoParaEnviar());
        assertTrue(antigo(PERFIL_ANTIGO).getAll().isEmpty());
    }

    @Test
    public void questionarioAntigoSemNinguemLogado_eDescartado() {
        antigo(PERFIL_ANTIGO).edit().putString("tipo_pele_tag", "DRY").commit();

        DadosDaConta.migrarDadosAntigos(context, null);

        assertTrue(antigo(PERFIL_ANTIGO).getAll().isEmpty());
        assertNull(new PerfilRepository(context, null).getTag(PerfilRepository.TIPO_PELE));
    }

    @Test
    public void questionarioAntigo_naoSobrescreveOQueAContaJaTem() {
        new PerfilRepository(context, ANA).salvarResposta(PerfilRepository.TIPO_PELE, "OILY", "Oleosa");
        antigo(PERFIL_ANTIGO).edit().putString("tipo_pele_tag", "DRY").commit();

        DadosDaConta.migrarDadosAntigos(context, ANA);

        assertEquals("OILY", new PerfilRepository(context, ANA).getTag(PerfilRepository.TIPO_PELE));
    }

    @Test
    public void semDadosAntigos_naoFazNada() {
        new ColecaoRepository(context, ANA).criar("Rotina da Ana", "", null);

        DadosDaConta.migrarDadosAntigos(context, ANA);
        DadosDaConta.migrarDadosAntigos(context, ANA);

        assertTrue(nomes(new ColecaoRepository(context, ANA).minhasListas().getValue())
                .contains("Rotina da Ana"));
    }

    @Test
    public void listasAntigasCorrompidas_naoDerrubamOApp() {
        antigo(LISTAS_ANTIGAS).edit().putString("minhas_listas", "{isso nao e json").commit();

        DadosDaConta.migrarDadosAntigos(context, ANA);

        assertTrue(antigo(LISTAS_ANTIGAS).getAll().isEmpty());
    }
}
