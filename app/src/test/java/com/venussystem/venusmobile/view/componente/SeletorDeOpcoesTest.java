package com.venussystem.venusmobile.view.componente;

import static org.junit.Assert.assertEquals;

import com.venussystem.venusmobile.repository.PerfilRepository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
}
