package com.venussystem.venusmobile.repository.api;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.PreferenceCatalogResponse;
import com.venussystem.venusmobile.repository.api.dto.UserAllergyDetailResponse;
import com.venussystem.venusmobile.repository.api.dto.UserFullProfileResponse;
import com.venussystem.venusmobile.repository.api.dto.UserPreferenceRequest;
import com.venussystem.venusmobile.repository.api.dto.UserPreferenceResponse;
import com.venussystem.venusmobile.repository.api.dto.UserProfileRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileResponse;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MapeadorPerfilApi {
    public interface Respostas {
        @Nullable
        String getTag(String chave);

        List<String> getLista(String chave);
    }

    private static final Set<String> GENEROS =
            Set.of("FEMALE", "MALE", "NON_BINARY", "OTHER", "PREFER_NOT_SAY");
    private static final Set<String> TIPOS_PELE =
            Set.of("NORMAL", "DRY", "OILY", "COMBINATION", "SENSITIVE", "ACNEIC", "OTHER");
    private static final Set<String> FOTOTIPOS = Set.of("I", "II", "III", "IV", "V", "VI");
    private static final Set<String> CABELOS = Set.of(
            "TYPE_1A", "TYPE_1B", "TYPE_1C", "TYPE_2A", "TYPE_2B", "TYPE_2C",
            "TYPE_3A", "TYPE_3B", "TYPE_3C", "TYPE_4A", "TYPE_4B", "TYPE_4C");
    private static final Set<String> COUROS =
            Set.of("NORMAL", "DRY", "OILY", "SENSITIVE", "DANDRUFF", "OTHER");
    private static final Set<String> SENSIBILIDADES = Set.of("LOW", "MEDIUM", "HIGH", "VERY_HIGH");

    private static final Set<String> FAIXAS_ETARIAS =
            Set.of("AGE_18_24", "AGE_25_34", "AGE_35_44", "AGE_45_54", "AGE_55_PLUS");

    private static final Set<String> TIPOS_PELE_NO_APP =
            Set.of("NORMAL", "DRY", "OILY", "COMBINATION", "SENSITIVE");
    private static final Set<String> CABELOS_NO_APP = Set.of(
            "TYPE_1", "TYPE_2A", "TYPE_2B", "TYPE_2C",
            "TYPE_3A", "TYPE_3B", "TYPE_3C", "TYPE_4A", "TYPE_4B", "TYPE_4C");
    private static final Set<String> SENSIBILIDADES_NO_APP = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> FAIXAS_ETARIAS_NO_APP = Set.of(
            "AGE_13_17", "AGE_18_24", "AGE_25_34", "AGE_35_44", "AGE_45_54", "AGE_55_PLUS");

    private static final String CABELO_TIPO_1_APP = "TYPE_1";
    private static final String CABELO_TIPO_1_API = "TYPE_1A";

    private static final String VEGANO = "vegano";
    private static final String CRUELTY_FREE = "cruelty-free";
    private static final String SEM_PARABENOS = "sem-parabenos";
    private static final String SEM_SULFATOS = "sem-sulfatos";
    private static final String SEM_SILICONES = "sem-silicones";

    private static final Map<String, List<String>> PREFERENCIAS_EM_ETIQUETA = new HashMap<>();

    static {
        PREFERENCIAS_EM_ETIQUETA.put("sem-alcool", Collections.singletonList("sem-alcool"));
        PREFERENCIAS_EM_ETIQUETA.put("sem-oleo", Collections.singletonList("sem-oleo"));
        PREFERENCIAS_EM_ETIQUETA.put("natural", Arrays.asList("natural", "ingredientes-naturais"));
        PREFERENCIAS_EM_ETIQUETA.put("organico", Collections.singletonList("organico"));
        PREFERENCIAS_EM_ETIQUETA.put("hipoalergenico", Collections.singletonList("hipoalergenico"));
        PREFERENCIAS_EM_ETIQUETA.put("nao-comedogenico",
                Collections.singletonList("nao-comedogenico"));
        PREFERENCIAS_EM_ETIQUETA.put("testado-dermatologicamente",
                Collections.singletonList("testado-dermatologicamente"));
        PREFERENCIAS_EM_ETIQUETA.put("embalagem-reciclavel",
                Collections.singletonList("embalagem-reciclavel"));
    }

    private MapeadorPerfilApi() {
    }

    @Nullable
    public static UserProfileRequest perfil(long userId, Respostas respostas) {
        String genero = valida(respostas.getTag(PerfilRepository.GENERO), GENEROS);
        String pele = valida(respostas.getTag(PerfilRepository.TIPO_PELE), TIPOS_PELE);
        String fototipo = valida(respostas.getTag(PerfilRepository.FOTOTIPO), FOTOTIPOS);
        String cabelo = valida(cabeloNaApi(respostas.getTag(PerfilRepository.TIPO_CABELO)), CABELOS);
        String couro = valida(respostas.getTag(PerfilRepository.COURO_CABELUDO), COUROS);
        String sensibilidade =
                valida(respostas.getTag(PerfilRepository.SENSIBILIDADE), SENSIBILIDADES);
        String faixa = valida(respostas.getTag(PerfilRepository.FAIXA_ETARIA), FAIXAS_ETARIAS);

        if (genero == null || pele == null || fototipo == null || cabelo == null
                || couro == null || sensibilidade == null || faixa == null) {
            return null;
        }

        UserProfileRequest corpo = new UserProfileRequest();
        corpo.userId = userId;
        corpo.gender = genero;
        corpo.skinType = pele;
        corpo.skinPhototype = fototipo;
        corpo.hairPattern = cabelo;
        corpo.scalpType = couro;
        corpo.skinSensitivity = sensibilidade;
        corpo.ageRange = faixa;

        List<String> condicoes = respostas.getLista(PerfilRepository.CONDICOES_PELE);
        corpo.acneProne = marcou(condicoes, "acneProne");
        corpo.hasRosacea = marcou(condicoes, "hasRosacea");
        corpo.hasEczema = marcou(condicoes, "hasEczema");
        corpo.hasHyperpigmentation = marcou(condicoes, "hasHyperpigmentation");
        corpo.hasMelasma = marcou(condicoes, "hasMelasma");

        List<String> gestacao = respostas.getLista(PerfilRepository.GESTACAO);
        corpo.isPregnant = marcou(gestacao, "isPregnant");
        corpo.isBreastfeeding = marcou(gestacao, "isBreastfeeding");

        return corpo;
    }

    public static UserPreferenceRequest preferencias(long userId, Respostas respostas) {
        Set<String> escolhidas = slugs(respostas.getLista(PerfilRepository.PREFERENCIAS));

        UserPreferenceRequest corpo = new UserPreferenceRequest();
        corpo.userId = userId;
        corpo.preferVegan = escolhidas.contains(VEGANO);
        corpo.preferCrueltyFree = escolhidas.contains(CRUELTY_FREE);
        corpo.preferParabenFree = escolhidas.contains(SEM_PARABENOS);
        corpo.preferSulfateFree = escolhidas.contains(SEM_SULFATOS);
        corpo.preferSiliconeFree = escolhidas.contains(SEM_SILICONES);
        corpo.preferFragranceFree = false;
        corpo.preferSustainable = false;
        return corpo;
    }

    public static Set<Long> etiquetasEscolhidas(List<PreferenceCatalogResponse> catalogo,
                                                Respostas respostas) {
        Set<String> aceitos = new HashSet<>();
        for (String escolhida : slugs(respostas.getLista(PerfilRepository.PREFERENCIAS))) {
            List<String> nomes = PREFERENCIAS_EM_ETIQUETA.get(escolhida);
            if (nomes != null) {
                aceitos.addAll(nomes);
            }
        }

        Set<Long> ids = new HashSet<>();
        for (PreferenceCatalogResponse etiqueta : catalogo) {
            if (etiqueta.id != null && (contem(aceitos, etiqueta.slug)
                    || contem(aceitos, etiqueta.name))) {
                ids.add(etiqueta.id);
            }
        }
        return ids;
    }

    public static Set<Long> alergiasEscolhidas(List<AllergyResponse> catalogo,
                                               Respostas respostas) {
        Set<String> escolhidas = slugs(respostas.getLista(PerfilRepository.ALERGIAS));

        Set<Long> ids = new HashSet<>();
        for (AllergyResponse alergia : catalogo) {
            if (alergia.id != null && contem(escolhidas, alergia.allergyName)) {
                ids.add(alergia.id);
            }
        }
        return ids;
    }

    @Nullable
    public static PerfilDaApi daApi(@Nullable UserFullProfileResponse completo,
                                    List<String> opcoesDePreferencia) {
        UserProfileResponse perfil = completo == null ? null : completo.profile;
        if (perfil == null) {
            return null;
        }

        PerfilDaApi daApi = new PerfilDaApi();
        guardar(daApi, PerfilRepository.GENERO, valida(perfil.gender, GENEROS));
        guardar(daApi, PerfilRepository.TIPO_PELE, valida(perfil.skinType, TIPOS_PELE_NO_APP));
        guardar(daApi, PerfilRepository.FOTOTIPO, valida(perfil.skinPhototype, FOTOTIPOS));
        guardar(daApi, PerfilRepository.TIPO_CABELO,
                valida(cabeloNoApp(perfil.hairPattern), CABELOS_NO_APP));
        guardar(daApi, PerfilRepository.COURO_CABELUDO, valida(perfil.scalpType, COUROS));
        guardar(daApi, PerfilRepository.SENSIBILIDADE,
                valida(perfil.skinSensitivity, SENSIBILIDADES_NO_APP));
        guardar(daApi, PerfilRepository.FAIXA_ETARIA,
                valida(perfil.ageRange, FAIXAS_ETARIAS_NO_APP));

        guardar(daApi, PerfilRepository.CONDICOES_PELE, marcadas(
                "acneProne", perfil.acneProne,
                "hasRosacea", perfil.hasRosacea,
                "hasEczema", perfil.hasEczema,
                "hasHyperpigmentation", perfil.hasHyperpigmentation,
                "hasMelasma", perfil.hasMelasma));
        if (!PerfilRepository.MASCULINO.equals(daApi.tags.get(PerfilRepository.GENERO))) {
            guardar(daApi, PerfilRepository.GESTACAO, marcadas(
                    "isPregnant", perfil.isPregnant,
                    "isBreastfeeding", perfil.isBreastfeeding));
        }

        guardar(daApi, PerfilRepository.PREFERENCIAS,
                preferenciasDaApi(completo, opcoesDePreferencia, daApi.etiquetasNaApi));
        guardar(daApi, PerfilRepository.ALERGIAS,
                alergiasDaApi(completo.allergies, daApi.alergiasNaApi));
        return daApi;
    }

    private static void guardar(PerfilDaApi daApi, String chave, @Nullable String tag) {
        if (tag != null) {
            daApi.tags.put(chave, tag);
        }
    }

    private static void guardar(PerfilDaApi daApi, String chave, List<String> itens) {
        if (!itens.isEmpty()) {
            daApi.listas.put(chave, itens);
        }
    }

    private static List<String> marcadas(Object... paresTagValor) {
        List<String> marcadas = new ArrayList<>();
        boolean respondeu = false;
        for (int i = 0; i < paresTagValor.length; i += 2) {
            Boolean valor = (Boolean) paresTagValor[i + 1];
            if (valor != null) {
                respondeu = true;
                if (valor) {
                    marcadas.add((String) paresTagValor[i]);
                }
            }
        }
        if (respondeu && marcadas.isEmpty()) {
            marcadas.add(PerfilRepository.NENHUMA);
        }
        return marcadas;
    }

    private static List<String> preferenciasDaApi(UserFullProfileResponse completo,
                                                  List<String> opcoes,
                                                  Set<Long> etiquetasNaApi) {
        Set<String> escolhidas = new HashSet<>();
        UserPreferenceResponse preferencias = completo.preferences;
        if (preferencias != null) {
            adicionarSeMarcou(escolhidas, preferencias.preferVegan, VEGANO);
            adicionarSeMarcou(escolhidas, preferencias.preferCrueltyFree, CRUELTY_FREE);
            adicionarSeMarcou(escolhidas, preferencias.preferParabenFree, SEM_PARABENOS);
            adicionarSeMarcou(escolhidas, preferencias.preferSulfateFree, SEM_SULFATOS);
            adicionarSeMarcou(escolhidas, preferencias.preferSiliconeFree, SEM_SILICONES);
        }

        if (completo.tags != null) {
            for (PreferenceCatalogResponse etiqueta : completo.tags) {
                String preferencia = preferenciaDaEtiqueta(etiqueta);
                if (preferencia != null && etiqueta.id != null) {
                    escolhidas.add(preferencia);
                    etiquetasNaApi.add(etiqueta.id);
                }
            }
        }

        List<String> textos = new ArrayList<>();
        for (String opcao : opcoes) {
            if (escolhidas.contains(paraSlug(opcao))) {
                textos.add(opcao);
            }
        }
        return textos;
    }

    private static void adicionarSeMarcou(Set<String> slugs, @Nullable Boolean marcou,
                                          String slug) {
        if (Boolean.TRUE.equals(marcou)) {
            slugs.add(slug);
        }
    }

    @Nullable
    private static String preferenciaDaEtiqueta(PreferenceCatalogResponse etiqueta) {
        for (Map.Entry<String, List<String>> entrada : PREFERENCIAS_EM_ETIQUETA.entrySet()) {
            Set<String> nomes = new HashSet<>(entrada.getValue());
            if (contem(nomes, etiqueta.slug) || contem(nomes, etiqueta.name)) {
                return entrada.getKey();
            }
        }
        return null;
    }

    private static List<String> alergiasDaApi(@Nullable List<UserAllergyDetailResponse> salvas,
                                              Set<Long> alergiasNaApi) {
        List<String> nomes = new ArrayList<>();
        if (salvas == null) {
            return nomes;
        }
        for (UserAllergyDetailResponse salva : salvas) {
            AllergyResponse alergia = salva == null ? null : salva.allergy;
            if (alergia == null || alergia.id == null || alergia.allergyName == null) {
                continue;
            }
            alergiasNaApi.add(alergia.id);
            if (!nomes.contains(alergia.allergyName)) {
                nomes.add(alergia.allergyName);
            }
        }
        return nomes;
    }

    @Nullable
    private static String cabeloNoApp(@Nullable String tag) {
        return tag != null && tag.startsWith(CABELO_TIPO_1_APP) ? CABELO_TIPO_1_APP : tag;
    }

    @Nullable
    private static String cabeloNaApi(@Nullable String tag) {
        return CABELO_TIPO_1_APP.equals(tag) ? CABELO_TIPO_1_API : tag;
    }

    @Nullable
    private static String valida(@Nullable String tag, Set<String> aceitos) {
        return tag != null && aceitos.contains(tag) ? tag : null;
    }

    @Nullable
    private static Boolean marcou(List<String> respostas, String tag) {
        if (respostas.isEmpty() || respostas.contains(PerfilRepository.PREFIRO_NAO_DIZER)) {
            return null;
        }
        return respostas.contains(tag);
    }

    private static Set<String> slugs(List<String> textos) {
        Set<String> slugs = new HashSet<>();
        for (String texto : textos) {
            slugs.add(paraSlug(texto));
        }
        return slugs;
    }

    private static boolean contem(Set<String> slugs, @Nullable String texto) {
        return texto != null && slugs.contains(paraSlug(texto));
    }

    static String paraSlug(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
