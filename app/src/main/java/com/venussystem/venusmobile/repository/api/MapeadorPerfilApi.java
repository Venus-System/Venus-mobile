package com.venussystem.venusmobile.repository.api;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.PreferenceCatalogResponse;
import com.venussystem.venusmobile.repository.api.dto.UserPreferenceRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileRequest;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Traduz as respostas do questionario, do jeito que o app guarda, para o que
 * o Venus-CRUD aceita: perfil, preferencias, etiquetas de preferencia e alergias.
 *
 * Quase todas as tags das telas ja sao os valores da API (ver PerfilRepository);
 * aqui ficam so as diferencas e as regras de "nao respondeu".
 */
public final class MapeadorPerfilApi {

    /** O que o mapeador precisa ler - o PerfilRepository ja tem os dois metodos. */
    public interface Respostas {
        @Nullable
        String getTag(String chave);

        List<String> getLista(String chave);
    }

    // Valores aceitos pela API (enums do UserProfileRequest em /v3/api-docs).
    // Uma tag fora daqui - de uma versao antiga do app, por exemplo - daria 400
    // em toda tentativa; melhor nao mandar do que insistir num envio que falha.
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

    // A API passou a ser so para adultos (migracao 20260914_003_adult_only do
    // Venus-Banco) e tirou UNDER_13 e AGE_13_17, mas a tela ainda oferece as
    // duas. Com elas o perfil fica so no aparelho.
    private static final Set<String> FAIXAS_ETARIAS =
            Set.of("AGE_18_24", "AGE_25_34", "AGE_35_44", "AGE_45_54", "AGE_55_PLUS");

    // A tela pergunta so "Tipo 1" (liso), mas a API divide em 1A, 1B e 1C.
    // 1A e o liso sem ondulacao nenhuma, o mais proximo do que a tela descreve.
    private static final String CABELO_TIPO_1_APP = "TYPE_1";
    private static final String CABELO_TIPO_1_API = "TYPE_1A";

    // As preferencias sao guardadas pelo texto da tela ("Sem parabenos"), entao
    // tudo aqui e comparado na forma de slug (ver paraSlug): um acento ou uma
    // maiuscula a mais no arrays.xml nao quebra o envio em silencio.
    private static final String VEGANO = "vegano";
    private static final String CRUELTY_FREE = "cruelty-free";
    private static final String SEM_PARABENOS = "sem-parabenos";
    private static final String SEM_SULFATOS = "sem-sulfatos";
    private static final String SEM_SILICONES = "sem-silicones";

    /**
     * As outras oito preferencias nao tem campo em /api/user-preferences e vao
     * como etiqueta do catalogo, achada pelo slug ou pelo nome. A lista de nomes
     * aceitos e a mesma do site (PREFERENCIAS_EM_ETIQUETA em perfil.ts), para os
     * dois lerem e gravarem as mesmas etiquetas.
     */
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

    /**
     * @return null quando ainda nao da para mandar: falta alguma resposta que a
     * API exige (quem escolheu "Responder depois"), ou a faixa etaria e de
     * menor de idade.
     */
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

    /**
     * As cinco preferencias que tem campo proprio na API. As outras vao como
     * etiqueta (ver etiquetasEscolhidas).
     *
     * A API exige os sete campos. "Sem fragrancia" saiu da tela e "sustentavel"
     * nunca esteve nela, entao os dois vao sempre false, como no site.
     */
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

    /**
     * Ids das etiquetas do catalogo (GET /api/profile-tags/preferences) que
     * correspondem as preferencias escolhidas. Preferencia sem etiqueta no
     * catalogo fica de fora: nao ha o que mandar ate o catalogo ter a etiqueta.
     */
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

    /**
     * Ids do catalogo (GET /api/allergies) das alergias escolhidas, comparando
     * pelo nome. A tela mostra os nomes do proprio catalogo, entao so fica de
     * fora quem foi escolhido numa lista antiga ou na lista reserva de quando a
     * API nao respondeu, e o catalogo ainda nao tem aquele nome.
     */
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
    private static String cabeloNaApi(@Nullable String tag) {
        return CABELO_TIPO_1_APP.equals(tag) ? CABELO_TIPO_1_API : tag;
    }

    @Nullable
    private static String valida(@Nullable String tag, Set<String> aceitos) {
        return tag != null && aceitos.contains(tag) ? tag : null;
    }

    /**
     * true/false quando a pessoa passou pela tela; null quando nao passou ou
     * preferiu nao dizer. Marcar "nenhuma" responde false para todas.
     */
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

    /**
     * "Oleo de Amendoas", com ou sem acento, vira "oleo-de-amendoas": sem
     * acento, minusculo e so letras e numeros separados por hifen. E a mesma
     * regra do paraSlug do site, e e o formato dos slugs do catalogo.
     */
    static String paraSlug(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
