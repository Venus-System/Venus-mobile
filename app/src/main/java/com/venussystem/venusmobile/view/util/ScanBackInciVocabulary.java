package com.venussystem.venusmobile.view.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanBackRules.COMMON_INCI_BOUNDARIES;
import static com.venussystem.venusmobile.view.util.ScanBackText.contémTermoComFronteira;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizar;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackInciVocabulary {
    private ScanBackInciVocabulary() { }

    static int contarSinaisINCI(
            String line
    ) {
        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return 0;
        }
        String n =
                normalizar(
                        line
                );
        int count =
                0;
        /*
         * INCI já conhecidos pelo parser.
         */
        for (
                String known
                : COMMON_INCI_BOUNDARIES
        ) {
            if (
                    contémTermoComFronteira(
                            n,
                            known
                    )
            ) {
                count++;
            }
        }
        /*
         * Alguns padrões químicos comuns.
         */
        String[] chemicalPatterns = {
                "\\b[A-Z]{4,}-[A-Z0-9-]{2,}\\b",
                "\\b[A-Z]{6,}(?:OL|ONE|ATE|IDE|IUM|IC|YL)\\b",
                "\\bCI\\s*\\d{3,6}\\b"
        };
        for (
                String regex
                : chemicalPatterns
        ) {
            if (
                    Pattern.compile(
                            regex
                    ).matcher(
                            n
                    ).find()
            ) {
                count++;
            }
        }
        return Math.min(
                count,
                5
        );
    }

    static boolean inciCompleto(String value) {
        String n = normalizar(value);
        for (String known : COMMON_INCI_BOUNDARIES) {
            if (normalizar(known).equals(n)) return true;
        }
        return false;
    }

    static boolean prefixoInci(String value) {
        String n = normalizar(value);
        if (n.isEmpty()) return false;
        for (String known : COMMON_INCI_BOUNDARIES) {
            if (normalizar(known).startsWith(n + " ")) return true;
        }
        return false;
    }

    static boolean linhaComecaComInciConhecido(String value) {
        String n = normalizar(value);
        for (String known : inciOrdenadosPorComprimento()) {
            String k = normalizar(known);
            if (n.equals(k) || (n.startsWith(k) && n.length() > k.length()
                    && " ,;.:()".indexOf(n.charAt(k.length())) >= 0)) return true;
        }
        return false;
    }

    static List<String> inciOrdenadosPorComprimento() {
        List<String> values =
                new ArrayList<>(COMMON_INCI_BOUNDARIES);
        values.sort(
                (a, b) -> Integer.compare(
                        normalizar(b).length(),
                        normalizar(a).length()
                )
        );
        return values;
    }
}
