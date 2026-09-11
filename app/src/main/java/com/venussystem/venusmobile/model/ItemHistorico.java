package com.venussystem.venusmobile.model;

import java.time.LocalDate;

public class ItemHistorico {
    private final Produto produto;
    private final LocalDate data;

    public ItemHistorico(Produto produto, LocalDate data) {
        this.produto = produto;
        this.data = data;
    }

    public Produto getProduto() {
        return produto;
    }

    public LocalDate getData() {
        return data;
    }
}
