package com.venussystem.venusmobile.domain.aviso;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.model.ProdutoEmAnalise;

public final class AvisoDeAnalise {

    public enum Destino {
        PRODUTO,
        PRODUTOS_EM_ANALISE
    }

    static final String TITULO_APROVADO = "Aprovado! 🎉";
    static final String TITULO_RECUSADO = "Ops, não foi dessa vez 😢";
    static final String NOME_PADRAO = "Seu produto";

    public final String titulo;
    public final String texto;
    public final Destino destino;
    public final long produtoId;
    public final String scanId;
    public final boolean aprovado;

    private AvisoDeAnalise(String titulo, String texto, Destino destino, long produtoId, String scanId,
                            boolean aprovado) {
        this.titulo = titulo;
        this.texto = texto;
        this.destino = destino;
        this.produtoId = produtoId;
        this.scanId = scanId;
        this.aprovado = aprovado;
    }

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
                            Destino.PRODUTO, produtoId, id, true);
                }
                return new AvisoDeAnalise(TITULO_APROVADO,
                        (nome == null ? NOME_PADRAO : nome) + " foi aprovado e logo aparece no catálogo.",
                        Destino.PRODUTOS_EM_ANALISE, 0L, id, true);
            }
            case RECUSADO: {
                String quem = nome == null ? NOME_PADRAO : nome;
                String motivo = produto.getMotivo();
                String texto = motivo == null ? quem + " não foi aprovado." : quem + ": " + motivo;
                return new AvisoDeAnalise(TITULO_RECUSADO, texto, Destino.PRODUTOS_EM_ANALISE, 0L, id, false);
            }
            default:
                return null;
        }
    }
}
