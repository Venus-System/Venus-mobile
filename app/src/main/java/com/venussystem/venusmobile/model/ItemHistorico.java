package com.venussystem.venusmobile.model;

import java.time.LocalDate;

public class ItemHistorico {
    private final Produto produto;
    private final LocalDate data;
    private final String status;
    private final String scanId;
    private final long momento;

    public ItemHistorico(Produto produto, LocalDate data) {
        this(produto, data, null);
    }

    public ItemHistorico(Produto produto, LocalDate data, String status) {
        this(produto, data, status, null);
    }

    public ItemHistorico(Produto produto, LocalDate data, String status, String scanId) {
        this(produto, data, status, scanId, 0L);
    }

    public ItemHistorico(Produto produto, LocalDate data, String status, String scanId,
                         long momento) {
        this.produto = produto;
        this.data = data;
        this.status = status;
        this.scanId = scanId;
        this.momento = momento;
    }

    public Produto getProduto() {
        return produto;
    }

    public LocalDate getData() {
        return data;
    }

    /**
     * Status do scan no ciclo de revisão, por exemplo PENDING_REVIEW. Null
     * quando o produto só foi aberto, sem scan.
     */
    public String getStatus() {
        return status;
    }

    public String getScanId() {
        return scanId;
    }

    /**
     * Quando aconteceu, em milissegundos; 0 se não se sabe. A data só separa os
     * dias: é isto que ordena as entradas de um mesmo dia.
     */
    public long getMomento() {
        return momento;
    }
}
