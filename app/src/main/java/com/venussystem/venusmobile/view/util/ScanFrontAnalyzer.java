package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class ScanFrontAnalyzer {

    private ScanFrontAnalyzer() {
    }

    @NonNull
    public static List<String> extractCandidates(
            @NonNull List<String> lines
    ) {

        List<String> candidates = new ArrayList<>();

        for (String line : lines) {

            String text =
                    ScanTextAnalyzer.normalize(line);

            if (text.length() < 3) {
                continue;
            }

            if (ScanTextAnalyzer.isIngredientHeader(text)) {
                continue;
            }

            if (ScanTextAnalyzer.isWarning(text)) {
                continue;
            }

            if (ScanTextAnalyzer.isContact(text)) {
                continue;
            }

            if (ScanTextAnalyzer.isVolume(text)) {
                continue;
            }

            if (ScanTextAnalyzer.isPercentage(text)) {
                continue;
            }

            if (ScanTextAnalyzer.isMarketing(text)) {
                continue;
            }

            candidates.add(text);
        }

        return candidates;
    }
}