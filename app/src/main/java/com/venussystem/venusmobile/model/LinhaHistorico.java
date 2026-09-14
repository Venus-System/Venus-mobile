package com.venussystem.venusmobile.model;

/**
 * Uma linha da lista de historico. A lista mistura dois tipos de linha - a data
 * ("Hoje", "25/07/2026") e o produto escaneado - entao o adapter precisa de um
 * tipo unico que saiba representar os dois.
 */
public class LinhaHistorico {

    public static final int TIPO_DATA = 0;
    public static final int TIPO_PRODUTO = 1;

    private final int tipo;
    private final String data;
    private final Produto produto;

    private LinhaHistorico(int tipo, String data, Produto produto) {
        this.tipo = tipo;
        this.data = data;
        this.produto = produto;
    }

    public static LinhaHistorico deData(String data) {
        return new LinhaHistorico(TIPO_DATA, data, null);
    }

    public static LinhaHistorico deProduto(Produto produto) {
        return new LinhaHistorico(TIPO_PRODUTO, null, produto);
    }

    public int getTipo() {
        return tipo;
    }

    public String getData() {
        return data;
    }

    public Produto getProduto() {
        return produto;
    }
}
