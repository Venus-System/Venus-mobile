package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Junta aos pontos do calculo no aparelho os motivos da Venus-Classificacao.
 * O texto que a API manda vem do banco e hoje e quase todo placeholder de teste
 * ("Regra de teste criada..."), entao daqui so sai o que e dado: ingrediente,
 * etiqueta e se ajudou ou atrapalhou. A tela escreve o texto (TextosMatch).
 */
public final class PontosDaClassificacao {

    /** Um motivo de regra de ingrediente da Classificacao. */
    public static final class Motivo {
        public final String slug;
        public final String ingrediente;
        /** true para penalidade ou bloqueio. */
        public final boolean aviso;

        public Motivo(String slug, String ingrediente, boolean aviso) {
            this.slug = slug;
            this.ingrediente = ingrediente;
            this.aviso = aviso;
        }
    }

    private PontosDaClassificacao() {
    }

    /**
     * A Classificacao manda: o que ela achou e o aparelho nao, entra; e um aviso
     * dela derruba o "Sem X" do aparelho para a mesma etiqueta.
     */
    public static List<ResultadoMatch.Ponto> mesclar(List<ResultadoMatch.Ponto> locais,
                                                     @Nullable List<Motivo> motivos) {
        if (motivos == null || motivos.isEmpty()) return locais;

        Map<String, Set<String>> porGrupo = new LinkedHashMap<>();
        for (Motivo m : motivos) {
            if (m == null || vazio(m.slug)) continue;
            String chave = (m.aviso ? "a|" : "b|") + m.slug;
            Set<String> ingredientes = porGrupo.computeIfAbsent(chave, k -> new LinkedHashSet<>());
            if (!vazio(m.ingrediente)) ingredientes.add(m.ingrediente);
        }

        List<ResultadoMatch.Ponto> saida = new ArrayList<>();
        Set<String> avisosDaApi = new LinkedHashSet<>();
        for (String chave : porGrupo.keySet()) {
            if (chave.startsWith("a|")) avisosDaApi.add(chave.substring(2));
        }
        for (ResultadoMatch.Ponto p : locais) {
            boolean ausenciaDesmentida = p.tipo == ResultadoMatch.Tipo.AUSENCIA
                    && avisosDaApi.contains(p.slug);
            if (!ausenciaDesmentida) saida.add(p);
        }

        for (Map.Entry<String, Set<String>> grupo : porGrupo.entrySet()) {
            boolean aviso = grupo.getKey().startsWith("a|");
            String slug = grupo.getKey().substring(2);
            if (jaTem(saida, slug, aviso)) continue;
            saida.add(new ResultadoMatch.Ponto(
                    aviso ? ResultadoMatch.Tipo.ALERTA : ResultadoMatch.Tipo.BENEFICIO,
                    slug, "", new ArrayList<>(grupo.getValue())));
        }

        saida.sort(Comparator.comparingInt(p -> p.tipo.ordinal()));
        return saida;
    }

    /**
     * Ingredientes da alergia: primeiro os da Classificacao, que liga a alergia
     * aos ingredientes pelo cadastro (Fragrancia pega LIMONENO), depois os que
     * o aparelho achou pelo nome. Sem repetir o mesmo ingrediente.
     */
    public static List<String> alergias(List<String> doAparelho,
                                        @Nullable List<String> daClassificacao) {
        Map<String, String> porSlug = new LinkedHashMap<>();
        if (daClassificacao != null) {
            for (String nome : daClassificacao) {
                if (!vazio(nome)) porSlug.putIfAbsent(Slug.de(nome), nome);
            }
        }
        for (String nome : doAparelho) {
            if (!vazio(nome)) porSlug.putIfAbsent(Slug.de(nome), nome);
        }
        return new ArrayList<>(porSlug.values());
    }

    private static boolean jaTem(List<ResultadoMatch.Ponto> pontos, String slug, boolean aviso) {
        for (ResultadoMatch.Ponto p : pontos) {
            if (!slug.equals(p.slug)) continue;
            boolean pontoEAviso = p.tipo == ResultadoMatch.Tipo.GRAVE
                    || p.tipo == ResultadoMatch.Tipo.ALERTA;
            if (aviso == pontoEAviso && p.tipo != ResultadoMatch.Tipo.AUSENCIA) return true;
        }
        return false;
    }

    private static boolean vazio(@Nullable String texto) {
        return texto == null || texto.trim().isEmpty();
    }
}
