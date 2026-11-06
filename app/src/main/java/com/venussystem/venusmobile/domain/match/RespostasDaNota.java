package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RespostasDaNota {
    public enum Efeito {
        BLOQUEOU,
        TIROU,
        COMBINOU_POUCO,
        COMBINOU
    }

    public static final class Motivo {
        public final String slug;
        @Nullable
        public final String ingrediente;
        @Nullable
        public final Double impacto;
        public final boolean aviso;

        public Motivo(String slug, @Nullable String ingrediente, @Nullable Double impacto,
                      boolean aviso) {
            this.slug = slug;
            this.ingrediente = ingrediente;
            this.impacto = impacto;
            this.aviso = aviso;
        }
    }

    public static final class Resposta {
        public final String slug;
        public final Efeito efeito;
        public final int porcento;
        public final List<String> ingredientes;

        Resposta(String slug, Efeito efeito, int porcento, List<String> ingredientes) {
            this.slug = slug;
            this.efeito = efeito;
            this.porcento = porcento;
            this.ingredientes = ingredientes;
        }

        public boolean puxouParaBaixo() {
            return efeito != Efeito.COMBINOU;
        }
    }

    static final double IMPACTO_DA_PERGUNTA_CHEIA = 20.0;
    static final int PORCENTO_PARA_COMBINAR = 50;

    private static final Map<String, String> MESMA_ETIQUETA_NO_APARELHO = new HashMap<>();

    static {
        MESMA_ETIQUETA_NO_APARELHO.put("muito-seca", "hidratacao");
    }

    private RespostasDaNota() {
    }

    public static List<Resposta> de(@Nullable Collection<Motivo> motivos) {
        List<Resposta> saida = new ArrayList<>();
        if (motivos == null) return saida;

        Map<String, Grupo> grupos = new LinkedHashMap<>();
        for (Motivo m : motivos) {
            if (m == null || m.slug == null || m.slug.trim().isEmpty()) continue;
            boolean bloqueio = m.aviso && m.impacto != null && m.impacto == 0;
            boolean avisoSemNumero = m.aviso && m.impacto == null;
            boolean mudouANota = m.impacto != null && m.impacto != 0;
            if (!bloqueio && !avisoSemNumero && !mudouANota) continue;

            Grupo g = grupos.computeIfAbsent(m.slug.trim(), Grupo::new);
            if (bloqueio) g.bloqueou = true;
            if (avisoSemNumero) g.tirouSemNumero = true;
            if (mudouANota) g.soma += m.impacto;
            if (m.ingrediente != null && !m.ingrediente.trim().isEmpty()) {
                g.ingredientes.add(m.ingrediente.trim());
            }
        }

        for (Grupo g : grupos.values()) {
            Efeito efeito = g.efeito();
            if (efeito != null) {
                saida.add(new Resposta(g.slug, efeito, g.porcento(),
                        new ArrayList<>(g.ingredientes)));
            }
        }
        saida.sort(Comparator.<Resposta>comparingInt(r -> r.efeito.ordinal())
                .thenComparingInt(r -> r.efeito == Efeito.COMBINOU ? -r.porcento : r.porcento));
        return saida;
    }

    public static boolean algumaPuxouParaBaixo(List<Resposta> respostas) {
        for (Resposta r : respostas) {
            if (r.puxouParaBaixo()) return true;
        }
        return false;
    }

    public static Set<String> etiquetasCobertas(List<Resposta> respostas) {
        Set<String> slugs = new HashSet<>();
        for (Resposta r : respostas) {
            slugs.add(r.slug);
            String noAparelho = MESMA_ETIQUETA_NO_APARELHO.get(r.slug);
            if (noAparelho != null) slugs.add(noAparelho);
        }
        return slugs;
    }

    public static List<ResultadoMatch.Ponto> semRepetir(List<ResultadoMatch.Ponto> pontos,
                                                        List<Resposta> respostas) {
        Set<String> cobertas = etiquetasCobertas(respostas);
        List<ResultadoMatch.Ponto> saida = new ArrayList<>();
        for (ResultadoMatch.Ponto p : pontos) {
            boolean jaExplicado = (p.tipo == ResultadoMatch.Tipo.ALERTA
                    || p.tipo == ResultadoMatch.Tipo.BENEFICIO) && cobertas.contains(p.slug);
            if (!jaExplicado) saida.add(p);
        }
        return saida;
    }

    private static final class Grupo {
        final String slug;
        final Set<String> ingredientes = new LinkedHashSet<>();
        double soma;
        boolean bloqueou;
        boolean tirouSemNumero;

        Grupo(String slug) {
            this.slug = slug;
        }

        int porcento() {
            if (bloqueou || tirouSemNumero || soma <= 0) return 0;
            double parte = Math.min(1.0, soma / IMPACTO_DA_PERGUNTA_CHEIA);
            return (int) Math.round(parte * 100);
        }

        @Nullable
        Efeito efeito() {
            if (bloqueou) return Efeito.BLOQUEOU;
            if (soma < 0 || tirouSemNumero) return Efeito.TIROU;
            if (soma > 0) {
                return porcento() < PORCENTO_PARA_COMBINAR ? Efeito.COMBINOU_POUCO : Efeito.COMBINOU;
            }
            return null;
        }
    }
}
