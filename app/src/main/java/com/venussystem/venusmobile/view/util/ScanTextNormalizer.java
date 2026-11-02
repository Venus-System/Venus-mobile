package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.Locale;

public final class ScanTextNormalizer {

    private ScanTextNormalizer() {
    }

    @NonNull
    public static String normalize(
            @NonNull String text
    ) {

        String normalized = text
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');

        normalized = Normalizer.normalize(
                normalized,
                Normalizer.Form.NFD
        );

        normalized = normalized.replaceAll(
                "\\p{M}+",
                ""
        );

        normalized = normalized
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        return normalized;
    }
}