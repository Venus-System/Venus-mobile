package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.model.Colecao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ColecaoRepositoryTest {

    private Context context;
    private ColecaoRepository listas;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        listas = new ColecaoRepository(context, "uid-ana");
    }

    // ---- Nome repetido ----

    @Test
    public void nomeEmUso_ignoraMaiusculasEEspacos() {
        listas.criar("Rotina da noite", "", null);

        assertTrue(listas.nomeEmUso("  rotina DA noite ", -1));
        assertFalse(listas.nomeEmUso("Rotina do dia", -1));
    }

    @Test
    public void nomeEmUso_naoContaAPropriaLista() {
        long id = listas.criar("Viagem", "", null).getId();

        // Renomear "Viagem" para "viagem" e a mesma lista, nao uma repetida.
        assertFalse(listas.nomeEmUso("viagem", id));
    }

    // ---- Listas de exemplo ----

    private static final String FAVORITOS_COMO_NASCEU =
            "{\"id\":1,\"nome\":\"Produtos favoritados\",\"chaveImagem\":\"favoritos\"}";
    private static final String ESCANEADOS_COMO_NASCEU =
            "{\"id\":2,\"nome\":\"Produtos escaneados\",\"chaveImagem\":\"escaneados\"}";
    private static final String SKINCARE_COMO_NASCEU =
            "{\"id\":3,\"nome\":\"Rotina de skincare\",\"chaveImagem\":\"skincare\"}";

    /** Grava as listas como uma versao anterior do app deixou. */
    private void listasGravadas(String... json) {
        DadosDaConta.prefs(context, ColecaoRepository.ARQUIVO, "uid-ana").edit()
                .putString("minhas_listas", "[" + String.join(",", json) + "]")
                .commit();
    }

    private List<String> nomesNaTela() {
        List<String> nomes = new ArrayList<>();
        for (Colecao colecao : listas.minhasListas().getValue()) {
            nomes.add(colecao.getName());
        }
        return nomes;
    }

    @Test
    public void contaNova_comecaSemNenhumaLista() {
        assertTrue(listas.minhasListas().getValue().isEmpty());
        assertFalse(listas.nomeEmUso("Produtos favoritados", -1));
    }

    @Test
    public void contaAntiga_perdeAsListasDeExemploQueFicaramComoNasceram() {
        listasGravadas(FAVORITOS_COMO_NASCEU, ESCANEADOS_COMO_NASCEU, SKINCARE_COMO_NASCEU);

        assertTrue(listas.minhasListas().getValue().isEmpty());
    }

    @Test
    public void contaAntiga_mantemAsListasDeExemploQueAPessoaUsou() {
        ListaItemRepository itens = new ListaItemRepository(context, "uid-ana");
        itens.adicionar(1L, 100L);
        listasGravadas(
                FAVORITOS_COMO_NASCEU,
                "{\"id\":2,\"nome\":\"Meus scans\",\"chaveImagem\":\"escaneados\"}",
                "{\"id\":3,\"nome\":\"Rotina de skincare\",\"chaveImagem\":\"skincare\","
                        + "\"descricao\":\"Noite\"}",
                "{\"id\":4,\"nome\":\"Viagem\",\"descricao\":\"\"}");

        assertEquals(Arrays.asList("Produtos favoritados", "Meus scans", "Rotina de skincare",
                "Viagem"), nomesNaTela());
    }

    @Test
    public void contaAntiga_mantemAListaDeExemploQueSubiuOuGanhouCapa() {
        listasGravadas(
                "{\"id\":1,\"nome\":\"Produtos favoritados\",\"chaveImagem\":\"favoritos\","
                        + "\"idApi\":7}",
                "{\"id\":2,\"nome\":\"Produtos escaneados\",\"chaveImagem\":\"escaneados\","
                        + "\"caminhoImagem\":\"file:///capa.jpg\"}",
                SKINCARE_COMO_NASCEU);

        assertEquals(Arrays.asList("Produtos favoritados", "Produtos escaneados"), nomesNaTela());
    }

    @Test
    public void listaDeExemploQueSaiu_naoDeixaProdutoParaEnviar() {
        ListaItemRepository itens = new ListaItemRepository(context, "uid-ana");
        // Entrou um produto e saiu: a lista esta vazia, mas a versao subiu.
        itens.adicionar(3L, 100L);
        itens.remover(3L, 100L);
        listasGravadas(SKINCARE_COMO_NASCEU);

        listas.minhasListas();

        assertFalse(itens.temItensParaEnviar(3L));
    }

    @Test
    public void idDasListasDeExemploQueSairam_naoEReaproveitado() {
        listasGravadas(FAVORITOS_COMO_NASCEU, ESCANEADOS_COMO_NASCEU, SKINCARE_COMO_NASCEU);

        long primeiraCriada = listas.criar("Viagem", "", null).getId();

        assertEquals(4L, primeiraCriada);
    }

    @Test
    public void listasDeExemplo_saoRevistasUmaVezSo() {
        listasGravadas(FAVORITOS_COMO_NASCEU);
        listas.minhasListas();

        // Depois da revisao, uma lista assim e legitima: por exemplo, a que veio
        // da API com capa padrao e teve o id esquecido.
        listasGravadas(FAVORITOS_COMO_NASCEU);

        assertEquals(Collections.singletonList("Produtos favoritados"), nomesNaTela());
    }

    // ---- Ids ----

    @Test
    public void idDeListaExcluida_naoEReaproveitado() {
        long primeira = listas.criar("A", "", null).getId();
        long segunda = listas.criar("B", "", null).getId();
        listas.excluir(segunda);

        long terceira = listas.criar("C", "", null).getId();

        assertEquals(primeira + 1, segunda);
        assertEquals(segunda + 1, terceira);
    }

    // ---- Controle do envio para a API ----

    @Test
    public void excluirListaQueJaEstavaNaApi_entraNaFilaDeApagar() {
        long id = listas.criar("Viagem", "", null).getId();
        listas.marcarCriadaNaApi(7L, listas.paraSincronizar().get(0));

        listas.excluir(id);

        assertEquals(Collections.singleton(7L), listas.idsParaApagarNaApi());
    }

    @Test
    public void excluirListaQueNuncaFoiParaAApi_naoEntraNaFila() {
        long id = listas.criar("Viagem", "", null).getId();

        listas.excluir(id);

        assertTrue(listas.idsParaApagarNaApi().isEmpty());
    }

    @Test
    public void renomearEMudarCapa_naoPerdemOIdDaApi() {
        long id = listas.criar("Viagem", "", null).getId();
        listas.marcarCriadaNaApi(7L, listas.paraSincronizar().get(0));

        listas.renomear(id, "Férias");
        listas.atualizarImagem(id, "file:///capa.jpg");
        listas.atualizarDescricao(id, "Praia");

        ColecaoRepository.ListaLocal lista = listas.paraSincronizar().get(0);
        assertEquals(Long.valueOf(7L), lista.idApi);
        assertEquals("Férias", lista.nome);
        assertEquals("Viagem", lista.nomeNaApi);
        assertEquals("Praia", lista.descricao);
        assertNull("a descricao nova ainda nao foi enviada", lista.descricaoNaApi);
    }

    @Test
    public void listasSalvasAntesDestaVersao_carregamSemIdDaApi() {
        // Formato gravado pelas versoes anteriores, sem idApi nem nomeNaApi.
        DadosDaConta.prefs(context, ColecaoRepository.ARQUIVO, "uid-ana").edit()
                .putString("minhas_listas",
                        "[{\"id\":4,\"nome\":\"Viagem\",\"descricao\":\"\"}]")
                .commit();

        List<ColecaoRepository.ListaLocal> salvas = listas.paraSincronizar();

        assertEquals(1, salvas.size());
        assertEquals("Viagem", salvas.get(0).nome);
        assertNull(salvas.get(0).idApi);
        assertNull(salvas.get(0).nomeNaApi);
    }
}
