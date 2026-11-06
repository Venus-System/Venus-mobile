package com.venussystem.venusmobile.view.componente;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class SeletorDeOpcoesTest {
    @Test
    public void adicionaOpcaoNoFim() {
        List<String> resultado = SeletorDeOpcoes.aplicar(
                Collections.singletonList("Lanolina"), "Parabenos");

        assertEquals(Arrays.asList("Lanolina", "Parabenos"), resultado);
    }

    @Test
    public void nenhumaApagaAsOutrasEFicaSozinha() {
        List<String> resultado = SeletorDeOpcoes.aplicar(
                Arrays.asList("Lanolina", "Parabenos"), PerfilRepository.NENHUMA);

        assertEquals(Collections.singletonList(PerfilRepository.NENHUMA), resultado);
    }

    @Test
    public void escolherOpcaoDepoisDeNenhumaTiraANenhuma() {
        List<String> resultado = SeletorDeOpcoes.aplicar(
                Collections.singletonList(PerfilRepository.NENHUMA), "Lanolina");

        assertEquals(Collections.singletonList("Lanolina"), resultado);
    }

    @Test
    public void textoEscritoTambemTiraANenhuma() {
        List<String> resultado = SeletorDeOpcoes.aplicar(
                Collections.singletonList(PerfilRepository.NENHUMA), "Níquel");

        assertEquals(Collections.singletonList("Níquel"), resultado);
    }

    @Test
    public void naoRepeteMesmoComMaiusculasDiferentes() {
        List<String> resultado = SeletorDeOpcoes.aplicar(
                Collections.singletonList("Lanolina"), "lanolina");

        assertEquals(Collections.singletonList("Lanolina"), resultado);
    }

    @Test
    public void naoMexeNaListaOriginal() {
        List<String> atuais = Arrays.asList("Lanolina", "Soja");

        SeletorDeOpcoes.aplicar(atuais, PerfilRepository.NENHUMA);

        assertEquals(Arrays.asList("Lanolina", "Soja"), atuais);
    }

    @Test
    public void listaDePreferenciasIncluiArraysNenhumaEOutro() {
        Context contexto = ApplicationProvider.getApplicationContext();
        List<String> preferencias = Arrays.asList(
                contexto.getResources().getStringArray(R.array.preferencias));

        List<String> opcoes = SeletorDeOpcoes.opcoesComExtras(contexto, preferencias);

        assertEquals(contexto.getString(R.string.opcao_nenhuma), opcoes.get(0));
        assertEquals(preferencias, opcoes.subList(1, opcoes.size() - 1));
        assertEquals(contexto.getString(R.string.opcao_outro), opcoes.get(opcoes.size() - 1));
    }
}
