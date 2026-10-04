package com.venussystem.venusmobile.view.util;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SituacaoAnaliseUtilTest {

    @Test
    public void cadaSituacao_temOSeuRotulo() {
        assertEquals(R.string.situacao_aguardando, SituacaoAnaliseUtil.rotulo(Situacao.AGUARDANDO));
        assertEquals(R.string.situacao_em_analise, SituacaoAnaliseUtil.rotulo(Situacao.EM_ANALISE));
        assertEquals(R.string.situacao_aprovado, SituacaoAnaliseUtil.rotulo(Situacao.APROVADO));
        assertEquals(R.string.situacao_recusado, SituacaoAnaliseUtil.rotulo(Situacao.RECUSADO));
    }

    @Test
    public void cadaSituacao_temUmaCorDiferente() {
        Set<Integer> cores = new HashSet<>();
        for (Situacao situacao : Situacao.values()) {
            cores.add(SituacaoAnaliseUtil.corTexto(situacao));
        }
        assertEquals(Situacao.values().length, cores.size());
    }

    @Test
    public void aprovadoECorDeNotaBoa_recusadoECorDeNotaRuim() {
        assertEquals(R.color.nota_boa, SituacaoAnaliseUtil.corTexto(Situacao.APROVADO));
        assertEquals(R.color.nota_boa_fundo, SituacaoAnaliseUtil.corFundo(Situacao.APROVADO));
        assertEquals(R.color.nota_ruim, SituacaoAnaliseUtil.corTexto(Situacao.RECUSADO));
        assertEquals(R.color.nota_ruim_fundo, SituacaoAnaliseUtil.corFundo(Situacao.RECUSADO));
    }

    @Test
    public void aprovadoJaNoCatalogo_dizQueEstaNoCatalogo() {
        assertEquals(R.string.situacao_aprovado_explicacao,
                SituacaoAnaliseUtil.explicacao(Situacao.APROVADO, 57L));
    }

    @Test
    public void aprovadoQueAindaNaoEntrouNoCatalogo_dizQueEmBreveAparece() {
        assertEquals(R.string.situacao_aprovado_aguardando_catalogo_explicacao,
                SituacaoAnaliseUtil.explicacao(Situacao.APROVADO, null));
    }

    @Test
    public void recusado_explicaQueNaoEntrou() {
        assertEquals(R.string.situacao_recusado_explicacao,
                SituacaoAnaliseUtil.explicacao(Situacao.RECUSADO, null));
    }
}
