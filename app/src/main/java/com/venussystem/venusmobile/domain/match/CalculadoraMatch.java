package com.venussystem.venusmobile.domain.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cruza o perfil da pessoa com os efeitos dos ingredientes do produto.
 *
 * Nao inventa nota: so diz o que o catalogo registra para as etiquetas e
 * alergias da pessoa. A nota 0-100 personalizada fica para o motor de
 * classificacao, quando ele existir.
 */
public final class CalculadoraMatch {

    static final String BENEFICIO = "BENEFIT";
    static final String ALERTA = "WARNING";
    static final String CONTRAINDICADO = "CONTRAINDICATION";

    private CalculadoraMatch() {
    }

    /**
     * @param perfilPreenchido false quando a pessoa ainda nao respondeu o questionario
     * @param etiquetasDaPessoa ids de profile-tags que descrevem a pessoa
     * @param alergias alergias escolhidas, ja em slug (ver paraSlug)
     */
    public static ResultadoMatch calcular(boolean perfilPreenchido,
                                          Set<Long> etiquetasDaPessoa,
                                          Set<String> alergias,
                                          List<IngredienteAnalisado> ingredientes) {
        if (!perfilPreenchido) {
            return new ResultadoMatch(ResultadoMatch.Veredito.SEM_PERFIL,
                    Collections.emptyList(), Collections.emptyList());
        }

        Map<String, Agrupado> alertas = new LinkedHashMap<>();
        Map<String, Agrupado> positivos = new LinkedHashMap<>();
        boolean grave = false;

        for (IngredienteAnalisado ingrediente : ingredientes) {
            if (alergiaNo(ingrediente, alergias)) {
                grave = true;
                alertas.computeIfAbsent("alergia", k -> new Agrupado(
                        "Ingrediente da sua lista de alergias",
                        "Você marcou uma alergia que aparece na composição.", true))
                        .ingredientes.add(ingrediente.nome);
            }

            for (IngredienteAnalisado.Efeito efeito : ingrediente.efeitos) {
                if (!etiquetasDaPessoa.contains(efeito.profileTagId)) continue;

                boolean contra = CONTRAINDICADO.equals(efeito.categoria);
                if (contra || ALERTA.equals(efeito.categoria)) {
                    grave |= contra;
                    alertas.computeIfAbsent("a" + efeito.profileTagId + efeito.nome,
                            k -> new Agrupado(efeito.nome, efeito.descricao, contra))
                            .ingredientes.add(ingrediente.nome);
                } else if (BENEFICIO.equals(efeito.categoria)) {
                    positivos.computeIfAbsent("p" + efeito.profileTagId + efeito.nome,
                            k -> new Agrupado(efeito.nome, efeito.descricao, false))
                            .ingredientes.add(ingrediente.nome);
                }
            }
        }

        ResultadoMatch.Veredito veredito;
        if (grave) {
            veredito = ResultadoMatch.Veredito.NAO_RECOMENDADO;
        } else if (!alertas.isEmpty()) {
            veredito = ResultadoMatch.Veredito.ATENCAO;
        } else if (!positivos.isEmpty()) {
            veredito = ResultadoMatch.Veredito.COMBINA;
        } else {
            veredito = ResultadoMatch.Veredito.SEM_PONTOS;
        }
        return new ResultadoMatch(veredito, converter(alertas), converter(positivos));
    }

    /** O slug do ingrediente tem de conter o da alergia como palavra inteira. */
    private static boolean alergiaNo(IngredienteAnalisado ingrediente, Set<String> alergias) {
        if (alergias.isEmpty()) return false;
        String slug = "-" + Slug.de(ingrediente.nome) + "-";
        for (String alergia : alergias) {
            if (!alergia.isEmpty() && slug.contains("-" + alergia + "-")) return true;
        }
        return false;
    }

    private static List<ResultadoMatch.Achado> converter(Map<String, Agrupado> mapa) {
        List<ResultadoMatch.Achado> lista = new ArrayList<>();
        for (Agrupado a : mapa.values()) {
            lista.add(new ResultadoMatch.Achado(a.titulo,
                    a.descricao == null ? "" : a.descricao,
                    new ArrayList<>(a.ingredientes), a.grave));
        }
        return lista;
    }

    private static final class Agrupado {
        final String titulo;
        final String descricao;
        final boolean grave;
        final Set<String> ingredientes = new java.util.LinkedHashSet<>();

        Agrupado(String titulo, String descricao, boolean grave) {
            this.titulo = titulo;
            this.descricao = descricao;
            this.grave = grave;
        }
    }
}
