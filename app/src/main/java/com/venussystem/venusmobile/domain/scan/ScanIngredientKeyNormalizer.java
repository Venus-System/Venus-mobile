package com.venussystem.venusmobile.domain.scan;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Deterministic key used only for catalogue lookup. The OCR value itself is
 * never rewritten with this method.
 */
public final class ScanIngredientKeyNormalizer {
    private ScanIngredientKeyNormalizer() { }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = value
                .replace('\u00a0', ' ')
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201c', '"')
                .replace('\u201d', '"');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        normalized = normalized.replaceAll("^[,;|.]+|[,;|.]+$", "").trim();
        return normalized;
    }
}
