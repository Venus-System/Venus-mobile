package com.venussystem.venusmobile.model;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Um produto que a pessoa escaneou, nao achou no catalogo e mandou para a
 * equipe conferir. A lista vem de GET /api/scan-sessions/user/{userId}.
 *
 * Serializable para ir inteiro no Intent da lista para o detalhe, sem pedir
 * de novo para a API.
 */
public class ProdutoEmAnalise implements Serializable {

    /**
     * Os seis status da API viram quatro para a pessoa: APPROVED, SYNCED e
     * SYNC_FAILED sao todos "aprovado" - a diferenca e so se o produto ja
     * entrou no catalogo, e isso aparece como o botao "Ver produto".
     */
    public enum Situacao {
        AGUARDANDO, EM_ANALISE, APROVADO, RECUSADO
    }

    private final String id;
    private final Situacao situacao;
    @Nullable private final String nome;
    @Nullable private final String marca;
    @Nullable private final String fotoFrente;
    @Nullable private final String fotoVerso;
    @Nullable private final LocalDate enviadoEm;
    @Nullable private final LocalDate decididoEm;
    @Nullable private final String motivo;
    @Nullable private final Long produtoId;

    public ProdutoEmAnalise(String id, Situacao situacao, @Nullable String nome,
                            @Nullable String marca, @Nullable String fotoFrente,
                            @Nullable String fotoVerso, @Nullable LocalDate enviadoEm,
                            @Nullable LocalDate decididoEm, @Nullable String motivo,
                            @Nullable Long produtoId) {
        this.id = id;
        this.situacao = situacao;
        this.nome = nome;
        this.marca = marca;
        this.fotoFrente = fotoFrente;
        this.fotoVerso = fotoVerso;
        this.enviadoEm = enviadoEm;
        this.decididoEm = decididoEm;
        this.motivo = motivo;
        this.produtoId = produtoId;
    }

    public String getId() {
        return id;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    /** O nome que o OCR leu na frente; null quando nao leu nada. */
    @Nullable
    public String getNome() {
        return nome;
    }

    @Nullable
    public String getMarca() {
        return marca;
    }

    @Nullable
    public String getFotoFrente() {
        return fotoFrente;
    }

    @Nullable
    public String getFotoVerso() {
        return fotoVerso;
    }

    @Nullable
    public LocalDate getEnviadoEm() {
        return enviadoEm;
    }

    /** Quando a equipe aprovou ou recusou; null enquanto ninguem decidiu. */
    @Nullable
    public LocalDate getDecididoEm() {
        return decididoEm;
    }

    /** Por que foi recusado (ou a observacao da aprovacao). */
    @Nullable
    public String getMotivo() {
        return motivo;
    }

    /** O produto no catalogo, so depois que ele entrou la (status SYNCED). */
    @Nullable
    public Long getProdutoId() {
        return produtoId;
    }
}
