package com.venussystem.venusmobile.view.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.match.RespostasDaNota;
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
        TEXTOS.put(R.string.match_chip_hidratacao, "Hidratação");
        TEXTOS.put(R.string.match_titulo_bom_para, "Bom para %1$s");
        TEXTOS.put(R.string.match_titulo_atencao_para, "Atenção para %1$s");
        TEXTOS.put(R.string.match_resposta_pele_oleosa, "Pele oleosa");
        TEXTOS.put(R.string.match_resposta_pele_seca, "Pele seca");
        TEXTOS.put(R.string.match_resposta_generica, "Você marcou %1$s");
        TEXTOS.put(R.string.match_resposta_tirou,
                "Tirou pontos por causa de %1$s. Puxou a nota para baixo.");
        TEXTOS.put(R.string.match_resposta_tirou_sem_ingrediente,
                "Tirou pontos. Puxou a nota para baixo.");
        TEXTOS.put(R.string.match_resposta_pouco,
                "Combinou só %1$d%%, com %2$s. Puxou a nota para baixo.");
        TEXTOS.put(R.string.match_resposta_combinou, "Combinou %1$d%% graças a %2$s.");
        TEXTOS.put(R.string.match_resposta_bloqueou,
                "Por causa de %1$s, este produto não é indicado para você.");
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
    public void pontosComVirgulaESemCasaQuandoInteiro() {
        assertEquals("24,5", TextosMatch.pontos(24.5));
        assertEquals("15,3", TextosMatch.pontos(15.3));
        assertEquals("0", TextosMatch.pontos(0.0));
        assertEquals("35", TextosMatch.pontos(35.0));
    }

    @Test
    public void erroDaNotaSaiPeloCode() {
        assertEquals(Integer.valueOf(R.string.produto_match_erro_perfil),
                TextosMatch.erroDaNota("PROFILE_NOT_FOUND"));
        assertEquals(Integer.valueOf(R.string.produto_match_erro_produto),
                TextosMatch.erroDaNota("PRODUCT_NOT_FOUND"));
        assertEquals(Integer.valueOf(R.string.produto_match_erro_produto),
                TextosMatch.erroDaNota("VERSION_NOT_FOUND"));
        assertEquals(Integer.valueOf(R.string.produto_match_erro_em_revisao),
                TextosMatch.erroDaNota("VERSION_UNDER_REVIEW"));
        assertEquals(Integer.valueOf(R.string.produto_match_erro_sem_ingredientes),
                TextosMatch.erroDaNota("NO_INGREDIENTS"));
        assertEquals(Integer.valueOf(R.string.produto_match_erro_calculo),
                TextosMatch.erroDaNota("SCORING_MODEL_NOT_FOUND"));
        assertEquals(Integer.valueOf(R.string.produto_match_erro_calculo),
                TextosMatch.erroDaNota("NO_ACTIVE_SCORING_MODEL"));
    }

    private static RespostasDaNota.Resposta resposta(String slug, Double impacto, boolean aviso,
                                                     String... ingredientes) {
        List<RespostasDaNota.Motivo> motivos = new java.util.ArrayList<>();
        for (String i : ingredientes) {
            motivos.add(new RespostasDaNota.Motivo(slug, i, impacto, aviso));
        }
        if (ingredientes.length == 0) {
            motivos.add(new RespostasDaNota.Motivo(slug, null, impacto, aviso));
        }
        return RespostasDaNota.de(motivos).get(0);
    }

    @Test
    public void respostaDaClassificacaoViraONomeDaPergunta() {
        assertEquals("Pele oleosa",
                TextosMatch.tituloDaResposta(RECURSOS, "controle-de-oleosidade"));
        assertEquals("Pele seca", TextosMatch.tituloDaResposta(RECURSOS, "muito-seca"));
        assertEquals("Você marcou vegano", TextosMatch.tituloDaResposta(RECURSOS, "vegano"));
    }

    @Test
    public void respostaQueTirouDizPorCausaDeQuais() {
        assertEquals("Tirou pontos por causa de Alcohol e Paraffinum. Puxou a nota para baixo.",
                TextosMatch.linhaDaResposta(RECURSOS,
                        resposta("controle-de-oleosidade", -3.0, true, "ALCOHOL", "PARAFFINUM")));
    }

    @Test
    public void respostaQueCombinouPoucoMostraAPorcentagem() {
        assertEquals("Combinou só 25%, com Panthenol. Puxou a nota para baixo.",
                TextosMatch.linhaDaResposta(RECURSOS, resposta("eczema", 5.0, false, "PANTHENOL")));
    }

    @Test
    public void respostaQueCombinouOuBloqueouTemFrasePropria() {
        assertEquals("Combinou 80% graças a Glycerin.", TextosMatch.linhaDaResposta(RECURSOS,
                resposta("muito-seca", 16.0, false, "GLYCERIN")));
        assertEquals("Por causa de Retinol, este produto não é indicado para você.",
                TextosMatch.linhaDaResposta(RECURSOS, resposta("gravidez", 0.0, true, "RETINOL")));
        assertEquals("Tirou pontos. Puxou a nota para baixo.",
                TextosMatch.linhaDaResposta(RECURSOS, resposta("reativa", -1.0, true)));
    }

    @Test
    public void semAnaliseOuSemCodeNaoMostraErro() {
        assertNull(TextosMatch.erroDaNota("ANALYSIS_NOT_FOUND"));
        assertNull(TextosMatch.erroDaNota("CODE_NOVO"));
        assertNull(TextosMatch.erroDaNota(null));
    }
}
