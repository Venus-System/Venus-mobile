package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CalculadoraMatch {

    static final String BENEFICIO = "BENEFIT";
    static final String ALERTA = "WARNING";
    static final String CONTRAINDICADO = "CONTRAINDICATION";

    private CalculadoraMatch() {
    }

    public static ResultadoMatch calcular(boolean perfilPreenchido,
                                          Map<Long, String> catalogo,
                                          Set<String> slugsDaPessoa,
                                          Set<String> alergias,
                                          List<IngredienteAnalisado> ingredientes) {
        if (!perfilPreenchido) {
            return new ResultadoMatch(ResultadoMatch.Veredito.SEM_PERFIL,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), false);
        }

        Map<String, Agrupado> pontos = new LinkedHashMap<>();
        Map<String, Destacado> destaques = new LinkedHashMap<>();
        Set<String> alergiasEncontradas = new LinkedHashSet<>();
        boolean catalogoCobre = false;

        for (IngredienteAnalisado ingrediente : ingredientes) {
            if (alergiaNo(ingrediente, alergias)) {
                alergiasEncontradas.add(ingrediente.nome);
            }

            for (IngredienteAnalisado.Efeito efeito : ingrediente.efeitos) {
                catalogoCobre = true;
                String slug = catalogo.get(efeito.profileTagId);
                ResultadoMatch.Tipo tipo = slug != null && slugsDaPessoa.contains(slug)
                        ? tipoParaAPessoa(efeito.categoria) : null;

                if (tipo != null) {
                    pontos.computeIfAbsent(tipo + "|" + slug + "|" + efeito.nome,
                            k -> new Agrupado(tipo, slug, efeito.nome))
                            .ingredientes.add(ingrediente.nome);
                } else if (!efeito.nome.isEmpty()) {
                    String chave = slug != null ? slug : efeito.nome;
                    destaques.computeIfAbsent(chave, k -> new Destacado(slug, efeito.nome,
                            ordemDoDestaque(efeito.categoria)));
                }
            }
        }

        if (catalogoCobre) {
            for (String slug : EtiquetasDoPerfil.ETIQUETAS_DE_AVISO) {
                if (slugsDaPessoa.contains(slug) && !temAviso(pontos, slug)) {
                    pontos.put("ausencia|" + slug,
                            new Agrupado(ResultadoMatch.Tipo.AUSENCIA, slug, ""));
                }
            }
        }

        for (Agrupado a : pontos.values()) destaques.remove(a.slug);

        List<Agrupado> ordenados = new ArrayList<>(pontos.values());
        ordenados.sort(Comparator.comparingInt(a -> a.tipo.ordinal()));
        List<ResultadoMatch.Ponto> listaPontos = new ArrayList<>();
        for (Agrupado a : ordenados) {
            listaPontos.add(new ResultadoMatch.Ponto(a.tipo, a.slug, a.efeito,
                    new ArrayList<>(a.ingredientes)));
        }

        List<Destacado> destaquesOrdenados = new ArrayList<>(destaques.values());
        destaquesOrdenados.sort(Comparator.comparingInt(d -> d.ordem));
        List<ResultadoMatch.Destaque> listaDestaques = new ArrayList<>();
        for (Destacado d : destaquesOrdenados) {
            listaDestaques.add(new ResultadoMatch.Destaque(d.slug, d.efeito));
        }

        return new ResultadoMatch(veredito(alergiasEncontradas, listaPontos), listaPontos,
                listaDestaques, new ArrayList<>(alergiasEncontradas), catalogoCobre);
    }

    private static ResultadoMatch.Veredito veredito(Set<String> alergias,
                                                    List<ResultadoMatch.Ponto> pontos) {
        if (!alergias.isEmpty() || tem(pontos, ResultadoMatch.Tipo.GRAVE)) {
            return ResultadoMatch.Veredito.NAO_RECOMENDADO;
        }
        if (tem(pontos, ResultadoMatch.Tipo.ALERTA)) return ResultadoMatch.Veredito.ATENCAO;
        if (tem(pontos, ResultadoMatch.Tipo.BENEFICIO)) return ResultadoMatch.Veredito.COMBINA;
        return ResultadoMatch.Veredito.SEM_ALERTAS;
    }

    private static boolean tem(List<ResultadoMatch.Ponto> pontos, ResultadoMatch.Tipo tipo) {
        for (ResultadoMatch.Ponto p : pontos) {
            if (p.tipo == tipo) return true;
        }
        return false;
    }

    private static boolean temAviso(Map<String, Agrupado> pontos, String slug) {
        for (Agrupado a : pontos.values()) {
            if (slug.equals(a.slug) && (a.tipo == ResultadoMatch.Tipo.GRAVE
                    || a.tipo == ResultadoMatch.Tipo.ALERTA)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ResultadoMatch.Tipo tipoParaAPessoa(String categoria) {
        if (CONTRAINDICADO.equals(categoria)) return ResultadoMatch.Tipo.GRAVE;
        if (ALERTA.equals(categoria)) return ResultadoMatch.Tipo.ALERTA;
        if (BENEFICIO.equals(categoria)) return ResultadoMatch.Tipo.BENEFICIO;
        return null;
    }

    private static int ordemDoDestaque(String categoria) {
        if (BENEFICIO.equals(categoria)) return 0;
        if (ALERTA.equals(categoria) || CONTRAINDICADO.equals(categoria)) return 2;
        return 1;
    }

    private static boolean alergiaNo(IngredienteAnalisado ingrediente, Set<String> alergias) {
        if (alergias.isEmpty()) return false;
        String slug = "-" + Slug.de(ingrediente.nome) + "-";
        for (String alergia : alergias) {
            if (!alergia.isEmpty() && slug.contains("-" + alergia + "-")) return true;
        }
        return false;
    }

    private static final class Agrupado {
        final ResultadoMatch.Tipo tipo;
        final String slug;
        final String efeito;
        final Set<String> ingredientes = new LinkedHashSet<>();

        Agrupado(ResultadoMatch.Tipo tipo, String slug, String efeito) {
            this.tipo = tipo;
            this.slug = slug;
            this.efeito = efeito;
        }
    }

    private static final class Destacado {
        @Nullable
        final String slug;
        final String efeito;
        final int ordem;

        Destacado(@Nullable String slug, String efeito, int ordem) {
            this.slug = slug;
            this.efeito = efeito;
            this.ordem = ordem;
        }
    }
}
