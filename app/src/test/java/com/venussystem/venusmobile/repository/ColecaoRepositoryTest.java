package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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

    @Test
    public void nomeEmUso_valeTambemParaAsDeExemplo() {
        assertTrue(listas.nomeEmUso("Produtos favoritados", -1));
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
        listas.marcarCriadaNaApi(id, 7L, "Viagem");

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
        listas.marcarCriadaNaApi(id, 7L, "Viagem");

        listas.renomear(id, "Férias");
        listas.atualizarImagem(id, "file:///capa.jpg");
        listas.atualizarDescricao(id, "Praia");

        ColecaoRepository.ListaLocal lista = listas.paraSincronizar().get(0);
        assertEquals(Long.valueOf(7L), lista.idApi);
        assertEquals("Férias", lista.nome);
        assertEquals("Viagem", lista.nomeNaApi);
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
