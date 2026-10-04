package com.venussystem.venusmobile.view.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.IngredientSection;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CONTACT_CONTEXT_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMPTY;
import static com.venussystem.venusmobile.view.util.ScanBackRules.INGREDIENT_HEADINGS;
import static com.venussystem.venusmobile.view.util.ScanBackText.HeadingMatch;
import static com.venussystem.venusmobile.view.util.ScanBackText.compactarEspacos;
import static com.venussystem.venusmobile.view.util.ScanBackText.containsHeading;
import static com.venussystem.venusmobile.view.util.ScanBackText.contémTermoComFronteira;
import static com.venussystem.venusmobile.view.util.ScanBackText.ehNovoBloco;
import static com.venussystem.venusmobile.view.util.ScanBackText.encontrarHeading;
import static com.venussystem.venusmobile.view.util.ScanBackText.limitarTexto;
import static com.venussystem.venusmobile.view.util.ScanBackText.limparRestoHeading;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanBackText.pareceAdministrativoForte;
import static com.venussystem.venusmobile.view.util.ScanBackText.substringSafe;
import static com.venussystem.venusmobile.view.util.ScanBackText.temQualquerToken;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackLabelSections {
    private ScanBackLabelSections() { }

    private static final String[] USAGE_HEADINGS = {
            "MODO DE USO",
            "MODO DE USAR",
            "MODO D USO",
            "ODO DE USO",
            "MODO DE EMPLEO",
            "INSTRUCOES DE USO",
            "INSTRUCOES PARA USO",
            "COMO USAR",
            "COMO UTILIZAR",
            "HOW TO USE",
            "DIRECTIONS FOR USE",
            "DIRECTIONS",
            "UTILIZACAO",
            "UTILIZACION"
    };

    private static final String[] PRECAUTION_HEADINGS = {
            "PRECAUCOES",
            "PRECAUCAO",
            "PRECAUTIONS",
            "CAUTIONS",
            "CUIDADOS",
            "CUIDADO",
            "ADVERTENCIA",
            "ADVERTENCIAS",
            "WARNING",
            "WARNINGS",
            "AVISOS",
            "PRECAUTION"
    };

    /*
     * ================================================================
     * CLAIMS
     * ================================================================
     */
    private static final String[] CLAIM_TOKENS = {
            "VEGANO",
            "VEGAN",
            "VEGETARIANO",
            "VEGETARIAN",
            "CRUELTY FREE",
            "CRUELTY-FREE",
            "NAO TESTADO EM ANIMAIS",
            "NOT TESTED ON ANIMALS",
            "SEM PARABENOS",
            "PARABEN FREE",
            "SEM SILICONE",
            "SILICONE FREE",
            "SEM SULFATO",
            "SEM SULFATOS",
            "SULFATE FREE",
            "SEM FRAGRANCIA",
            "FRAGRANCE FREE",
            "SEM PERFUME",
            "SEM ALCOOL",
            "ALCOHOL FREE",
            "HIPOALERGENICO",
            "HYPOALLERGENIC",
            "DERMATOLOGICAMENTE TESTADO",
            "DERMATOLOGICAMENTE TESTADA",
            "DERMATOLOGICALLY TESTED",
            "OFTALMOLOGICAMENTE TESTADO",
            "CLINICAMENTE TESTADO",
            "CLINICALLY TESTED",
            "PH BALANCEADO",
            "PH BALANCED",
            "SEM GLUTEN",
            "GLUTEN FREE",
            "SEM OLEO MINERAL",
            "MINERAL OIL FREE",
            "SEM CORANTES",
            "DYE FREE",
            "SEM TALCO",
            "TALC FREE",
            "PROTECAO SOLAR",
            "RESISTENTE A AGUA",
            "WATER RESISTANT",
            "WATERPROOF",
            "PARA PELE SENSIVEL",
            "FOR SENSITIVE SKIN",
            "NATURAL",
            "NATURALE",
            "ORGANICO",
            "ORGANIC",
            "OIL FREE",
            "NAO COMEDOGENICO",
            "NON COMEDOGENIC",
            "TESTADO CLINICAMENTE"
    };

    /*
     * ================================================================
     * SEGURANÇA
     * ================================================================
     */
    private static final String[] SECURITY_TOKENS = {
            "NAO APLICAR",
            "NO APLICAR",
            "NO EXPONER",
            "PROTEGER",
            "PROTEJA",
            "EVITAR",
            "MANTER FORA",
            "FORA DO ALCANCE",
            "ALCANCE DE CRIANCAS",
            "ALCANCE DE LOS NINOS",
            "SE OCORRER",
            "SE OCORRER ALERGIA",
            "SE APARECER",
            "SINTOMAS DE IRRITACAO",
            "SINTOMAS DE IRRITACION",
            "IRRITACAO",
            "IRRITACION",
            "PRURIDO",
            "ALERGIA",
            "OLHOS",
            "OJOS",
            "FOGO",
            "LLAMA",
            "SUPERFICIES QUENTES",
            "SUPERFICIES CALIENTES",
            "CONTEUDO SOB PRESSAO",
            "CONTENIDO BAJO PRESION",
            "PODE EXPLODIR",
            "NO INCINERAR",
            "NAO INCINERAR",
            "PERIGO",
            "DANGER",
            "PELIGRO",
            "ATENCAO",
            "CAUTION",
            "WARNING"
    };

    private static final Pattern USAGE_INGREDIENT_START_PATTERN =
            Pattern.compile(
                    "(?i)(?:N?GREDIENTES(?:INGREDIENTS)?|"
                            + "INGREDIENTES(?:INGREDIENTS)?|"
                            + "INGREDIENTS(?:INGREDIENTES)?)"
            );

    /*
     * ================================================================
     * USO
     * ================================================================
     */
    static String extrairUso(
            List<String> lines,
            IngredientSection section
    ) {
        /*
         * Heading explícito.
         */
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            HeadingMatch heading =
                    encontrarHeading(
                            normalizar(
                                    lines.get(
                                            i
                                    )
                            ),
                            USAGE_HEADINGS,
                            true
                    );
            if (heading == null) {
                continue;
            }
            List<String> block =
                    new ArrayList<>();
            String remainder =
                    limparRestoHeading(
                            substringSafe(
                                    lines.get(
                                            i
                                    ),
                                    heading.endInOriginal
                            )
                    );
            if (
                    !remainder.isEmpty()
            ) {
                block.add(
                        remainder
                );
            }
            for (
                    int j = i + 1;
                    j < lines.size();
                    j++
            ) {
                String n =
                        normalizar(
                                lines.get(
                                        j
                                )
                        );
                if (
                        ehNovoBloco(
                                n,
                                INGREDIENT_HEADINGS
                        )
                                || ehNovoBloco(
                                n,
                                PRECAUTION_HEADINGS
                        )
                                || pareceAdministrativoForte(
                                n
                        )
                ) {
                    break;
                }
                block.add(
                        lines.get(
                                j
                        )
                );
            }
            String value =
                    limparBloco(
                            block,
                            SECURITY_TOKENS,
                            false
                    );
            value = limparUsoExtraido(value);
            if (!value.isEmpty()) {
                return value;
            }
        }
        /*
         * Fallback OCR.
         */
        List<String> fallback =
                new ArrayList<>();
        boolean collecting =
                false;
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );
            if (
                    section.startIndex >= 0
                            && i >= section.startIndex
            ) {
                break;
            }
            if (
                    !collecting
                            && (
                            contémTermoComFronteira(
                                    n,
                                    "AGITE ANTES"
                            )
                                    || contémTermoComFronteira(
                                    n,
                                    "AGITAR ANTES"
                            )
                                    || contémTermoComFronteira(
                                    n,
                                    "MODO DE USAR"
                            )
                                    || contémTermoComFronteira(
                                    n,
                                    "MODO DE USO"
                            )
                                    || contémTermoComFronteira(n, "APPLY")
                                    || contémTermoComFronteira(n, "SPRAY")
                                    || contémTermoComFronteira(n, "SHAKE")
                                    || contémTermoComFronteira(n, "USE")
                    )
            ) {
                collecting =
                        true;
                int marker =
                        n.indexOf(
                                "AGITE ANTES"
                        );
                if (marker < 0) {
                    marker =
                            n.indexOf(
                                    "AGITAR ANTES"
                            );
                }
                if (marker < 0) {
                    marker =
                            n.indexOf(
                                    "MODO DE USAR"
                            );
                }
                if (marker < 0) {
                    marker = n.indexOf("MODO DE USO");
                }
                if (marker < 0) marker = n.indexOf("APPLY");
                if (marker < 0) marker = n.indexOf("SPRAY");
                if (marker < 0) marker = n.indexOf("SHAKE");
                if (marker < 0) marker = n.indexOf("USE");
                String action =
                        marker >= 0
                                && marker
                                < lines.get(
                                i
                        ).length()
                                ? lines.get(
                                i
                        ).substring(
                                Math.min(
                                        marker,
                                        lines.get(
                                                i
                                        ).length()
                                )
                        ).trim()
                                : lines.get(
                                i
                        );
                fallback.add(
                        action
                );
                continue;
            }
            if (!collecting) {
                continue;
            }
            /*
             * Mudança de responsabilidade para segurança.
             */
            if (
                    n.contains(
                            "PROTEGER OS OLHOS"
                    )
                            || n.contains(
                            "PROTEGER LOS OJOS"
                    )
                            || n.contains(
                            "NAO APLICAR"
                    )
                            || n.contains(
                            "NO APLICAR"
                    )
                            || n.contains(
                            "NAO USAR"
                    )
                            || n.contains(
                            "NO USAR"
                    )
            ) {
                break;
            }
            if (
                    temQualquerToken(
                            n,
                            new String[]{
                                    "PULVERIZAR",
                                    "APLICAR",
                                    "USAR",
                                    "UTILIZAR",
                                    "APPLY",
                                    "SPRAY",
                                    "SHAKE",
                                    "USE"
                            }
                    )
            ) {
                fallback.add(
                        lines.get(
                                i
                        )
                );
            }
            if (fallback.size() >= 2) {
                break;
            }
        }
        return limparUsoExtraido(
                limparBloco(
                        fallback,
                        new String[0],
                        false
                )
        );
    }

    private static String limparUsoExtraido(String value) {
        if (value == null || value.trim().isEmpty()) {
            return EMPTY;
        }
        String result = value.trim();
        Matcher ingredientStart = USAGE_INGREDIENT_START_PATTERN.matcher(result);
        if (ingredientStart.find()) {
            result = result.substring(0, ingredientStart.start()).trim();
        }
        Matcher contact = CONTACT_CONTEXT_PATTERN.matcher(result);
        if (contact.find()) {
            result = result.substring(0, contact.start()).trim();
        }
        return compactarEspacos(result);
    }

    /*
     * ================================================================
     * SEGURANÇA
     * ================================================================
     */
    static String extrairSeguranca(
            List<String> lines,
            boolean warnings,
            IngredientSection section
    ) {
        List<String> explicit =
                new ArrayList<>();
        boolean collecting =
                false;
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );
            boolean heading =
                    containsHeading(
                            n,
                            warnings
                                    ? new String[]{
                                    "WARNING",
                                    "WARNINGS",
                                    "ADVERTENCIA",
                                    "ADVERTENCIAS",
                                    "AVISOS",
                                    "DANGER",
                                    "PELIGRO"
                            }
                                    : PRECAUTION_HEADINGS
                    );
            if (heading) {
                collecting =
                        true;
                String remainder =
                        limparRestoHeading(
                                lines.get(
                                        i
                                )
                        );
                if (
                        !remainder.isEmpty()
                ) {
                    explicit.add(
                            remainder
                    );
                }
                continue;
            }
            if (collecting) {
                if (
                        section.startIndex >= 0
                                && i >= section.startIndex
                ) {
                    break;
                }
                if (
                        pareceAdministrativoForte(
                                n
                        )
                ) {
                    break;
                }
                explicit.add(
                        lines.get(
                                i
                        )
                );
            }
        }
        String explicitValue =
                limparBloco(
                        explicit,
                        new String[0],
                        false
                );
        if (
                !explicitValue.isEmpty()
        ) {
            return limitarTexto(
                    explicitValue,
                    1400
            );
        }
        /*
         * Fallback por frases.
         */
        List<String> safety =
                new ArrayList<>();
        for (
                String line
                : lines
        ) {
            String n =
                    normalizar(
                            line
                    );
            if (
                    !temQualquerToken(
                            n,
                            SECURITY_TOKENS
                    )
            ) {
                continue;
            }
            safety.add(
                    line
            );
        }
        String result =
                limparBloco(
                        safety,
                        new String[0],
                        false
                );
        if (warnings) {
            List<String> selected =
                    new ArrayList<>();
            for (
                    String line
                    : safety
            ) {
                String n =
                        normalizar(
                                line
                        );
                if (
                        n.contains(
                                "PRESSAO"
                        )
                                || n.contains(
                                "PRESION"
                        )
                                || n.contains(
                                "EXPLO"
                        )
                                || n.contains(
                                "INCINER"
                        )
                                || n.contains(
                                "FOGO"
                        )
                                || n.contains(
                                "LLAMA"
                        )
                                || n.contains(
                                "OLHOS"
                        )
                                || n.contains(
                                "OJOS"
                        )
                                || n.contains(
                                "DANGER"
                        )
                                || n.contains(
                                "WARNING"
                        )
                                || n.contains(
                                "PELIGRO"
                        )
                ) {
                    selected.add(
                            line
                    );
                }
            }
            String selectedText =
                    limparBloco(
                            selected,
                            new String[0],
                            false
                    );
            return limitarTexto(
                    selectedText.isEmpty()
                            ? result
                            : selectedText,
                    1400
            );
        }
        return limitarTexto(
                result,
                1800
        );
    }

    /*
     * ================================================================
     * CLAIMS
     * ================================================================
     */
    static List<String> extrairClaims(
            List<String> lines
    ) {
        LinkedHashSet<String> claims =
                new LinkedHashSet<>();
        for (
                String line
                : lines
        ) {
            String n =
                    normalizar(
                            line
                    );
            for (
                    String token
                    : CLAIM_TOKENS
            ) {
                if (
                        contémTermoComFronteira(
                                n,
                                token
                        )
                ) {
                    claims.add(
                            limitarTexto(
                                    compactarEspacos(
                                            line.trim()
                                    ),
                                    180
                            )
                    );
                    break;
                }
            }
        }
        return new ArrayList<>(
                claims
        );
    }

    /*
     * ================================================================
     * OTHER TEXT
     * ================================================================
     */
    static String extrairOtherText(
            List<String> lines,
            IngredientSection section
    ) {
        List<String> other =
                new ArrayList<>();
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            if (
                    i == section.startIndex
                            || (
                            section.startIndex >= 0
                                    && i
                                    > section.startIndex
                                    && i
                                    <= section.endIndex
                    )
            ) {
                continue;
            }
            String line =
                    compactarEspacos(
                            lines.get(
                                    i
                            )
                    );
            if (line.isEmpty()) {
                continue;
            }
            if (
                    pareceAdministrativoForte(
                            normalizar(
                                    line
                            )
                    )
            ) {
                continue;
            }
            if (
                    containsAnyClaim(
                            normalizar(
                                    line
                            )
                    )
            ) {
                continue;
            }
            if (
                    ehTextoMuitoCurto(
                            line
                    )
            ) {
                continue;
            }
            other.add(
                    line
            );
        }
        return limitarTexto(
                String.join(
                        " | ",
                        other
                ),
                2200
        );
    }

    private static boolean containsAnyClaim(
            String line
    ) {
        return temQualquerToken(
                line,
                CLAIM_TOKENS
        );
    }

    private static String limparBloco(
            List<String> lines,
            String[] ignoredTokens,
            boolean onlyActionLines
    ) {
        if (
                lines == null
                        || lines.isEmpty()
        ) {
            return EMPTY;
        }
        List<String> valid =
                new ArrayList<>();
        for (
                String line
                : lines
        ) {
            if (line == null) {
                continue;
            }
            String n =
                    normalizar(
                            line
                    );
            if (n.isEmpty()) {
                continue;
            }
            if (
                    pareceAdministrativoForte(
                            n
                    )
            ) {
                continue;
            }
            if (
                    onlyActionLines
                            && !temQualquerToken(
                            n,
                            new String[]{
                                    "APLICAR",
                                    "PULVERIZAR",
                                    "AGITE",
                                    "AGITAR",
                                    "USAR",
                                    "UTILIZAR",
                                    "DIRECTIONS",
                                    "HOW TO"
                            }
                    )
            ) {
                continue;
            }
            valid.add(
                    compactarEspacos(
                            line
                    )
            );
        }
        return compactarEspacos(
                String.join(
                        " ",
                        valid
                )
        );
    }

    private static boolean ehTextoMuitoCurto(
            String line
    ) {
        return line == null
                || line.trim().length() < 3;
    }
}
