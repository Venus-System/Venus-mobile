package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RespostasDaNotaTest {

    private static RespostasDaNota.Motivo motivo(String slug, String ingrediente, Double impacto,
                                                 boolean aviso) {
        return new RespostasDaNota.Motivo(slug, ingrediente, impacto, aviso);
    }

    @Test
    public void semMotivosNaoTemResposta() {
        assertTrue(RespostasDaNota.de(null).isEmpty());
        assertTrue(RespostasDaNota.de(Collections.emptyList()).isEmpty());
    }

    @Test
    public void agrupaPorRespostaESomaOImpacto() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Arrays.asList(
                motivo("controle-de-oleosidade", "ALCOHOL", -5.0, true),
                motivo("controle-de-oleosidade", "PARAFFINUM", -3.0, true),
                motivo("controle-de-oleosidade", "NIACINAMIDE", 2.0, false)));

        assertEquals(1, r.size());
        assertEquals(RespostasDaNota.Efeito.TIROU, r.get(0).efeito);
        assertEquals(0, r.get(0).porcento);
        assertEquals(Arrays.asList("ALCOHOL", "PARAFFINUM", "NIACINAMIDE"), r.get(0).ingredientes);
    }

    @Test
    public void somarPoucoCombinaPoucoEPuxaANotaParaBaixo() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Collections.singletonList(
                motivo("eczema", "PANTHENOL", 5.0, false)));

        assertEquals(RespostasDaNota.Efeito.COMBINOU_POUCO, r.get(0).efeito);
        assertEquals(25, r.get(0).porcento);
        assertTrue(RespostasDaNota.algumaPuxouParaBaixo(r));
    }

    @Test
    public void somarMetadeOuMaisCombina() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Arrays.asList(
                motivo("muito-seca", "GLYCERIN", 6.0, false),
                motivo("muito-seca", "PANTHENOL", 4.0, false)));

        assertEquals(RespostasDaNota.Efeito.COMBINOU, r.get(0).efeito);
        assertEquals(50, r.get(0).porcento);
        assertFalse(RespostasDaNota.algumaPuxouParaBaixo(r));
    }

    @Test
    public void passarDaPerguntaCheiaFicaEmCemPorCento() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Collections.singletonList(
                motivo("caspa", "ZINC PYRITHIONE", 35.0, false)));

        assertEquals(100, r.get(0).porcento);
    }

    @Test
    public void avisoComImpactoZeroEBloqueio() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Collections.singletonList(
                motivo("gravidez", "RETINOL", 0.0, true)));

        assertEquals(RespostasDaNota.Efeito.BLOQUEOU, r.get(0).efeito);
        assertTrue(RespostasDaNota.algumaPuxouParaBaixo(r));
    }

    @Test
    public void motivoQueNaoMudaANotaFicaDeFora() {
        assertTrue(RespostasDaNota.de(Collections.singletonList(
                motivo("reativa", "FRAGRANCE", 0.0, false))).isEmpty());
    }

    @Test
    public void avisoSemImpactoContaComoTirou() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Collections.singletonList(
                motivo("reativa", "FRAGRANCE", null, true)));

        assertEquals(RespostasDaNota.Efeito.TIROU, r.get(0).efeito);
    }

    @Test
    public void ordemPoeOQueMaisPuxouParaBaixoPrimeiro() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Arrays.asList(
                motivo("muito-seca", "GLYCERIN", 12.0, false),
                motivo("caspa", "ZINC", 20.0, false),
                motivo("eczema", "PANTHENOL", 6.0, false),
                motivo("melasma", "NIACINAMIDE", 2.0, false),
                motivo("controle-de-oleosidade", "ALCOHOL", -8.0, true),
                motivo("gravidez", "RETINOL", 0.0, true)));

        assertEquals("gravidez", r.get(0).slug);
        assertEquals("controle-de-oleosidade", r.get(1).slug);
        assertEquals("melasma", r.get(2).slug);
        assertEquals("eczema", r.get(3).slug);
        assertEquals("caspa", r.get(4).slug);
        assertEquals("muito-seca", r.get(5).slug);
    }

    @Test
    public void ingredienteRepetidoApareceSoUmaVez() {
        List<RespostasDaNota.Resposta> r = RespostasDaNota.de(Arrays.asList(
                motivo("reativa", "FRAGRANCE", -1.0, true),
                motivo("reativa", "FRAGRANCE", -1.0, true)));

        assertEquals(Collections.singletonList("FRAGRANCE"), r.get(0).ingredientes);
    }

    @Test
    public void pontoJaExplicadoPelaRespostaSaiDaListaMasGraveEAusenciaFicam() {
        List<RespostasDaNota.Resposta> respostas = RespostasDaNota.de(Arrays.asList(
                motivo("sem-silicone", "DIMETHICONE", -3.0, true),
                motivo("gravidez", "RETINOL", 0.0, true)));
        List<ResultadoMatch.Ponto> pontos = Arrays.asList(
                new ResultadoMatch.Ponto(ResultadoMatch.Tipo.ALERTA, "sem-silicone", "",
                        Collections.singletonList("DIMETHICONE")),
                new ResultadoMatch.Ponto(ResultadoMatch.Tipo.GRAVE, "gravidez", "",
                        Collections.singletonList("RETINOL")),
                new ResultadoMatch.Ponto(ResultadoMatch.Tipo.AUSENCIA, "sem-sulfato", "",
                        Collections.emptyList()),
                new ResultadoMatch.Ponto(ResultadoMatch.Tipo.BENEFICIO, "controle-de-frizz", "",
                        Collections.singletonList("PANTHENOL")));

        List<ResultadoMatch.Ponto> r = RespostasDaNota.semRepetir(pontos, respostas);

        assertEquals(3, r.size());
        assertEquals("gravidez", r.get(0).slug);
        assertEquals("sem-sulfato", r.get(1).slug);
        assertEquals("controle-de-frizz", r.get(2).slug);
    }

    @Test
    public void peleSecaDaClassificacaoCobreAEtiquetaDoAparelho() {
        Set<String> cobertas = RespostasDaNota.etiquetasCobertas(RespostasDaNota.de(
                Collections.singletonList(motivo("muito-seca", "GLYCERIN", 2.0, false))));

        assertTrue(cobertas.contains("muito-seca"));
        assertTrue(cobertas.contains("hidratacao"));
    }
}
