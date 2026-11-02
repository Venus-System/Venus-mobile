package com.venussystem.venusmobile.domain.match;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.api.MapeadorPerfilApi;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

public final class EtiquetasDoPerfil {
    public static final List<String> ETIQUETAS_DE_AVISO = Arrays.asList(
            "gravidez", "amamentacao", "reativa", "sem-silicone", "sem-sulfato",
            "sem-parabenos");

    private static final Map<String, String> PREFERENCIAS = new HashMap<>();
    private static final Map<String, String> CONDICOES = new HashMap<>();

    static {
        PREFERENCIAS.put("vegano", "vegano");
        PREFERENCIAS.put("cruelty-free", "cruelty-free");
        PREFERENCIAS.put("sem-parabenos", "sem-parabenos");
        PREFERENCIAS.put("sem-sulfatos", "sem-sulfato");
        PREFERENCIAS.put("sem-silicones", "sem-silicone");
        PREFERENCIAS.put("sem-alcool", "sem-alcool");
        PREFERENCIAS.put("sem-oleo", "sem-oleo");
        PREFERENCIAS.put("natural", "ingredientes-naturais");
        PREFERENCIAS.put("organico", "organico");
        PREFERENCIAS.put("hipoalergenico", "hipoalergenico");
        PREFERENCIAS.put("nao-comedogenico", "nao-comedogenico");
        PREFERENCIAS.put("testado-dermatologicamente", "testado-dermatologicamente");

        CONDICOES.put("acneProne", "pele-acneica");
        CONDICOES.put("hasRosacea", "rosacea");
        CONDICOES.put("hasEczema", "eczema");
        CONDICOES.put("hasHyperpigmentation", "hiperpigmentacao");
        CONDICOES.put("hasMelasma", "melasma");
        CONDICOES.put("isPregnant", "gravidez");
        CONDICOES.put("isBreastfeeding", "amamentacao");
    }

    private EtiquetasDoPerfil() {
    }

    private static final Map<String, String> ROTULOS = new HashMap<>();

    static {
        ROTULOS.put("hidratacao", "pele seca");
        ROTULOS.put("controle-de-oleosidade", "pele oleosa");
        ROTULOS.put("pele-acneica", "pele acneica");
        ROTULOS.put("reativa", "pele sensível");
        ROTULOS.put("hiperpigmentacao", "hiperpigmentação");
        ROTULOS.put("amamentacao", "amamentação");
        ROTULOS.put("controle-de-frizz", "cabelo ondulado, cacheado ou crespo");
        ROTULOS.put("couro-cabeludo-sensivel", "couro cabeludo sensível");
        ROTULOS.put("couro-cabeludo-seco", "couro cabeludo seco");
        ROTULOS.put("couro-cabeludo-oleoso", "couro cabeludo oleoso");
        ROTULOS.put("hipoalergenico", "hipoalergênico");
        ROTULOS.put("nao-comedogenico", "não comedogênico");
        ROTULOS.put("sem-alcool", "sem álcool");
        ROTULOS.put("sem-oleo", "sem óleo");
        ROTULOS.put("sem-sulfato", "sem sulfatos");
        ROTULOS.put("sem-silicone", "sem silicones");
        ROTULOS.put("sem-parabenos", "sem parabenos");
        ROTULOS.put("ingredientes-naturais", "natural");
        ROTULOS.put("organico", "orgânico");
        ROTULOS.put("testado-dermatologicamente", "testado dermatologicamente");
    }

    public static List<String> rotulos(Set<String> etiquetas, Set<String> alergias) {
        TreeSet<String> textos = new TreeSet<>();
        for (String slug : etiquetas) {
            String rotulo = ROTULOS.get(slug);
            textos.add(rotulo != null ? rotulo : slug.replace('-', ' '));
        }
        for (String alergia : alergias) {
            textos.add("alergia a " + alergia.replace('-', ' '));
        }
        return new ArrayList<>(textos);
    }

    public static Set<String> slugs(MapeadorPerfilApi.Respostas respostas) {
        Set<String> slugs = new HashSet<>();

        for (String texto : respostas.getLista(PerfilRepository.PREFERENCIAS)) {
            adicionar(slugs, PREFERENCIAS.get(Slug.de(texto)));
        }
        for (String chave : new String[]{PerfilRepository.CONDICOES_PELE,
                PerfilRepository.GESTACAO}) {
            for (String tag : respostas.getLista(chave)) {
                adicionar(slugs, CONDICOES.get(tag));
            }
        }

        String pele = respostas.getTag(PerfilRepository.TIPO_PELE);
        if ("DRY".equals(pele)) slugs.add("hidratacao");
        if ("OILY".equals(pele)) slugs.add("controle-de-oleosidade");
        if ("ACNEIC".equals(pele)) slugs.add("pele-acneica");
        if ("SENSITIVE".equals(pele)) slugs.add("reativa");

        String sensibilidade = respostas.getTag(PerfilRepository.SENSIBILIDADE);
        if ("HIGH".equals(sensibilidade) || "VERY_HIGH".equals(sensibilidade)) {
            slugs.add("reativa");
        }

        String cabelo = respostas.getTag(PerfilRepository.TIPO_CABELO);
        if (cabelo != null && cabelo.matches("TYPE_[234][ABC]")) {
            slugs.add("controle-de-frizz");
        }

        String couro = respostas.getTag(PerfilRepository.COURO_CABELUDO);
        if ("DANDRUFF".equals(couro)) slugs.add("caspa");
        if ("SENSITIVE".equals(couro)) slugs.add("couro-cabeludo-sensivel");
        if ("DRY".equals(couro)) slugs.add("couro-cabeludo-seco");
        if ("OILY".equals(couro)) slugs.add("couro-cabeludo-oleoso");
        return slugs;
    }

    public static Set<String> alergias(MapeadorPerfilApi.Respostas respostas) {
        Set<String> slugs = new HashSet<>();
        for (String alergia : alergiasEmTexto(respostas)) {
            String slug = Slug.de(alergia);
            if (!slug.isEmpty()) slugs.add(slug);
        }
        return slugs;
    }

    public static List<String> alergiasEmTexto(MapeadorPerfilApi.Respostas respostas) {
        List<String> alergias = new ArrayList<>();
        for (String alergia : respostas.getLista(PerfilRepository.ALERGIAS)) {
            if (alergia == null || alergia.trim().isEmpty()
                    || PerfilRepository.NENHUMA.equals(alergia)
                    || PerfilRepository.PREFIRO_NAO_DIZER.equals(alergia)) {
                continue;
            }
            alergias.add(alergia.trim());
        }
        return alergias;
    }

    private static void adicionar(Set<String> destino, @Nullable String slug) {
        if (slug != null) destino.add(slug);
    }
}
