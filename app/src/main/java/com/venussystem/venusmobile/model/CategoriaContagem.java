package com.venussystem.venusmobile.model;

/** Uma categoria do catalogo junto com quantos produtos ela tem hoje. */
public class CategoriaContagem {

    private final long id;
    private final String nome;
    private final int quantidade;

    public CategoriaContagem(long id, String nome, int quantidade) {
        this.id = id;
        this.nome = nome;
        this.quantidade = quantidade;
    }

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
