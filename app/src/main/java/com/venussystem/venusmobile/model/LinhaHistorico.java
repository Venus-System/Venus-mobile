package com.venussystem.venusmobile.model;

public class LinhaHistorico {

    public static final int TIPO_DATA = 0;
    public static final int TIPO_PRODUTO = 1;

    private final int tipo;
    private final String data;
    private final Produto produto;
    private final String status;

    private LinhaHistorico(int tipo, String data, Produto produto, String status) {
        this.tipo = tipo;
        this.data = data;
        this.produto = produto;
        this.status = status;
    }

    public static LinhaHistorico deData(String data) {
        return new LinhaHistorico(TIPO_DATA, data, null, null);
    }

    public static LinhaHistorico deProduto(Produto produto) {
        return deProduto(produto, null);
    }

    public static LinhaHistorico deProduto(Produto produto, String status) {
        return new LinhaHistorico(TIPO_PRODUTO, null, produto, status);
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

    public String getStatus() {
        return status;
    }
}
