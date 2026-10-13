package com.venussystem.venusmobile.model;

import androidx.annotation.DrawableRes;

public class Colecao {
    private final Long id;
    private final String name;
    private final String imageUrl;
    private final int imagemLocal;
    private final String descricao;

    public Colecao(Long id, String name, @DrawableRes int imagemLocal) {
        this(id, name, null, imagemLocal, null);
    }

    public Colecao(Long id, String name, String descricao) {
        this(id, name, null, 0, descricao);
    }

    public Colecao(Long id, String name, String imageUrl, int imagemLocal, String descricao) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.imagemLocal = imagemLocal;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    @DrawableRes
    public int getImagemLocal() {
        return imagemLocal;
    }

    public String getDescricao() {
        return descricao;
    }
}
