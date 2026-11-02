package com.venussystem.venusmobile.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.api.dto.IngredientResponse;

import java.util.Locale;

public class IngredienteProduto {
    private static final String PREFIXO_DESCRICAO_GENERICA = "faz parte da fórmula";

    private final String nome;
    @Nullable private final String nomeInci;
    @Nullable private final String descricao;

    public IngredienteProduto(@NonNull String nome,
                              @Nullable String nomeInci,
                              @Nullable String descricao) {
        this.nome = nome;
        this.nomeInci = nomeInci;
        this.descricao = descricao;
    }

    @Nullable
    public static IngredienteProduto de(@Nullable IngredientResponse ingrediente) {
        if (ingrediente == null) {
            return null;
        }
        String comum = limpar(ingrediente.commonName);
        String inci = limpar(ingrediente.inciName);
        String nome = comum != null ? comum : inci;
        if (nome == null) {
            return null;
        }
        String secundario = comum != null && inci != null && !inci.equalsIgnoreCase(comum)
                ? inci : null;
        return new IngredienteProduto(nome, secundario, descricaoUtil(ingrediente.description));
    }

    @Nullable
    private static String descricaoUtil(@Nullable String descricao) {
        String limpa = limpar(descricao);
        if (limpa == null) {
            return null;
        }
        boolean generica = limpa.toLowerCase(Locale.ROOT)
                .startsWith(PREFIXO_DESCRICAO_GENERICA);
        return generica ? null : limpa;
    }

    @Nullable
    private static String limpar(@Nullable String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    @NonNull
    public String getNome() {
        return nome;
    }

    @Nullable
    public String getNomeInci() {
        return nomeInci;
    }

    @Nullable
    public String getDescricao() {
        return descricao;
    }
}
