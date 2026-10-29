package com.venussystem.venusmobile.model;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDate;

public class ProdutoEmAnalise implements Serializable {
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

    @Nullable
    public LocalDate getDecididoEm() {
        return decididoEm;
    }

    @Nullable
    public String getMotivo() {
        return motivo;
    }

    @Nullable
    public Long getProdutoId() {
        return produtoId;
    }
}
