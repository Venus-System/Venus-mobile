package com.venussystem.venusmobile.model;

import androidx.annotation.DrawableRes;

public class Colecao {
    private final Long id;
    private final String name;
    private final String imageUrl;
    private final int imagemLocal;
    private final String autor;

    public Colecao(Long id, String name, @DrawableRes int imagemLocal) {
        this(id, name, null, imagemLocal, null);
    }

    public Colecao(Long id, String name, @DrawableRes int imagemLocal, String autor) {
        this(id, name, null, imagemLocal, autor);
    }

    public Colecao(Long id, String name, String imageUrl, int imagemLocal, String autor) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.imagemLocal = imagemLocal;
        this.autor = autor;
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

    public String getAutor() {
        return autor;
    }
}
