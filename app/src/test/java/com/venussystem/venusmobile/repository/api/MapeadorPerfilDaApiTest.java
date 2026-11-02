package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.PreferenceCatalogResponse;
import com.venussystem.venusmobile.repository.api.dto.UserAllergyDetailResponse;
import com.venussystem.venusmobile.repository.api.dto.UserFullProfileResponse;
import com.venussystem.venusmobile.repository.api.dto.UserPreferenceResponse;
import com.venussystem.venusmobile.repository.api.dto.UserProfileRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileResponse;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapeadorPerfilDaApiTest {

    private static final List<String> OPCOES = Arrays.asList(
            "Vegano", "Cruelty free", "Sem parabenos", "Sem sulfatos", "Sem silicones",
            "Sem álcool", "Sem óleo", "Natural", "Orgânico", "Hipoalergênico",
            "Não comedogênico", "Testado dermatologicamente", "Embalagem reciclável");

    private UserFullProfileResponse completo;
    private UserProfileResponse perfil;

    @Before
    public void setUp() {
        perfil = new UserProfileResponse();
        perfil.gender = "FEMALE";
        perfil.skinType = "COMBINATION";
        perfil.skinPhototype = "IV";
        perfil.hairPattern = "TYPE_3B";
        perfil.scalpType = "OILY";
        perfil.skinSensitivity = "MEDIUM";
        perfil.ageRange = "AGE_25_34";

        completo = new UserFullProfileResponse();
        completo.profile = perfil;
    }

    @Test
    public void semPerfil_naoHaOQueRestaurar() {
        completo.profile = null;

        assertNull(MapeadorPerfilApi.daApi(completo, OPCOES));
        assertNull(MapeadorPerfilApi.daApi(null, OPCOES));
    }

    @Test
    public void respostasDeEscolhaUnica_voltamComAsTagsDoApp() {
        PerfilDaApi daApi = MapeadorPerfilApi.daApi(completo, OPCOES);

        assertEquals("FEMALE", daApi.tags.get(PerfilRepository.GENERO));
        assertEquals("COMBINATION", daApi.tags.get(PerfilRepository.TIPO_PELE));
        assertEquals("IV", daApi.tags.get(PerfilRepository.FOTOTIPO));
        assertEquals("TYPE_3B", daApi.tags.get(PerfilRepository.TIPO_CABELO));
        assertEquals("OILY", daApi.tags.get(PerfilRepository.COURO_CABELUDO));
        assertEquals("MEDIUM", daApi.tags.get(PerfilRepository.SENSIBILIDADE));
        assertEquals("AGE_25_34", daApi.tags.get(PerfilRepository.FAIXA_ETARIA));
    }

    @Test
    public void cabeloTipo1DaApi_viraOTipo1UnicoDoApp() {
        for (String tipo1 : Arrays.asList("TYPE_1A", "TYPE_1B", "TYPE_1C")) {
            perfil.hairPattern = tipo1;
            assertEquals("TYPE_1",
                    MapeadorPerfilApi.daApi(completo, OPCOES).tags.get(PerfilRepository.TIPO_CABELO));
        }
    }

    @Test
    public void valoresQueOAppNaoOferece_ficamPendentes() {
        perfil.skinType = "ACNEIC";
        perfil.skinSensitivity = "VERY_HIGH";
        perfil.gender = "QUALQUER";

        PerfilDaApi daApi = MapeadorPerfilApi.daApi(completo, OPCOES);

        assertFalse(daApi.tags.containsKey(PerfilRepository.TIPO_PELE));
        assertFalse(daApi.tags.containsKey(PerfilRepository.SENSIBILIDADE));
        assertFalse(daApi.tags.containsKey(PerfilRepository.GENERO));
        assertEquals("IV", daApi.tags.get(PerfilRepository.FOTOTIPO));
    }

    @Test
    public void condicoesMarcadas_viramListaDeTags() {
        perfil.acneProne = true;
        perfil.hasRosacea = false;
        perfil.hasMelasma = true;

        assertEquals(Arrays.asList("acneProne", "hasMelasma"),
                MapeadorPerfilApi.daApi(completo, OPCOES).listas.get(PerfilRepository.CONDICOES_PELE));
    }

    @Test
    public void condicoesTodasFalsas_viramNenhuma() {
        perfil.acneProne = false;
        perfil.hasRosacea = false;
        perfil.hasEczema = false;
        perfil.hasHyperpigmentation = false;
        perfil.hasMelasma = false;

        assertEquals(Collections.singletonList(PerfilRepository.NENHUMA),
                MapeadorPerfilApi.daApi(completo, OPCOES).listas.get(PerfilRepository.CONDICOES_PELE));
    }

    @Test
    public void condicoesSemResposta_ficamPendentes() {
        assertFalse(MapeadorPerfilApi.daApi(completo, OPCOES)
                .listas.containsKey(PerfilRepository.CONDICOES_PELE));
    }

    @Test
    public void gestacao_voltaParaQuemNaoEMasculino() {
        perfil.isPregnant = false;
        perfil.isBreastfeeding = true;

        assertEquals(Collections.singletonList("isBreastfeeding"),
                MapeadorPerfilApi.daApi(completo, OPCOES).listas.get(PerfilRepository.GESTACAO));
    }

    @Test
    public void gestacao_ficaDeForaParaMasculino() {
        perfil.gender = PerfilRepository.MASCULINO;
        perfil.isPregnant = false;
        perfil.isBreastfeeding = false;

        assertFalse(MapeadorPerfilApi.daApi(completo, OPCOES)
                .listas.containsKey(PerfilRepository.GESTACAO));
    }

    @Test
    public void preferencias_juntamOsCamposEAsEtiquetasNoTextoDoApp() {
        UserPreferenceResponse preferencias = new UserPreferenceResponse();
        preferencias.preferVegan = true;
        preferencias.preferSulfateFree = true;
        preferencias.preferParabenFree = false;
        completo.preferences = preferencias;
        completo.tags = Arrays.asList(
                etiqueta(37L, "ingredientes-naturais", "Ingredientes Naturais"),
                etiqueta(40L, "embalagem-reciclavel", "Embalagem Reciclável"),
                etiqueta(51L, "pele-oleosa", "Pele oleosa"));

        PerfilDaApi daApi = MapeadorPerfilApi.daApi(completo, OPCOES);

        assertEquals(Arrays.asList("Vegano", "Sem sulfatos", "Natural", "Embalagem reciclável"),
                daApi.listas.get(PerfilRepository.PREFERENCIAS));
        assertEquals(new HashSet<>(Arrays.asList(37L, 40L)), daApi.etiquetasNaApi);
    }

    @Test
    public void alergias_voltamPeloNomeEGuardamOsIds() {
        completo.allergies = Arrays.asList(alergia(9L, "Látex"), alergia(12L, "Soja"),
                new UserAllergyDetailResponse());

        PerfilDaApi daApi = MapeadorPerfilApi.daApi(completo, OPCOES);

        assertEquals(Arrays.asList("Látex", "Soja"), daApi.listas.get(PerfilRepository.ALERGIAS));
        assertEquals(new HashSet<>(Arrays.asList(9L, 12L)), daApi.alergiasNaApi);
    }

    @Test
    public void restauradoEReenviado_mandaOMesmoPerfil() {
        perfil.acneProne = true;
        perfil.hasRosacea = false;
        perfil.hasEczema = false;
        perfil.hasHyperpigmentation = false;
        perfil.hasMelasma = false;
        perfil.isPregnant = true;
        perfil.isBreastfeeding = false;

        PerfilDaApi daApi = MapeadorPerfilApi.daApi(completo, OPCOES);
        MapeadorPerfilApi.Respostas respostas = new MapeadorPerfilApi.Respostas() {
            @Override
            public String getTag(String chave) {
                return daApi.tags.get(chave);
            }

            @Override
            public List<String> getLista(String chave) {
                List<String> lista = daApi.listas.get(chave);
                return lista == null ? new ArrayList<>() : lista;
            }
        };

        UserProfileRequest reenvio = MapeadorPerfilApi.perfil(42L, respostas);

        assertEquals(perfil.gender, reenvio.gender);
        assertEquals(perfil.skinType, reenvio.skinType);
        assertEquals(perfil.hairPattern, reenvio.hairPattern);
        assertEquals(perfil.ageRange, reenvio.ageRange);
        assertTrue(reenvio.acneProne);
        assertFalse(reenvio.hasRosacea);
        assertTrue(reenvio.isPregnant);
        assertFalse(reenvio.isBreastfeeding);
    }

    private static PreferenceCatalogResponse etiqueta(long id, String slug, String nome) {
        PreferenceCatalogResponse etiqueta = new PreferenceCatalogResponse();
        etiqueta.id = id;
        etiqueta.slug = slug;
        etiqueta.name = nome;
        return etiqueta;
    }

    private static UserAllergyDetailResponse alergia(long id, String nome) {
        AllergyResponse alergia = new AllergyResponse();
        alergia.id = id;
        alergia.allergyName = nome;
        UserAllergyDetailResponse detalhe = new UserAllergyDetailResponse();
        detalhe.allergy = alergia;
        return detalhe;
    }
}
