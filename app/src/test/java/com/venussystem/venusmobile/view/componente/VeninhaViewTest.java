package com.venussystem.venusmobile.view.componente;

import com.venussystem.venusmobile.model.HumorDaVeninha;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VeninhaViewTest {

    @Test
    public void feliz_eQuieta_pula() {
        assertTrue(VeninhaView.pula(HumorDaVeninha.FELIZ, false, true));
    }

    @Test
    public void falando_naoPula_paraABocaMexer() {
        assertFalse(VeninhaView.pula(HumorDaVeninha.FELIZ, true, true));
    }

    @Test
    public void comAnimacoesDesligadas_naoPula() {
        assertFalse(VeninhaView.pula(HumorDaVeninha.FELIZ, false, false));
    }

    @Test
    public void interrogacoesDaPensando_vaoDeZeroATresEVoltam() {
        assertEquals(0, VeninhaView.interrogacoesVisiveis(0));
        assertEquals(1, VeninhaView.interrogacoesVisiveis(1));
        assertEquals(3, VeninhaView.interrogacoesVisiveis(3));
        assertEquals(0, VeninhaView.interrogacoesVisiveis(4));
        assertEquals(1, VeninhaView.interrogacoesVisiveis(5));
    }

    @Test
    public void outrosHumores_naoPulam() {
        assertFalse(VeninhaView.pula(HumorDaVeninha.PENSANDO, false, true));
        assertFalse(VeninhaView.pula(HumorDaVeninha.CONFUSA, false, true));
        assertFalse(VeninhaView.pula(HumorDaVeninha.CHORANDO, false, true));
    }

    @Test
    public void semLetras_naoMexeABoca() {
        assertEquals(0, VeninhaView.passosDaFala(""));
        assertEquals(0, VeninhaView.passosDaFala(null));
        assertEquals(0, VeninhaView.passosDaFala("123 ?!"));
    }

    @Test
    public void cadaTresLetras_eUmaSilaba_queAbreEFechaABoca() {
        assertEquals(2, VeninhaView.passosDaFala("Olá"));
        assertEquals(4, VeninhaView.passosDaFala("Olá, tu"));
    }

    @Test
    public void pontuacaoENumeros_naoContam() {
        assertEquals(VeninhaView.passosDaFala("Oláoi"), VeninhaView.passosDaFala("O-l-á 42 o.i!"));
    }

    @Test
    public void textoLongo_paraEmTresSegundos() {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            texto.append('a');
        }

        assertEquals(50, VeninhaView.passosDaFala(texto.toString()));
    }
}
