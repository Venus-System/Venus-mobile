package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.PreferenceCatalogResponse;
import com.venussystem.venusmobile.repository.api.dto.UserPreferenceRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileRequest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapeadorPerfilApiTest {

    private static final long USER_ID = 42L;

    /** Respostas em memoria, no mesmo formato do PerfilRepository. */
    private static class RespostasFalsas implements MapeadorPerfilApi.Respostas {
        final Map<String, String> tags = new HashMap<>();
        final Map<String, List<String>> listas = new HashMap<>();

        @Override
        public String getTag(String chave) {
            return tags.get(chave);
        }

        @Override
        public List<String> getLista(String chave) {
            List<String> lista = listas.get(chave);
            return lista == null ? new ArrayList<>() : new ArrayList<>(lista);
        }
    }

    private RespostasFalsas respostas;

    @Before
    public void setUp() {
        respostas = new RespostasFalsas();
        respostas.tags.put(PerfilRepository.GENERO, "FEMALE");
        respostas.tags.put(PerfilRepository.TIPO_CABELO, "TYPE_3B");
        respostas.tags.put(PerfilRepository.COURO_CABELUDO, "OILY");
        respostas.tags.put(PerfilRepository.TIPO_PELE, "COMBINATION");
        respostas.tags.put(PerfilRepository.SENSIBILIDADE, "MEDIUM");
        respostas.tags.put(PerfilRepository.FOTOTIPO, "IV");
        respostas.tags.put(PerfilRepository.FAIXA_ETARIA, "AGE_25_34");
    }

    // ---- Perfil ----

    @Test
    public void questionarioCompleto_viraOCorpoDaApi() {
        UserProfileRequest corpo = MapeadorPerfilApi.perfil(USER_ID, respostas);

        assertNotNull(corpo);
        assertEquals(Long.valueOf(USER_ID), corpo.userId);
        assertEquals("FEMALE", corpo.gender);
        assertEquals("TYPE_3B", corpo.hairPattern);
        assertEquals("OILY", corpo.scalpType);
        assertEquals("COMBINATION", corpo.skinType);
        assertEquals("MEDIUM", corpo.skinSensitivity);
        assertEquals("IV", corpo.skinPhototype);
        assertEquals("AGE_25_34", corpo.ageRange);
    }

    @Test
    public void cabeloTipo1DaTela_vira1ANaApi() {
        respostas.tags.put(PerfilRepository.TIPO_CABELO, "TYPE_1");

        UserProfileRequest corpo = MapeadorPerfilApi.perfil(USER_ID, respostas);

        assertNotNull(corpo);
        assertEquals("TYPE_1A", corpo.hairPattern);
    }

    @Test
    public void faixaDeMenorDeIdade_naoEnviaOPerfil() {
        respostas.tags.put(PerfilRepository.FAIXA_ETARIA, "UNDER_13");
        assertNull(MapeadorPerfilApi.perfil(USER_ID, respostas));

        respostas.tags.put(PerfilRepository.FAIXA_ETARIA, "AGE_13_17");
        assertNull(MapeadorPerfilApi.perfil(USER_ID, respostas));
    }

    @Test
    public void faltandoRespostaObrigatoria_naoEnvia() {
        // Quem escolheu "Responder depois" nao tem nenhuma; aqui falta so uma.
        respostas.tags.remove(PerfilRepository.FOTOTIPO);

        assertNull(MapeadorPerfilApi.perfil(USER_ID, respostas));
    }

    @Test
    public void tagQueAApiNaoConhece_naoEnvia() {
        respostas.tags.put(PerfilRepository.TIPO_PELE, "OLEOSA");

        assertNull(MapeadorPerfilApi.perfil(USER_ID, respostas));
    }

    @Test
    public void condicoesMarcadas_viramTrueEAsOutrasFalse() {
        respostas.listas.put(PerfilRepository.CONDICOES_PELE, Arrays.asList("acneProne", "hasMelasma"));

        UserProfileRequest corpo = MapeadorPerfilApi.perfil(USER_ID, respostas);

        assertNotNull(corpo);
        assertTrue(corpo.acneProne);
        assertTrue(corpo.hasMelasma);
        assertFalse(corpo.hasRosacea);
        assertFalse(corpo.hasEczema);
        assertFalse(corpo.hasHyperpigmentation);
    }

    @Test
    public void nenhumaCondicao_respondeFalseParaTodas() {
        respostas.listas.put(PerfilRepository.CONDICOES_PELE,
                Arrays.asList(PerfilRepository.NENHUMA));

        UserProfileRequest corpo = MapeadorPerfilApi.perfil(USER_ID, respostas);

        assertNotNull(corpo);
        assertFalse(corpo.acneProne);
        assertFalse(corpo.hasRosacea);
        assertFalse(corpo.hasEczema);
        assertFalse(corpo.hasHyperpigmentation);
        assertFalse(corpo.hasMelasma);
    }

    @Test
    public void telaNaoRespondida_deixaAsCondicoesComoNaoInformadas() {
        UserProfileRequest corpo = MapeadorPerfilApi.perfil(USER_ID, respostas);

        assertNotNull(corpo);
        assertNull(corpo.acneProne);
        assertNull(corpo.hasMelasma);
        assertNull(corpo.isPregnant);
        assertNull(corpo.isBreastfeeding);
    }

    @Test
    public void gestacao_prefiroNaoDizerFicaNaoInformado() {
        respostas.listas.put(PerfilRepository.GESTACAO,
                Arrays.asList(PerfilRepository.PREFIRO_NAO_DIZER));

        UserProfileRequest corpo = MapeadorPerfilApi.perfil(USER_ID, respostas);

        assertNotNull(corpo);
        assertNull(corpo.isPregnant);
        assertNull(corpo.isBreastfeeding);
    }

    @Test
    public void gestacao_amamentando() {
        respostas.listas.put(PerfilRepository.GESTACAO, Arrays.asList("isBreastfeeding"));

        UserProfileRequest corpo = MapeadorPerfilApi.perfil(USER_ID, respostas);

        assertNotNull(corpo);
        assertFalse(corpo.isPregnant);
        assertTrue(corpo.isBreastfeeding);
    }

    // ---- Preferencias ----

    @Test
    public void preferenciasComCampoNaApi_viramTrue() {
        respostas.listas.put(PerfilRepository.PREFERENCIAS, Arrays.asList(
                "Vegano", "Cruelty free", "Sem parabenos", "Sem sulfatos", "Sem silicones"));

        UserPreferenceRequest corpo = MapeadorPerfilApi.preferencias(USER_ID, respostas);

        assertEquals(Long.valueOf(USER_ID), corpo.userId);
        assertTrue(corpo.preferVegan);
        assertTrue(corpo.preferCrueltyFree);
        assertTrue(corpo.preferParabenFree);
        assertTrue(corpo.preferSulfateFree);
        assertTrue(corpo.preferSiliconeFree);
    }

    @Test
    public void fragranciaESustentavel_vaoSempreFalse() {
        // "Sem fragrancia" saiu da tela, mas pode estar salvo de uma versao
        // antiga; "Embalagem reciclavel" agora vai como etiqueta.
        respostas.listas.put(PerfilRepository.PREFERENCIAS,
                Arrays.asList("Sem fragrância", "Embalagem reciclável"));

        UserPreferenceRequest corpo = MapeadorPerfilApi.preferencias(USER_ID, respostas);

        assertFalse(corpo.preferFragranceFree);
        assertFalse(corpo.preferSustainable);
    }

    @Test
    public void preferenciasSemCampoNaApi_naoMexemNasColunas() {
        respostas.listas.put(PerfilRepository.PREFERENCIAS,
                Arrays.asList("Sem álcool", "Orgânico", "Vegano"));

        UserPreferenceRequest corpo = MapeadorPerfilApi.preferencias(USER_ID, respostas);

        assertTrue(corpo.preferVegan);
        assertFalse(corpo.preferCrueltyFree);
        assertFalse(corpo.preferFragranceFree);
        assertFalse(corpo.preferSustainable);
    }

    @Test
    public void semPreferencias_mandaTudoFalse() {
        // A API exige os sete campos, entao "nada escolhido" vai como false.
        UserPreferenceRequest corpo = MapeadorPerfilApi.preferencias(USER_ID, respostas);

        assertFalse(corpo.preferVegan);
        assertFalse(corpo.preferCrueltyFree);
        assertFalse(corpo.preferSustainable);
        assertFalse(corpo.preferFragranceFree);
        assertFalse(corpo.preferParabenFree);
        assertFalse(corpo.preferSulfateFree);
        assertFalse(corpo.preferSiliconeFree);
    }

    @Test
    public void preferencias_ignoramAcentoECaixa() {
        respostas.listas.put(PerfilRepository.PREFERENCIAS, Arrays.asList("SEM PARABÉNOS "));

        assertTrue(MapeadorPerfilApi.preferencias(USER_ID, respostas).preferParabenFree);
    }

    // ---- Etiquetas de preferencia ----

    private static PreferenceCatalogResponse etiqueta(long id, String slug, String nome) {
        PreferenceCatalogResponse etiqueta = new PreferenceCatalogResponse();
        etiqueta.id = id;
        etiqueta.slug = slug;
        etiqueta.name = nome;
        return etiqueta;
    }

    // Recorte do catalogo de verdade (GET /api/profile-tags/preferences).
    private static final List<PreferenceCatalogResponse> CATALOGO_ETIQUETAS = Arrays.asList(
            etiqueta(28, "vegano", "Vegano"),
            etiqueta(31, "sem-alcool", "Sem Álcool"),
            etiqueta(36, "organico", "Orgânico"),
            etiqueta(37, "ingredientes-naturais", "Ingredientes Naturais"),
            etiqueta(38, "testado-dermatologicamente", "Testado Dermatologicamente"),
            etiqueta(100, "recyclable-packaging", "Recyclable Packaging"));

    @Test
    public void etiquetas_achadasPeloNomeOuSlug() {
        respostas.listas.put(PerfilRepository.PREFERENCIAS,
                Arrays.asList("Sem álcool", "Orgânico", "Testado dermatologicamente"));

        assertEquals(new HashSet<>(Arrays.asList(31L, 36L, 38L)),
                MapeadorPerfilApi.etiquetasEscolhidas(CATALOGO_ETIQUETAS, respostas));
    }

    @Test
    public void etiquetas_naturalViraIngredientesNaturais() {
        respostas.listas.put(PerfilRepository.PREFERENCIAS, Arrays.asList("Natural"));

        assertEquals(Collections.singleton(37L),
                MapeadorPerfilApi.etiquetasEscolhidas(CATALOGO_ETIQUETAS, respostas));
    }

    @Test
    public void etiquetas_preferenciaComColunaNaoViraEtiqueta() {
        // Vegano tem etiqueta no catalogo, mas vai pelo preferVegan.
        respostas.listas.put(PerfilRepository.PREFERENCIAS, Arrays.asList("Vegano"));

        assertTrue(MapeadorPerfilApi.etiquetasEscolhidas(CATALOGO_ETIQUETAS, respostas).isEmpty());
    }

    @Test
    public void etiquetas_semCorrespondenteNoCatalogo_ficamDeFora() {
        // "Recyclable Packaging" nao conta como "Embalagem reciclavel": o site
        // tambem nao reconhece, e os dois precisam gravar as mesmas etiquetas.
        respostas.listas.put(PerfilRepository.PREFERENCIAS,
                Arrays.asList("Hipoalergênico", "Embalagem reciclável"));

        assertTrue(MapeadorPerfilApi.etiquetasEscolhidas(CATALOGO_ETIQUETAS, respostas).isEmpty());
    }

    // ---- Alergias ----

    private static AllergyResponse alergia(long id, String nome) {
        AllergyResponse alergia = new AllergyResponse();
        alergia.id = id;
        alergia.allergyName = nome;
        return alergia;
    }

    @Test
    public void alergias_achadasPeloNomeSemAcentoNemCaixa() {
        List<AllergyResponse> catalogo = Arrays.asList(
                alergia(9, "Látex"), alergia(12, "Óleo de Amêndoas"), alergia(4, "Salicylic Acid"));
        respostas.listas.put(PerfilRepository.ALERGIAS,
                Arrays.asList("Látex", "Óleo de amêndoas", "Níquel"));

        assertEquals(new HashSet<>(Arrays.asList(9L, 12L)),
                MapeadorPerfilApi.alergiasEscolhidas(catalogo, respostas));
    }

    @Test
    public void semAlergias_nenhumId() {
        assertTrue(MapeadorPerfilApi.alergiasEscolhidas(
                Collections.singletonList(alergia(9, "Látex")), respostas).isEmpty());
    }

    @Test
    public void paraSlug_igualAoDoSite() {
        assertEquals("oleo-de-amendoas", MapeadorPerfilApi.paraSlug("Óleo de Amêndoas"));
        assertEquals("sem-alcool", MapeadorPerfilApi.paraSlug("  Sem  álcool! "));
        assertEquals("nao-comedogenico", MapeadorPerfilApi.paraSlug("Não comedogênico"));
    }
}
