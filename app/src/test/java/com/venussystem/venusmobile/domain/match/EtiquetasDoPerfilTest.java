package com.venussystem.venusmobile.domain.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.repository.api.MapeadorPerfilApi;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EtiquetasDoPerfilTest {

    private static MapeadorPerfilApi.Respostas respostas(String chave, String tag) {
        Map<String, String> tags = new HashMap<>();
        tags.put(chave, tag);
        return new MapeadorPerfilApi.Respostas() {
            @Override
            public String getTag(String c) {
                return tags.get(c);
            }

            @Override
            public List<String> getLista(String c) {
                return Collections.emptyList();
            }
        };
    }

    @Test
    public void cabeloCacheadoEntraComoControleDeFrizz() {
        assertTrue(EtiquetasDoPerfil.slugs(respostas(PerfilRepository.TIPO_CABELO, "TYPE_3A"))
                .contains("controle-de-frizz"));
    }

    @Test
    public void cabeloLisoNaoEntraComoControleDeFrizz() {
        assertFalse(EtiquetasDoPerfil.slugs(respostas(PerfilRepository.TIPO_CABELO, "TYPE_1"))
                .contains("controle-de-frizz"));
    }

    @Test
    public void alergiasIgnoramNenhumaEPrefiroNaoDizer() {
        MapeadorPerfilApi.Respostas comAlergias = new MapeadorPerfilApi.Respostas() {
            @Override
            public String getTag(String c) {
                return null;
            }

            @Override
            public List<String> getLista(String c) {
                return PerfilRepository.ALERGIAS.equals(c)
                        ? Arrays.asList("Lanolina", PerfilRepository.NENHUMA,
                        PerfilRepository.PREFIRO_NAO_DIZER)
                        : Collections.emptyList();
            }
        };

        assertEquals(Collections.singletonList("Lanolina"),
                EtiquetasDoPerfil.alergiasEmTexto(comAlergias));
        assertEquals(Collections.singleton("lanolina"), EtiquetasDoPerfil.alergias(comAlergias));
    }

    @Test
    public void controleDeFrizzTemRotuloLegivel() {
        assertTrue(EtiquetasDoPerfil.rotulos(Collections.singleton("controle-de-frizz"),
                Collections.emptySet()).contains("cabelo ondulado, cacheado ou crespo"));
    }
}
