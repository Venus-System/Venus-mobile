package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * O que a aba "Match com voce" mostra: os pontos do perfil, os destaques da
 * composicao que valem para qualquer pessoa e as alergias encontradas.
 * Os textos ficam na tela (TextosMatch); aqui vao so slug e tipo.
 */
public final class ResultadoMatch {

    public enum Veredito {
        /** A pessoa ainda nao respondeu o questionario: nada a cruzar. */
        SEM_PERFIL,
        /** Alergia declarada ou efeito contraindicado para o perfil. */
        NAO_RECOMENDADO,
        ATENCAO,
        COMBINA,
        /** Perfil preenchido e nada na composicao vai contra ele. */
        SEM_ALERTAS
    }

    /** Na ordem em que aparecem na tela. */
    public enum Tipo {
        GRAVE,
        ALERTA,
        BENEFICIO,
        /** Etiqueta de aviso da pessoa sem nada na composicao: "Sem silicones". */
        AUSENCIA
    }

    /** Um ponto do perfil; ingredientes iguais no mesmo efeito vem agrupados. */
    public static final class Ponto {
        public final Tipo tipo;
        /** Etiqueta do catalogo (profile-tags) que ligou o efeito a pessoa. */
        public final String slug;
        /** Nome do efeito no catalogo, para quando a tela nao tem texto do slug. */
        public final String efeito;
        public final List<String> ingredientes;

        public Ponto(Tipo tipo, String slug, String efeito, List<String> ingredientes) {
            this.tipo = tipo;
            this.slug = slug;
            this.efeito = efeito;
            this.ingredientes = ingredientes;
        }
    }

    /** Efeito da composicao fora do perfil da pessoa, mostrado como chip. */
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
    /** Ingredientes que batem com a lista de alergias da pessoa. */
    public final List<String> alergiasEncontradas;
    /** false quando nenhum ingrediente tem efeito no catalogo. */
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
