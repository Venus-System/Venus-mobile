package com.venussystem.venusmobile.model;

public class MarcaFiltro {

    private final long id;
    private final String nome;

    public MarcaFiltro(long id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
