package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Uma seção da grade de sugestões da home. */
public final class ProdutoSecao {
    private final String nome;
    private final List<Produto> produtos;

    public ProdutoSecao(@NonNull String nome, @NonNull List<Produto> produtos) {
        this.nome = nome;
        this.produtos = Collections.unmodifiableList(new ArrayList<>(produtos));
    }

    @NonNull
    public String getNome() {
        return nome;
    }

    @NonNull
    public List<Produto> getProdutos() {
        return produtos;
    }
}
