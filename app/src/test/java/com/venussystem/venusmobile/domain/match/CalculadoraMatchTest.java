package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalculadoraMatchTest {

    private static final long SEM_SULFATO = 34L;
    private static final long HIDRATACAO = 42L;

    private static IngredienteAnalisado ingrediente(String nome,
                                                    IngredienteAnalisado.Efeito... efeitos) {
        return new IngredienteAnalisado(nome, Arrays.asList(efeitos));
    }

    private static IngredienteAnalisado.Efeito efeito(long tag, String categoria, String nome) {
        return new IngredienteAnalisado.Efeito(tag, categoria, nome, "descricao");
    }

    private static Set<Long> tags(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void semQuestionarioNaoCruzaNada() {
        ResultadoMatch r = CalculadoraMatch.calcular(false, tags(HIDRATACAO),
                Collections.emptySet(), Collections.emptyList());

        assertEquals(ResultadoMatch.Veredito.SEM_PERFIL, r.veredito);
    }

    @Test
    public void efeitoDeEtiquetaQueAPessoaNaoTemFicaDeFora() {
        List<IngredienteAnalisado> lista = Collections.singletonList(
                ingrediente("SODIUM LAURYL SULFATE",
                        efeito(SEM_SULFATO, "WARNING", "Presença de sulfato")));

        ResultadoMatch r = CalculadoraMatch.calcular(true, tags(HIDRATACAO),
                Collections.emptySet(), lista);

        assertEquals(ResultadoMatch.Veredito.SEM_PONTOS, r.veredito);
        assertTrue(r.alertas.isEmpty());
    }

    @Test
    public void avisoDeEtiquetaDaPessoaViraAtencaoAgrupandoIngredientes() {
        List<IngredienteAnalisado> lista = Arrays.asList(
                ingrediente("SODIUM LAURYL SULFATE",
                        efeito(SEM_SULFATO, "WARNING", "Presença de sulfato")),
                ingrediente("AMMONIUM LAURYL SULFATE",
                        efeito(SEM_SULFATO, "WARNING", "Presença de sulfato")));

        ResultadoMatch r = CalculadoraMatch.calcular(true, tags(SEM_SULFATO),
                Collections.emptySet(), lista);

        assertEquals(ResultadoMatch.Veredito.ATENCAO, r.veredito);
        assertEquals(1, r.alertas.size());
        assertEquals(2, r.alertas.get(0).ingredientes.size());
    }

    @Test
    public void beneficioSemAlertaCombina() {
        List<IngredienteAnalisado> lista = Collections.singletonList(
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Hidratante")));

        ResultadoMatch r = CalculadoraMatch.calcular(true, tags(HIDRATACAO),
                Collections.emptySet(), lista);

        assertEquals(ResultadoMatch.Veredito.COMBINA, r.veredito);
        assertEquals(1, r.positivos.size());
    }

    @Test
    public void contraindicacaoPesaMaisQueBeneficio() {
        List<IngredienteAnalisado> lista = Arrays.asList(
                ingrediente("GLYCERIN", efeito(HIDRATACAO, "BENEFIT", "Hidratante")),
                ingrediente("RETINOL", efeito(102L, "CONTRAINDICATION", "Evitar na gestação")));

        ResultadoMatch r = CalculadoraMatch.calcular(true, tags(HIDRATACAO, 102L),
                Collections.emptySet(), lista);

        assertEquals(ResultadoMatch.Veredito.NAO_RECOMENDADO, r.veredito);
        assertTrue(r.alertas.get(0).grave);
    }

    @Test
    public void alergiaNaComposicaoNaoRecomenda() {
        List<IngredienteAnalisado> lista = Collections.singletonList(
                ingrediente("PARFUM", new IngredienteAnalisado.Efeito[0]));

        ResultadoMatch r = CalculadoraMatch.calcular(true, Collections.emptySet(),
                Collections.singleton("parfum"), lista);

        assertEquals(ResultadoMatch.Veredito.NAO_RECOMENDADO, r.veredito);
    }

    @Test
    public void alergiaCasaPalavraInteiraENaoPedacoDeNome() {
        List<IngredienteAnalisado> lista = Collections.singletonList(
                ingrediente("SODIUM LATEXOLATE", new IngredienteAnalisado.Efeito[0]));

        ResultadoMatch r = CalculadoraMatch.calcular(true, Collections.emptySet(),
                Collections.singleton("latex"), lista);

        assertEquals(ResultadoMatch.Veredito.SEM_PONTOS, r.veredito);
    }
}
