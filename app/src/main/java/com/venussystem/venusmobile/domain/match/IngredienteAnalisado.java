package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public final class IngredienteAnalisado {
    public static final class Efeito {
        public final long profileTagId;
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
