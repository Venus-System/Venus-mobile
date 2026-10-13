package com.venussystem.venusmobile.domain.match;

import java.text.Normalizer;
import java.util.Locale;

/** Mesma regra do paraSlug do MapeadorPerfilApi e do site: sem acento, minusculo, hifens. */
public final class Slug {

    private Slug() {
    }

    public static String de(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
