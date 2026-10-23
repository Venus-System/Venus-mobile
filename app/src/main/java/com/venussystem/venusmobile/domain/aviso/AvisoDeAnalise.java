package com.venussystem.venusmobile.domain.aviso;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.model.ProdutoEmAnalise;

/**
 * O aviso de "a equipe decidiu sobre o seu produto": o texto da notificacao e a
 * tela que abre ao tocar. Java puro, sem Android: se testa sem emulador.
 *
 * Nasce de um {@link ProdutoEmAnalise} que acabou de ser aprovado ou recusado.
 * O nome e a marca sao os que o OCR leu na frente do produto; sem nome, o texto
 * fala em "Seu produto".
 */
public final class AvisoDeAnalise {

    public enum Destino {
        /** A tela do produto, ja no catalogo. */
        PRODUTO,
        /** A lista de produtos em analise. */
        PRODUTOS_EM_ANALISE
    }

    static final String TITULO_APROVADO = "Produto aprovado";
    static final String TITULO_RECUSADO = "Produto não aprovado";
    static final String NOME_PADRAO = "Seu produto";

    public final String titulo;
    public final String texto;
    public final Destino destino;
    /** O id no catalogo; so vale quando o destino e {@link Destino#PRODUTO}. */
    public final long produtoId;
    /** O id do scan, para a notificacao de cada produto ter o seu proprio numero. */
    public final String scanId;

    private AvisoDeAnalise(String titulo, String texto, Destino destino, long produtoId, String scanId) {
        this.titulo = titulo;
        this.texto = texto;
        this.destino = destino;
        this.produtoId = produtoId;
        this.scanId = scanId;
    }

    /**
     * @return o aviso de um produto aprovado ou recusado; null para um que ainda
     * espera decisao (nao ha o que avisar).
     */
    @Nullable
    public static AvisoDeAnalise de(ProdutoEmAnalise produto) {
        String nome = produto.getNome();
        String marca = produto.getMarca();
        String id = produto.getId() == null ? "" : produto.getId();

        switch (produto.getSituacao()) {
            case APROVADO: {
                Long produtoId = produto.getProdutoId();
                if (produtoId != null && produtoId > 0) {
                    String quem = nome == null ? NOME_PADRAO : (marca == null ? nome : marca + " " + nome);
                    return new AvisoDeAnalise(TITULO_APROVADO, quem + " já está no catálogo da Venus.",
                            Destino.PRODUTO, produtoId, id);
                }
                // Aprovado, mas ainda sem entrar no catalogo (a sincronizacao falhou ou
                // esta pendente): o produto ainda nao tem tela propria para abrir.
                return new AvisoDeAnalise(TITULO_APROVADO,
                        (nome == null ? NOME_PADRAO : nome) + " foi aprovado e logo aparece no catálogo.",
                        Destino.PRODUTOS_EM_ANALISE, 0L, id);
            }
            case RECUSADO: {
                String quem = nome == null ? NOME_PADRAO : nome;
                String motivo = produto.getMotivo();
                String texto = motivo == null ? quem + " não foi aprovado." : quem + ": " + motivo;
                return new AvisoDeAnalise(TITULO_RECUSADO, texto, Destino.PRODUTOS_EM_ANALISE, 0L, id);
            }
            default:
                return null;
        }
    }
}
