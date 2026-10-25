package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PontosDaClassificacaoTest {

    private static ResultadoMatch.Ponto ponto(ResultadoMatch.Tipo tipo, String slug) {
        return new ResultadoMatch.Ponto(tipo, slug, "", Collections.singletonList("GLYCERIN"));
    }

    private static PontosDaClassificacao.Motivo motivo(String slug, String ingrediente,
                                                       boolean aviso) {
        return new PontosDaClassificacao.Motivo(slug, ingrediente, aviso);
    }

    @Test
    public void semMotivosFicaComOsPontosDoAparelho() {
        List<ResultadoMatch.Ponto> locais =
                Collections.singletonList(ponto(ResultadoMatch.Tipo.BENEFICIO, "hidratacao"));

        assertSame(locais, PontosDaClassificacao.mesclar(locais, null));
    }

    @Test
    public void motivoQueOAparelhoJaMostraNaoRepete() {
        List<ResultadoMatch.Ponto> r = PontosDaClassificacao.mesclar(
                Collections.singletonList(ponto(ResultadoMatch.Tipo.BENEFICIO, "hidratacao")),
                Collections.singletonList(motivo("hidratacao", "GLYCERIN", false)));

        assertEquals(1, r.size());
    }

    @Test
    public void etiquetaQueSoAClassificacaoConheceEntraAgrupada() {
        List<ResultadoMatch.Ponto> r = PontosDaClassificacao.mesclar(Collections.emptyList(),
                Arrays.asList(motivo("pele-acneica", "NIACINAMIDE", false),
                        motivo("pele-acneica", "ZINC PCA", false)));

        assertEquals(1, r.size());
        assertEquals(ResultadoMatch.Tipo.BENEFICIO, r.get(0).tipo);
        assertEquals(Arrays.asList("NIACINAMIDE", "ZINC PCA"), r.get(0).ingredientes);
    }

    @Test
    public void avisoDaClassificacaoDerrubaOSemDoAparelho() {
        List<ResultadoMatch.Ponto> r = PontosDaClassificacao.mesclar(
                Collections.singletonList(ponto(ResultadoMatch.Tipo.AUSENCIA, "sem-silicone")),
                Collections.singletonList(motivo("sem-silicone", "DIMETHICONE", true)));

        assertEquals(1, r.size());
        assertEquals(ResultadoMatch.Tipo.ALERTA, r.get(0).tipo);
        assertEquals(Collections.singletonList("DIMETHICONE"), r.get(0).ingredientes);
    }

    @Test
    public void avisosVemAntesDosBeneficios() {
        List<ResultadoMatch.Ponto> r = PontosDaClassificacao.mesclar(
                Collections.singletonList(ponto(ResultadoMatch.Tipo.BENEFICIO, "hidratacao")),
                Collections.singletonList(motivo("sem-sulfato", "SODIUM LAURYL SULFATE", true)));

        assertEquals(ResultadoMatch.Tipo.ALERTA, r.get(0).tipo);
        assertEquals(ResultadoMatch.Tipo.BENEFICIO, r.get(1).tipo);
    }
}
