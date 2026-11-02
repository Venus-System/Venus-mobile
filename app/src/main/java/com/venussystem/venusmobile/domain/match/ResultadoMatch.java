package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.util.List;

public final class ResultadoMatch {

    public enum Veredito {
        SEM_PERFIL,
        NAO_RECOMENDADO,
        ATENCAO,
        COMBINA,
        SEM_ALERTAS
    }

    public enum Tipo {
        GRAVE,
        ALERTA,
        BENEFICIO,
        AUSENCIA
    }

    public static final class Ponto {
        public final Tipo tipo;
        public final String slug;
        public final String efeito;
        public final List<String> ingredientes;

        public Ponto(Tipo tipo, String slug, String efeito, List<String> ingredientes) {
            this.tipo = tipo;
            this.slug = slug;
            this.efeito = efeito;
            this.ingredientes = ingredientes;
        }
    }

    public static final class Destaque {
        @Nullable
        public final String slug;
        public final String efeito;

        public Destaque(@Nullable String slug, String efeito) {
            this.slug = slug;
            this.efeito = efeito;
        }
    }

    public final Veredito veredito;
    public final List<Ponto> pontos;
    public final List<Destaque> destaques;
    public final List<String> alergiasEncontradas;
    public final boolean catalogoCobre;

    public ResultadoMatch(Veredito veredito, List<Ponto> pontos, List<Destaque> destaques,
                          List<String> alergiasEncontradas, boolean catalogoCobre) {
        this.veredito = veredito;
        this.pontos = pontos;
        this.destaques = destaques;
        this.alergiasEncontradas = alergiasEncontradas;
        this.catalogoCobre = catalogoCobre;
    }
}
