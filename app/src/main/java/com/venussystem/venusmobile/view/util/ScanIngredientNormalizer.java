package com.venussystem.venusmobile.view.util;

import com.venussystem.venusmobile.domain.scan.ScanIngredientPolicy;

/** Correct one candidate only after segmentation. Never rewrite raw OCR. */
final class ScanIngredientNormalizer {
    private ScanIngredientNormalizer() { }

    static Correction correct(String raw) {
        String normalized = ScanIngredientPolicy.correct(raw);
        return new Correction(normalized, !normalized.equals(ScanIngredientPolicy.normalize(raw)));
    }

    static final class Correction {
        final String normalized;
        final boolean corrected;
        Correction(String normalized, boolean corrected) {
            this.normalized = normalized;
            this.corrected = corrected;
        }
    }
}
