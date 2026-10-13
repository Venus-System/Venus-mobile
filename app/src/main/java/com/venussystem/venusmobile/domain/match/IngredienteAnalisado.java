package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** Um ingrediente do produto com os efeitos que o catalogo registra para ele. */
public final class IngredienteAnalisado {

    /** Efeito do catalogo (ingredient-effects) ligado a uma etiqueta de perfil. */
    public static final class Efeito {
        public final long profileTagId;
        /** BENEFIT, WARNING, CONTRAINDICATION ou NEUTRAL, como vem da API. */
        public final String categoria;
        public final String nome;
        @Nullable
        public final String descricao;

        public Efeito(long profileTagId, String categoria, String nome,
                      @Nullable String descricao) {
            this.profileTagId = profileTagId;
            this.categoria = categoria == null ? "" : categoria;
            this.nome = nome == null ? "" : nome;
            this.descricao = descricao;
        }
    }

    public final String nome;
    public final List<Efeito> efeitos;

    public IngredienteAnalisado(String nome, @Nullable List<Efeito> efeitos) {
        this.nome = nome == null ? "" : nome;
        this.efeitos = efeitos == null ? Collections.emptyList() : efeitos;
    }
}
