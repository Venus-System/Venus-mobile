package com.venussystem.venusmobile.view.util;

import static org.junit.Assert.assertEquals;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.match.ResultadoMatch;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextosMatchTest {

    private static final Map<Integer, String> TEXTOS = new HashMap<>();

    static {
        TEXTOS.put(R.string.match_ingredientes_dois, "%1$s e %2$s");
        TEXTOS.put(R.string.match_ingredientes_mais, "%1$s e mais %2$d");
        TEXTOS.put(R.string.match_titulo_contem_silicone, "Contém silicone");
        TEXTOS.put(R.string.match_linha_evitar_silicones,
                "Você marcou evitar silicones e a fórmula tem %1$s.");
        TEXTOS.put(R.string.match_alergia_nao_contem_duas,
                "Não contém %1$s nem %2$s, da sua lista de alergias.");
        TEXTOS.put(R.string.match_alergia_nao_contem_varias,
                "Não contém nenhuma das suas %1$d alergias.");
        TEXTOS.put(R.string.match_chip_hidratacao, "Hidratação");
        TEXTOS.put(R.string.match_titulo_bom_para, "Bom para %1$s");
        TEXTOS.put(R.string.match_titulo_atencao_para, "Atenção para %1$s");
    }

    @Test
    public void etiquetaSoDaClassificacaoGanhaTituloGenerico() {
        ResultadoMatch.Ponto beneficio = new ResultadoMatch.Ponto(ResultadoMatch.Tipo.BENEFICIO,
                "pele-acneica", "", Collections.singletonList("NIACINAMIDE"));
        ResultadoMatch.Ponto alerta = new ResultadoMatch.Ponto(ResultadoMatch.Tipo.ALERTA,
                "olhos-sensiveis", "", Collections.singletonList("PARFUM"));

        assertEquals("Bom para pele acneica", TextosMatch.titulo(RECURSOS, beneficio));
        assertEquals("Atenção para olhos sensiveis", TextosMatch.titulo(RECURSOS, alerta));
    }

    private static final TextosMatch.Recursos RECURSOS =
            (id, argumentos) -> String.format(TEXTOS.get(id), argumentos);

    private static ResultadoMatch.Ponto ponto(ResultadoMatch.Tipo tipo, String slug,
                                              String... ingredientes) {
        return new ResultadoMatch.Ponto(tipo, slug, "Efeito do catálogo",
                Arrays.asList(ingredientes));
    }

    @Test
    public void umIngredienteViraFormatoDeTitulo() {
        assertEquals("Butylene Glycol",
                TextosMatch.ingredientes(RECURSOS, Collections.singletonList("BUTYLENE GLYCOL")));
    }

    @Test
    public void doisIngredientesAparecemOsDois() {
        assertEquals("Glycerin e Panthenol",
                TextosMatch.ingredientes(RECURSOS, Arrays.asList("GLYCERIN", "PANTHENOL")));
    }

    @Test
    public void maisDeDoisIngredientesResumeNoPrimeiro() {
        assertEquals("Glycerin e mais 4", TextosMatch.ingredientes(RECURSOS,
                Arrays.asList("GLYCERIN", "A", "B", "C", "D")));
    }

    @Test
    public void nomeQueNaoEstaEmMaiusculasFicaComoVeio() {
        assertEquals("Óleo de Amêndoas", TextosMatch.formatoTitulo("Óleo de Amêndoas"));
    }

    @Test
    public void alertaDePreferenciaLembraOQueAPessoaMarcou() {
        ResultadoMatch.Ponto p = ponto(ResultadoMatch.Tipo.ALERTA, "sem-silicone", "DIMETHICONE");

        assertEquals("Contém silicone", TextosMatch.titulo(RECURSOS, p));
        assertEquals("Você marcou evitar silicones e a fórmula tem Dimethicone.",
                TextosMatch.linha(RECURSOS, p));
    }

    @Test
    public void slugSemTextoUsaONomeDoEfeito() {
        ResultadoMatch.Ponto p = ponto(ResultadoMatch.Tipo.BENEFICIO, "slug-novo", "GLYCERIN");

        assertEquals("Efeito do catálogo", TextosMatch.titulo(RECURSOS, p));
    }

    @Test
    public void ausenciaNaoTemLinha() {
        assertEquals("", TextosMatch.linha(RECURSOS,
                ponto(ResultadoMatch.Tipo.AUSENCIA, "sem-silicone")));
    }

    @Test
    public void chipsNaoRepetemERespeitamOMaximo() {
        List<ResultadoMatch.Destaque> destaques = Arrays.asList(
                new ResultadoMatch.Destaque("hidratacao", "Suporte à hidratação"),
                new ResultadoMatch.Destaque(null, "Efeito sem etiqueta"),
                new ResultadoMatch.Destaque(null, "Outro efeito"));

        assertEquals(Arrays.asList("Hidratação", "Efeito sem etiqueta"),
                TextosMatch.chips(RECURSOS, destaques, 2));
    }

    @Test
    public void alergiasAusentesCitaAsDuas() {
        assertEquals("Não contém lanolina nem óleo de amêndoas, da sua lista de alergias.",
                TextosMatch.alergiasAusentes(RECURSOS,
                        Arrays.asList("Lanolina", "Óleo de Amêndoas")));
    }

    @Test
    public void muitasAlergiasViramContagem() {
        assertEquals("Não contém nenhuma das suas 3 alergias.",
                TextosMatch.alergiasAusentes(RECURSOS, Arrays.asList("A", "B", "C")));
    }
}
