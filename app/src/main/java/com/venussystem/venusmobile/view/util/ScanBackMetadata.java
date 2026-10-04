package com.venussystem.venusmobile.view.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMPTY;
import static com.venussystem.venusmobile.view.util.ScanBackRules.REGISTRATION_MARKER_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackText.compactarEspacos;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizar;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackMetadata {
    private ScanBackMetadata() { }

    private static final Pattern REGISTRATION_VALUE_PATTERN =
            Pattern.compile(
                    "(?<![A-Z0-9])"
                            + "(?:"
                            + "2\\s*\\d{3,7}(?:\\s+\\d{1,3}(?:-[A-Z0-9]{1,8})?)?"
                            + "|2\\s*\\d{4,7}(?:-\\d{1,4})?"
                            + "|\\d{1,3}\\.\\d{3,7}(?:-\\d{1,4})?"
                            + ")"
                            + "(?![A-Z0-9])"
            );

    private static final Pattern NET_CONTENT_PATTERN =
            Pattern.compile(
                    "(?i)(?<![A-Z0-9])"
                            + "(?:CONTEUDO(?:\\s+LIQUIDO)?"
                            + "|PESO(?:\\s+LIQUIDO)?"
                            + "|VOLUME)?"
                            + "\\s*[:#.;-]?\\s*"
                            + "(\\d+(?:[.,]\\d+)?)\\s*"
                            + "(ML|MI|M1|L|CL|DL|UL|G|GR|KG|MG|OZ|FL\\s*OZ)\\b"
            );

    private static final Pattern GENERIC_SIZE_PATTERN =
            Pattern.compile(
                    "(?i)(?<![A-Z0-9])"
                            + "(\\d+(?:[.,]\\d+)?)\\s*"
                            + "(ML|MI|M1|L|CL|DL|UL|G|GR|KG|MG|OZ|FL\\s*OZ)\\b"
            );

    private static final Pattern BARCODE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)(\\d{8}|\\d{12}|\\d{13}|\\d{14})(?!\\d)"
            );

    /*
     * ================================================================
     * LOTE
     * ================================================================
     */
    static String extrairLote(
            List<String> lines
    ) {
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
            Matcher matcher =
                    Pattern.compile(
                                    "(?i)\\b(?:LOTE|LOT(?:\\s+CODE)?|BATCH)\\b"
                                            + "\\s*[:#]\\s*"
                                            + "([A-Z0-9][A-Z0-9./-]{1,20})\\b"
                            )
                            .matcher(n);
            if (!matcher.find()) {
                // Alguns rótulos colocam o código na linha seguinte ao heading.
                if (ehHeadingLoteIsolado(n) && i + 1 < lines.size()) {
                    String next = normalizar(lines.get(i + 1));
                    if (ehValorLoteValido(next)) {
                        return next;
                    }
                }
                continue;
            }
            String value =
                    matcher.group(
                            1
                    ).trim();
            if (
                    ehValorLoteValido(
                            value
                    )
            ) {
                return value;
            }
        }
        return EMPTY;
    }

    private static boolean ehValorLoteValido(
            String value
    ) {
        if (value == null || value.length() < 3 || value.length() > 20) {
            return false;
        }
        String n =
                normalizar(
                        value
                );
        if (n.startsWith("WWW")
                || n.startsWith("HTTP")
                || n.contains("EXPIR")
                || n.contains("VALID")
                || n.contains("VENC")
                || n.contains("DATE")
                || n.contains("MFG")
                || n.contains("MANUF")
                || n.equals("DOVE")
                || n.equals("DEL")
                || n.equals("ENVASE")
                || n.equals("FONDO")
                || n.equals("COM")
                || n.equals("AR")) {
            return false;
        }
        if (n.matches("^[0-9]{8,14}$")) {
            return false;
        }
        // Um lote exclusivamente alfabético é muito propenso a ser texto OCR.
        boolean hasLetter = n.matches(".*[A-Z].*");
        boolean hasDigit = n.matches(".*\\d.*");
        return (hasLetter && hasDigit)
                || n.matches("^[0-9]{3,7}$");
    }

    private static boolean ehHeadingLoteIsolado(String value) {
        return "LOTE".equals(value)
                || "LOT".equals(value)
                || "LOT CODE".equals(value)
                || "BATCH".equals(value);
    }

    /*
     * ================================================================
     * REGISTRO
     * ================================================================
     */
    static String extrairRegistro(
            List<String> lines
    ) {
        LinkedHashSet<String> values =
                new LinkedHashSet<>();
        for (
                String line
                : lines
        ) {
            String n =
                    normalizar(
                            line
                    );
            if (
                    !REGISTRATION_MARKER_PATTERN
                            .matcher(
                                    n
                            )
                            .find()
            ) {
                continue;
            }
            Matcher matcher =
                    REGISTRATION_VALUE_PATTERN
                            .matcher(
                                    n
                            );
            while (
                    matcher.find()
            ) {
                String value =
                        compactarEspacos(
                                matcher.group()
                        ).trim();
                if (!value.isEmpty()) {
                    values.add(
                            value
                    );
                }
            }
        }
        /*
         * Marcador em uma linha e valor na próxima.
         */
        if (values.isEmpty()) {
            for (
                    int i = 0;
                    i < lines.size();
                    i++
            ) {
                if (
                        !REGISTRATION_MARKER_PATTERN
                                .matcher(
                                        normalizar(
                                                lines.get(
                                                        i
                                                )
                                        )
                                )
                                .find()
                ) {
                    continue;
                }
                if (
                        i + 1
                                < lines.size()
                ) {
                    Matcher matcher =
                            REGISTRATION_VALUE_PATTERN
                                    .matcher(
                                            normalizar(
                                                    lines.get(
                                                            i + 1
                                                    )
                                            )
                                    );
                    if (matcher.find()) {
                        values.add(
                                matcher.group()
                                        .trim()
                        );
                    }
                }
            }
        }
        return String.join(
                " | ",
                values
        );
    }

    /*
     * ================================================================
     * CONTEÚDO
     * ================================================================
     */
    static String extrairConteudo(
            List<String> lines
    ) {
        /*
         * 1) Contextual.
         */
        for (
                String line
                : lines
        ) {
            String n =
                    normalizar(
                            line
                    );
            if (
                    !(
                            n.contains(
                                    "CONTEUDO"
                            )
                                    || n.contains(
                                    "PESO LIQUIDO"
                            )
                                    || n.contains(
                                    "VOLUME"
                            )
                    )
            ) {
                continue;
            }
            Matcher m =
                    NET_CONTENT_PATTERN.matcher(
                            n
                    );
            while (m.find()) {
                if (conteudoPlausivel(m.group(1))) {
                    return formatarConteudo(m.group(1), m.group(2));
                }
            }
        }
        /*
         * 2) Tamanho solto, somente quando parece conteúdo.
         */
        for (
                String line
                : lines
        ) {
            String n =
                    normalizar(
                            line
                    );
            if (
                    temContextoDePressaoOuAdministrativo(
                            n
                    )
            ) {
                continue;
            }
            Matcher m =
                    GENERIC_SIZE_PATTERN.matcher(
                            n
                    );
            while (m.find()) {
                if (linhaPareceDeConteudo(n) && conteudoPlausivel(m.group(1))) {
                    return formatarConteudo(m.group(1), m.group(2));
                }
            }
        }
        return EMPTY;
    }

    private static boolean conteudoPlausivel(String value) {
        // Long numeric package codes are not a retail cosmetic quantity.
        return value.matches("\\d{1,5}(?:[.,]\\d{1,3})?")
                && Double.parseDouble(value.replace(',', '.')) > 0;
    }

    private static boolean linhaPareceDeConteudo(
            String line
    ) {
        return line.length() <= 55
                || line.contains(
                "CAP"
        )
                || line.contains(
                "CONTE"
        )
                || line.contains(
                "ML"
        );
    }

    private static boolean temContextoDePressaoOuAdministrativo(
            String n
    ) {
        return n.contains(
                "PRESSAO"
        )
                || n.contains(
                "PRESION"
        )
                || n.contains(
                "CNPJ"
        )
                || n.contains(
                "AUTFUNC"
        )
                || n.contains(
                "REGISTRO"
        )
                || n.contains(
                "CEP"
        );
    }

    private static String formatarConteudo(
            String value,
            String unit
    ) {
        String u =
                normalizar(
                        unit
                );
        if (
                "MI".equals(
                        u
                )
                        || "M1".equals(
                        u
                )
        ) {
            u =
                    "ML";
        }
        if (
                "GR".equals(
                        u
                )
        ) {
            u =
                    "G";
        }
        return value.replace(
                ',',
                '.'
        )
                + " "
                + u;
    }

    /*
     * ================================================================
     * BARCODE
     * ================================================================
     */
    static String extrairBarcode(
            List<String> lines
    ) {
        for (
                String line
                : lines
        ) {
            String n =
                    normalizar(
                            line
                    );
            Matcher matcher =
                    BARCODE_PATTERN.matcher(
                            n
                    );
            while (
                    matcher.find()
            ) {
                String candidate =
                        matcher.group(
                                1
                        );
                if (
                        validarChecksumEAN(
                                candidate
                        )
                ) {
                    return candidate;
                }
            }
        }
        return EMPTY;
    }

    private static boolean validarChecksumEAN(
            String code
    ) {
        if (code == null) {
            return false;
        }
        String digits =
                code.replaceAll(
                        "\\D",
                        EMPTY
                );
        if (
                !(
                        digits.length() == 8
                                || digits.length() == 12
                                || digits.length() == 13
                                || digits.length() == 14
                )
        ) {
            return false;
        }
        int sum =
                0;
        int positionFromRight =
                0;
        for (
                int i = digits.length() - 2;
                i >= 0;
                i--,
                        positionFromRight++
        ) {
            int d =
                    Character.digit(
                            digits.charAt(
                                    i
                            ),
                            10
                    );
            sum +=
                    (
                            positionFromRight % 2
                                    == 0
                    )
                            ? d * 3
                            : d;
        }
        int check =
                (10 - (
                        sum % 10
                )) % 10;
        return check
                == Character.digit(
                digits.charAt(
                        digits.length() - 1
                ),
                10
        );
    }
}
