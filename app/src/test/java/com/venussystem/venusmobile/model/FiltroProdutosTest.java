package com.venussystem.venusmobile.model;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FiltroProdutosTest {

    private static Produto produto(Long marcaId, Long categoriaId, Integer nota, Boolean brasileira) {
        return new Produto(1L, "Produto", "Marca", nota, null, categoriaId, "Categoria",
                marcaId, brasileira);
    }

    // ---- FaixaNota ----

    @Test
    public void faixa_limitesSao70e40() {
        assertEquals(FaixaNota.BOA, FaixaNota.de(100));
        assertEquals(FaixaNota.BOA, FaixaNota.de(70));
        assertEquals(FaixaNota.MEDIA, FaixaNota.de(69));
        assertEquals(FaixaNota.MEDIA, FaixaNota.de(40));
        assertEquals(FaixaNota.RUIM, FaixaNota.de(39));
        assertEquals(FaixaNota.RUIM, FaixaNota.de(0));
    }

    @Test
    public void faixa_semNotaNaoTemFaixa() {
        assertNull(FaixaNota.de(null));
    }

    // ---- FiltroProdutos ----

    @Test
    public void filtroVazio_naoEstaAtivoEAceitaTudo() {
        FiltroProdutos filtro = FiltroProdutos.vazio();

        assertFalse(filtro.isAtivo());
        assertTrue(filtro.aceita(produto(null, null, null, null)));
        assertTrue(filtro.aceita(produto(10L, 100L, 85, true)));
    }

    @Test
    public void comOpcaoMarcada_ficaAtivo() {
        assertTrue(FiltroProdutos.vazio().comMarca(10L, true).isAtivo());
        assertTrue(FiltroProdutos.vazio().comCategoria(100L, true).isAtivo());
        assertTrue(FiltroProdutos.vazio().comFaixa(FaixaNota.BOA, true).isAtivo());
        assertTrue(FiltroProdutos.vazio().comSoBrasileiras(true).isAtivo());
    }

    @Test
    public void desmarcarTudo_voltaAoVazio() {
        FiltroProdutos filtro = FiltroProdutos.vazio()
                .comMarca(10L, true).comMarca(10L, false)
                .comFaixa(FaixaNota.RUIM, true).comFaixa(FaixaNota.RUIM, false)
                .comSoBrasileiras(true).comSoBrasileiras(false);

        assertFalse(filtro.isAtivo());
        assertEquals(FiltroProdutos.vazio(), filtro);
    }

    @Test
    public void marca_produtoSemMarcaNaoPassa() {
        FiltroProdutos filtro = FiltroProdutos.vazio().comMarca(10L, true);

        assertTrue(filtro.aceita(produto(10L, null, null, null)));
        assertFalse(filtro.aceita(produto(20L, null, null, null)));
        assertFalse(filtro.aceita(produto(null, null, null, null)));
    }

    @Test
    public void categoria_produtoSemCategoriaNaoPassa() {
        FiltroProdutos filtro = FiltroProdutos.vazio().comCategoria(100L, true);

        assertTrue(filtro.aceita(produto(null, 100L, null, null)));
        assertFalse(filtro.aceita(produto(null, 200L, null, null)));
        assertFalse(filtro.aceita(produto(null, null, null, null)));
    }

    @Test
    public void faixa_produtoSemNotaNaoPassaQuandoHaFaixaMarcada() {
        FiltroProdutos filtro = FiltroProdutos.vazio()
                .comFaixa(FaixaNota.BOA, true).comFaixa(FaixaNota.RUIM, true);

        assertTrue(filtro.aceita(produto(null, null, 90, null)));
        assertTrue(filtro.aceita(produto(null, null, 10, null)));
        assertFalse(filtro.aceita(produto(null, null, 50, null)));
        assertFalse(filtro.aceita(produto(null, null, null, null)));
    }

    @Test
    public void soBrasileiras_exigeAMarcaDizerQueE() {
        FiltroProdutos filtro = FiltroProdutos.vazio().comSoBrasileiras(true);

        assertTrue(filtro.aceita(produto(null, null, null, true)));
        assertFalse(filtro.aceita(produto(null, null, null, false)));
        assertFalse(filtro.aceita(produto(null, null, null, null)));
    }

    @Test
    public void gruposSeCombinamComE() {
        FiltroProdutos filtro = FiltroProdutos.vazio()
                .comMarca(10L, true).comMarca(30L, true)
                .comFaixa(FaixaNota.BOA, true)
                .comSoBrasileiras(true);

        assertTrue(filtro.aceita(produto(30L, null, 80, true)));
        assertFalse(filtro.aceita(produto(30L, null, 80, false)));
        assertFalse(filtro.aceita(produto(30L, null, 50, true)));
        assertFalse(filtro.aceita(produto(20L, null, 80, true)));
    }

    @Test
    public void filtroEImutavel() {
        FiltroProdutos original = FiltroProdutos.vazio();
        FiltroProdutos alterado = original.comMarca(10L, true);

        assertFalse(original.isAtivo());
        assertNotEquals(original, alterado);
        assertSame(FiltroProdutos.vazio(), FiltroProdutos.vazio());
        assertEquals(Collections.singleton(10L), alterado.getMarcas());
    }

    @Test
    public void filtrosComAsMesmasEscolhas_saoIguais() {
        FiltroProdutos a = FiltroProdutos.vazio().comMarca(1L, true).comFaixa(FaixaNota.MEDIA, true);
        FiltroProdutos b = FiltroProdutos.vazio().comFaixa(FaixaNota.MEDIA, true).comMarca(1L, true);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
