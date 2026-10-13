package com.venussystem.venusmobile.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.venussystem.venusmobile.repository.api.dto.IngredientResponse;

import org.junit.Test;

public class IngredienteProdutoTest {

    private static IngredientResponse resposta(String inci, String comum, String descricao) {
        IngredientResponse r = new IngredientResponse();
        r.inciName = inci;
        r.commonName = comum;
        r.description = descricao;
        return r;
    }

    @Test
    public void usaNomeComumEGuardaInciComoSecundario() {
        IngredienteProduto i = IngredienteProduto.de(
                resposta("GLYCERIN", "GLICERINA", "Hidrata a pele."));

        assertEquals("GLICERINA", i.getNome());
        assertEquals("GLYCERIN", i.getNomeInci());
        assertEquals("Hidrata a pele.", i.getDescricao());
    }

    @Test
    public void semNomeComum_usaInciESemSecundario() {
        IngredienteProduto i = IngredienteProduto.de(resposta("PARFUM", "  ", "Perfuma."));

        assertEquals("PARFUM", i.getNome());
        assertNull(i.getNomeInci());
    }

    @Test
    public void descricaoGenericaDoCatalogoNaoViraDescricao() {
        IngredienteProduto i = IngredienteProduto.de(resposta("CERAMIDA NP", "",
                "Faz parte da fórmula, mas sua função cosmética específica ainda não está "
                        + "classificada no catálogo."));

        assertNull(i.getDescricao());
    }

    @Test
    public void semNomeNenhum_devolveNull() {
        assertNull(IngredienteProduto.de(resposta(null, "", "algo")));
        assertNull(IngredienteProduto.de(null));
    }
}
