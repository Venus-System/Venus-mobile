package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ScanTextAnalyzer {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(".*@.*\\..*");

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    ".*(WWW\\.|HTTP://|HTTPS://).*"
            );

    private static final Pattern PHONE_PATTERN =
            Pattern.compile(
                    ".*\\b\\d{4,5}[- ]?\\d{4}\\b.*"
            );

    private static final Pattern VOLUME_PATTERN =
            Pattern.compile(
                    ".*\\b\\d+(?:[.,]\\d+)?\\s?(ML|L|G|KG|MG|OZ)\\b.*"
            );

    private static final Pattern PERCENT_PATTERN =
            Pattern.compile(
                    ".*\\b\\d+(?:[.,]\\d+)?%.*"
            );

    private ScanTextAnalyzer() {
    }

    @NonNull
    public static String normalize(
            @NonNull String line
    ) {
        return ScanTextNormalizer.normalize(line);
    }

    public static boolean isIngredientHeader(
            @NonNull String line
    ) {

        String text = normalize(line);

        return text.contains("INGREDIENTES")
                || text.contains("INGREDIENTS")
                || text.contains("COMPOSICAO")
                || text.contains("COMPOSITION");
    }

    public static boolean isWarning(
            @NonNull String line
    ) {

        String text = normalize(line);

        return text.contains("PRECAUCOES")
                || text.contains("MANTER FORA")
                || text.contains("USO EXTERNO")
                || text.contains("NAO USAR")
                || text.contains("EVITAR")
                || text.contains("SOMENTE NAS AREAS");
    }

    public static boolean isContact(
            @NonNull String line
    ) {

        String text = normalize(line);

        return EMAIL_PATTERN.matcher(text).matches()
                || URL_PATTERN.matcher(text).matches()
                || PHONE_PATTERN.matcher(text).matches();
    }

    public static boolean isVolume(
            @NonNull String line
    ) {

        return VOLUME_PATTERN.matcher(
                normalize(line)
        ).matches();
    }

    public static boolean isPercentage(
            @NonNull String line
    ) {

        return PERCENT_PATTERN.matcher(
                normalize(line)
        ).matches();
    }

    public static boolean isMarketing(
            @NonNull String line
    ) {

        String text = normalize(line);

        return text.contains("NOVA TECNOLOGIA")
                || text.contains("PROTECAO")
                || text.contains("PERFUMACAO")
                || text.contains("RESULTADO COMPROVADO")
                || text.contains("NAO MANCHA")
                || text.contains("NAO DEIXA RESIDUOS");
    }

    @NonNull
    public static List<String> cleanLines(
            @NonNull List<String> lines
    ) {

        List<String> result = new ArrayList<>();

        for (String line : lines) {

            if (line == null) {
                continue;
            }

            String normalized = normalize(line);

            if (normalized.length() < 2) {
                continue;
            }

            result.add(normalized);
        }

        return result;
    }
}