package com.venussystem.venusmobile.domain.aviso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.venussystem.venusmobile.domain.aviso.AvisoDeAnalise.Destino;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;

import org.junit.Test;

public class AvisoDeAnaliseTest {

    private static ProdutoEmAnalise produto(Situacao situacao, String nome, String marca, String motivo,
                                            Long produtoId) {
        return new ProdutoEmAnalise("scan-1", situacao, nome, marca, null, null, null, null, motivo, produtoId);
    }

    @Test
    public void aprovadoNoCatalogo_dizOndeEstaOProdutoEAbreOProduto() {
        AvisoDeAnalise aviso = AvisoDeAnalise.de(produto(Situacao.APROVADO, "Creme Hidratante", "Cerave", null, 57L));

        assertNotNull(aviso);
        assertEquals("Aprovado! 🎉", aviso.titulo);
        assertEquals("Cerave Creme Hidratante já está no catálogo da Venus.", aviso.texto);
        assertEquals(Destino.PRODUTO, aviso.destino);
        assertEquals(57L, aviso.produtoId);
        assertEquals("scan-1", aviso.scanId);
    }

    @Test
    public void aprovadoSemMarca_usaSoONome() {
        AvisoDeAnalise aviso = AvisoDeAnalise.de(produto(Situacao.APROVADO, "Creme Hidratante", null, null, 57L));

        assertEquals("Creme Hidratante já está no catálogo da Venus.", aviso.texto);
    }

    @Test
    public void aprovadoSemNome_dizSeuProdutoENaoUsaAMarca() {
        AvisoDeAnalise aviso = AvisoDeAnalise.de(produto(Situacao.APROVADO, null, "Cerave", null, 57L));

        assertEquals("Seu produto já está no catálogo da Venus.", aviso.texto);
        assertEquals(Destino.PRODUTO, aviso.destino);
    }

    @Test
    public void aprovadoSemEntrarNoCatalogo_dizQueLogoApareceEAbreProdutosEmAnalise() {
        AvisoDeAnalise aviso = AvisoDeAnalise.de(produto(Situacao.APROVADO, "Creme Hidratante", "Cerave", null, null));

        assertEquals("Aprovado! 🎉", aviso.titulo);
        assertEquals("Creme Hidratante foi aprovado e logo aparece no catálogo.", aviso.texto);
        assertEquals(Destino.PRODUTOS_EM_ANALISE, aviso.destino);
    }

    @Test
    public void aprovadoComIdInvalido_abreProdutosEmAnalise() {
        assertEquals(Destino.PRODUTOS_EM_ANALISE,
                AvisoDeAnalise.de(produto(Situacao.APROVADO, "Creme", null, null, 0L)).destino);
        assertEquals(Destino.PRODUTOS_EM_ANALISE,
                AvisoDeAnalise.de(produto(Situacao.APROVADO, "Creme", null, null, -3L)).destino);
    }

    @Test
    public void recusado_mostraONomeEOMotivoEAbreProdutosEmAnalise() {
        AvisoDeAnalise aviso = AvisoDeAnalise.de(
                produto(Situacao.RECUSADO, "Tônico Facial", "Vichy", "Foto do verso ilegível", null));

        assertEquals("Ops, não foi dessa vez 😢", aviso.titulo);
        assertEquals("Tônico Facial: Foto do verso ilegível", aviso.texto);
        assertEquals(Destino.PRODUTOS_EM_ANALISE, aviso.destino);
    }

    @Test
    public void recusadoSemMotivo_naoDeixaOsDoisPontos() {
        AvisoDeAnalise aviso = AvisoDeAnalise.de(produto(Situacao.RECUSADO, "Tônico Facial", null, null, null));

        assertEquals("Tônico Facial não foi aprovado.", aviso.texto);
    }

    @Test
    public void recusadoSemNome_dizSeuProduto() {
        AvisoDeAnalise aviso = AvisoDeAnalise.de(produto(Situacao.RECUSADO, null, null, "Fora do escopo", null));

        assertEquals("Seu produto: Fora do escopo", aviso.texto);
    }

    @Test
    public void quemAindaEsperaDecisao_naoTemAviso() {
        assertNull(AvisoDeAnalise.de(produto(Situacao.AGUARDANDO, "Creme", null, null, null)));
        assertNull(AvisoDeAnalise.de(produto(Situacao.EM_ANALISE, "Creme", null, null, null)));
    }
}
