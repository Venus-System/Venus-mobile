package com.venussystem.venusmobile.model;

import java.time.LocalDate;

public class ItemHistorico {
    private final Produto produto;
    private final LocalDate data;
    private final String status;
    private final String scanId;

    public ItemHistorico(Produto produto, LocalDate data) {
        this(produto, data, null);
    }

    public ItemHistorico(Produto produto, LocalDate data, String status) {
        this(produto, data, status, null);
    }

    public ItemHistorico(Produto produto, LocalDate data, String status, String scanId) {
        this.produto = produto;
        this.data = data;
        this.status = status;
        this.scanId = scanId;
    }

    public Produto getProduto() {
        return produto;
    }

    public LocalDate getData() {
        return data;
    }

    /** Status do scan no ciclo de revisão, por exemplo PENDING_REVIEW. */
    public String getStatus() {
        return status;
    }

    public String getScanId() {
        return scanId;
    }
}
