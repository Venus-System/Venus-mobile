package com.venussystem.venusmobile.model;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.OffsetDateTime;

public class Avaliacao implements Serializable {

    public enum Voto {
        UTIL, NAO_UTIL
    }

    private final long id;
    @Nullable private final String autor;
    private final boolean minha;
    private final double nota;
    private final String titulo;
    private final String comentario;
    private final boolean usoVerificado;
    private final long votosUteis;
    @Nullable private final OffsetDateTime criadaEm;

    public Avaliacao(long id, @Nullable String autor, boolean minha, double nota, String titulo,
                     String comentario, boolean usoVerificado, long votosUteis,
                     @Nullable OffsetDateTime criadaEm) {
        this.id = id;
        this.autor = autor;
        this.minha = minha;
        this.nota = nota;
        this.titulo = titulo;
        this.comentario = comentario;
        this.usoVerificado = usoVerificado;
        this.votosUteis = votosUteis;
        this.criadaEm = criadaEm;
    }

    public long getId() {
        return id;
    }

    @Nullable
    public String getAutor() {
        return autor;
    }

    public boolean isMinha() {
        return minha;
    }

    public double getNota() {
        return nota;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getComentario() {
        return comentario;
    }

    public boolean isUsoVerificado() {
        return usoVerificado;
    }

    public long getVotosUteis() {
        return votosUteis;
    }

    @Nullable
    public OffsetDateTime getCriadaEm() {
        return criadaEm;
    }
}
