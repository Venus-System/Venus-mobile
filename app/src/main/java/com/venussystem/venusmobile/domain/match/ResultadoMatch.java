package com.venussystem.venusmobile.domain.match;

import java.util.List;

/** O que a aba "Match com voce" mostra, ja separado em alertas e pontos positivos. */
public final class ResultadoMatch {

    public enum Veredito {
        /** A pessoa ainda nao respondeu o questionario: nada a cruzar. */
        SEM_PERFIL,
        /** Alergia declarada ou efeito contraindicado para o perfil. */
        NAO_RECOMENDADO,
        ATENCAO,
        COMBINA,
        /** Perfil preenchido, mas o catalogo nao tem efeito que o toque. */
        SEM_PONTOS
    }

    /** Um ponto do resultado; ingredientes iguais no mesmo efeito vem agrupados. */
    public static final class Achado {
        public final String titulo;
        public final String descricao;
        public final List<String> ingredientes;
        public final boolean grave;

        public Achado(String titulo, String descricao, List<String> ingredientes,
                      boolean grave) {
            this.titulo = titulo;
            this.descricao = descricao;
            this.ingredientes = ingredientes;
            this.grave = grave;
        }
    }

    public final Veredito veredito;
    public final List<Achado> alertas;
    public final List<Achado> positivos;

    public ResultadoMatch(Veredito veredito, List<Achado> alertas, List<Achado> positivos) {
        this.veredito = veredito;
        this.alertas = alertas;
        this.positivos = positivos;
    }
}
