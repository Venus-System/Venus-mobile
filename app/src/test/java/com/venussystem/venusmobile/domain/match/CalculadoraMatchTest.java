package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CalculadoraMatchTest {

    private static final long SEM_SILICONE = 32L;
    private static final long SEM_SULFATO = 34L;
    private static final long BARREIRA = 39L;
    private static final long HIDRATACAO = 42L;
    private static final long GRAVIDEZ = 102L;

    private static final Map<Long, String> CATALOGO = new HashMap<>();

    static {
        CATALOGO.put(SEM_SILICONE, "sem-silicone");
        CATALOGO.put(SEM_SULFATO, "sem-sulfato");
        CATALOGO.put(BARREIRA, "reparo-da-barreira");
        CATALOGO.put(HIDRATACAO, "hidratacao");
        CATALOGO.put(GRAVIDEZ, "gravidez");
    }

    private static IngredienteAnalisado ingrediente(String nome,
                                                    IngredienteAnalisado.Efeito... efeitos) {
        return new IngredienteAnalisado(nome, Arrays.asList(efeitos));
    }

    private static IngredienteAnalisado.Efeito efeito(long tag, String categoria, String nome) {
        return new IngredienteAnalisado.Efeito(tag, categoria, nome, "descricao");
    }

    private static Set<String> slugs(String... slugs) {
        return new HashSet<>(Arrays.asList(slugs));
    }

    private static ResultadoMatch calcular(Set<String> pessoa, Set<String> alergias,
                                           IngredienteAnalisado... ingredientes) {
        return CalculadoraMatch.calcular(true, CATALOGO, pessoa, alergias,
                Arrays.asList(ingredientes));
    }

    @Test
    public void semQuestionarioNaoCruzaNada() {
        ResultadoMatch r = CalculadoraMatch.calcular(false, CATALOGO, slugs("hidratacao"),
                Collections.emptySet(), Collections.emptyList());

        assertEquals(ResultadoMatch.Veredito.SEM_PERFIL, r.veredito);
    }

    @Test
    public void avisoDeEtiquetaQueAPessoaNaoTemViraDestaque() {
        ResultadoMatch r = calcular(slugs("hidratacao"), Collections.emptySet(),
                ingrediente("SODIUM LAURYL SULFATE",
                        efeito(SEM_SULFATO, "WARNING", "Presença de sulfato")));

        assertEquals(ResultadoMatch.Veredito.SEM_ALERTAS, r.veredito);
        assertTrue(r.pontos.isEmpty());
        assertEquals(1, r.destaques.size());
        assertEquals("sem-sulfato", r.destaques.get(0).slug);
    }

    @Test
    public void avisoDaPessoaViraAtencaoAgrupandoIngredientes() {
        ResultadoMatch r = calcular(slugs("sem-sulfato"), Collections.emptySet(),
                ingrediente("SODIUM LAURYL SULFATE",
                        efeito(SEM_SULFATO, "WARNING", "Presença de sulfato")),
                ingrediente("AMMONIUM LAURYL SULFATE",
                        efeito(SEM_SULFATO, "WARNING", "Presença de sulfato")));

        assertEquals(ResultadoMatch.Veredito.ATENCAO, r.veredito);
        assertEquals(1, r.pontos.size());
        assertEquals(ResultadoMatch.Tipo.ALERTA, r.pontos.get(0).tipo);
        assertEquals(2, r.pontos.get(0).ingredientes.size());
    }

    @Test
    public void beneficioSemAlertaCombina() {
        ResultadoMatch r = calcular(slugs("hidratacao"), Collections.emptySet(),
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Hidratante")));

        assertEquals(ResultadoMatch.Veredito.COMBINA, r.veredito);
        assertEquals(ResultadoMatch.Tipo.BENEFICIO, r.pontos.get(0).tipo);
    }

    @Test
    public void contraindicacaoPesaMaisQueBeneficioEVemPrimeiro() {
        ResultadoMatch r = calcular(slugs("hidratacao", "gravidez"), Collections.emptySet(),
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Hidratante")),
                ingrediente("RETINOL",
                        efeito(GRAVIDEZ, "CONTRAINDICATION", "Evitar na gestação")));

        assertEquals(ResultadoMatch.Veredito.NAO_RECOMENDADO, r.veredito);
        assertEquals(ResultadoMatch.Tipo.GRAVE, r.pontos.get(0).tipo);
        assertEquals(ResultadoMatch.Tipo.BENEFICIO, r.pontos.get(1).tipo);
    }

    @Test
    public void alergiaNaComposicaoNaoRecomendaEFicaNoProprioCampo() {
        ResultadoMatch r = calcular(Collections.emptySet(), Collections.singleton("parfum"),
                ingrediente("PARFUM"));

        assertEquals(ResultadoMatch.Veredito.NAO_RECOMENDADO, r.veredito);
        assertEquals(Collections.singletonList("PARFUM"), r.alergiasEncontradas);
        assertTrue(r.pontos.isEmpty());
    }

    @Test
    public void alergiaCasaPalavraInteiraENaoPedacoDeNome() {
        ResultadoMatch r = calcular(Collections.emptySet(), Collections.singleton("latex"),
                ingrediente("SODIUM LATEXOLATE"));

        assertEquals(ResultadoMatch.Veredito.SEM_ALERTAS, r.veredito);
        assertTrue(r.alergiasEncontradas.isEmpty());
    }

    @Test
    public void perfilSemEtiquetaRecebeDestaquesSemRepetir() {
        ResultadoMatch r = calcular(Collections.emptySet(), Collections.emptySet(),
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Suporte à hidratação")),
                ingrediente("BUTYLENE GLYCOL",
                        efeito(HIDRATACAO, "BENEFIT", "Suporte à hidratação")));

        assertEquals(ResultadoMatch.Veredito.SEM_ALERTAS, r.veredito);
        assertEquals(1, r.destaques.size());
    }

    @Test
    public void etiquetaQueJaEPontoNaoSeRepeteNosDestaques() {
        ResultadoMatch r = calcular(slugs("hidratacao"), Collections.emptySet(),
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Suporte à hidratação")),
                ingrediente("DIMETHICONE",
                        efeito(HIDRATACAO, "BENEFIT", "Suporte à hidratação"),
                        efeito(SEM_SILICONE, "WARNING", "Presença de silicone")));

        assertEquals(ResultadoMatch.Veredito.COMBINA, r.veredito);
        assertEquals(1, r.pontos.size());
        assertEquals(1, r.destaques.size());
        assertEquals("sem-silicone", r.destaques.get(0).slug);
    }

    @Test
    public void destaquesTrazemBeneficiosAntesDeNeutrosEAvisos() {
        ResultadoMatch r = calcular(Collections.emptySet(), Collections.emptySet(),
                ingrediente("DIMETHICONE",
                        efeito(SEM_SILICONE, "WARNING", "Presença de silicone")),
                ingrediente("SHEA BUTTER", efeito(BARREIRA, "NEUTRAL", "Suporte à barreira")),
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Suporte à hidratação")));

        assertEquals("hidratacao", r.destaques.get(0).slug);
        assertEquals("reparo-da-barreira", r.destaques.get(1).slug);
        assertEquals("sem-silicone", r.destaques.get(2).slug);
    }

    @Test
    public void etiquetaDeAvisoSemNadaNaFormulaViraAusencia() {
        ResultadoMatch r = calcular(slugs("sem-silicone", "gravidez"), Collections.emptySet(),
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Suporte à hidratação")));

        assertEquals(ResultadoMatch.Veredito.SEM_ALERTAS, r.veredito);
        assertEquals(2, r.pontos.size());
        for (ResultadoMatch.Ponto p : r.pontos) {
            assertEquals(ResultadoMatch.Tipo.AUSENCIA, p.tipo);
        }
    }

    @Test
    public void ausenciaNaoApareceQuandoOAvisoBate() {
        ResultadoMatch r = calcular(slugs("sem-silicone"), Collections.emptySet(),
                ingrediente("DIMETHICONE",
                        efeito(SEM_SILICONE, "WARNING", "Presença de silicone")));

        assertEquals(ResultadoMatch.Veredito.ATENCAO, r.veredito);
        assertEquals(1, r.pontos.size());
        assertEquals(ResultadoMatch.Tipo.ALERTA, r.pontos.get(0).tipo);
    }

    @Test
    public void semEfeitoNenhumNaoAfirmaAusencia() {
        ResultadoMatch r = calcular(slugs("gravidez"), Collections.emptySet(),
                ingrediente("AQUA"), ingrediente("CETEARYL ALCOHOL"));

        assertFalse(r.catalogoCobre);
        assertTrue(r.pontos.isEmpty());
        assertTrue(r.destaques.isEmpty());
    }

    @Test
    public void efeitoNeutroDaPessoaFicaNosDestaques() {
        ResultadoMatch r = calcular(slugs("reparo-da-barreira"), Collections.emptySet(),
                ingrediente("SHEA BUTTER", efeito(BARREIRA, "NEUTRAL", "Suporte à barreira")));

        assertTrue(r.pontos.isEmpty());
        assertEquals("reparo-da-barreira", r.destaques.get(0).slug);
    }
}
