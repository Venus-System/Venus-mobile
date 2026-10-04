package com.venussystem.venusmobile.view.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CEP_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CNPJ_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.CONTACT_CONTEXT_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMAIL_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMPTY;
import static com.venussystem.venusmobile.view.util.ScanBackRules.INLINE_COMPANY_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.PHONE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.STREET_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackRules.URL_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanBackText.compactarEspacos;
import static com.venussystem.venusmobile.view.util.ScanBackText.contarLetras;
import static com.venussystem.venusmobile.view.util.ScanBackText.contémTermoComFronteira;
import static com.venussystem.venusmobile.view.util.ScanBackText.indexOfMarkerWithBoundary;
import static com.venussystem.venusmobile.view.util.ScanBackText.localizarIndiceAproximadoOriginal;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizarOCRBasico;
import static com.venussystem.venusmobile.view.util.ScanBackText.safeSubstring;
import static com.venussystem.venusmobile.view.util.ScanBackText.temContextoAdministrativo;
import static com.venussystem.venusmobile.view.util.ScanBackText.temRuidoAdministrativo;
import static com.venussystem.venusmobile.view.util.ScanBackText.temRuidoAdministrativoExcessivo;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackCompany {
    private ScanBackCompany() { }

    /*
     * ================================================================
     * EMPRESA / FABRICANTE
     * ================================================================
     */
    private static final String[] MANUFACTURER_MARKERS = {
            "FABRICADO POR",
            "FABRICADO",
            "FABRICANTE",
            "FABRICANT",
            "MANUFACTURER",
            "MANUFACTURED BY",
            "MANUFACTURED",
            "MADE BY",
            "PRODUZIDO POR",
            "PRODUZIDO",
            "MANUFACTURADO POR",
            "ELABORADO POR",
            "ELABORADO",
            "DISTRIBUTED BY"
    };

    private static final String[] IMPORTER_MARKERS = {
            "IMPORTADO POR",
            "IMPORTADOR",
            "IMPORTADO",
            "IMPORTED BY",
            "IMPORTED",
            "IMPORTADORA"
    };

    private static final String[] DISTRIBUTOR_MARKERS = {
            "DISTRIBUIDO POR",
            "DISTRIBUIDORA",
            "DISTRIBUTOR",
            "DISTRIBUTED BY"
    };

    /*
     * ================================================================
     * EMPRESAS
     * ================================================================
     */
    private static final String[] COMPANY_SUFFIXES = {
            "LTDA",
            "S A",
            "S/A",
            "SA",
            "SAS",
            "SRL",
            "S DE RL",
            "S DE R L",
            "LLC",
            "INC",
            "CORP",
            "CORPORATION",
            "PLC",
            "BV",
            "NV",
            "GMBH",
            "AG",
            "KG",
            "SPA",
            "S P A",
            "EIRELI",
            "ME",
            "LTD"
    };

    private static final String[] CORPORATE_MARKERS = {
            "UNILEVER",
            "LOREAL",
            "L OREAL",
            "PROCTER",
            "COLGATE",
            "PALMOLIVE",
            "NIVEA",
            "BEIERSDORF",
            "JOHNSON",
            "RECKITT",
            "HENKEL",
            "KENVUE",
            "LVMH",
            "ESTEE LAUDER",
            "THE BODY SHOP",
            "NATURA",
            "BOTICARIO",
            "GRANADO",
            "CETAPHIL",
            "CERAVE",
            "EUCERIN",
            "DOVE",
            "REVLON",
            "MAYBELLINE",
            "RIMMEL",
            "COTY",
            "SHISEIDO"
    };

    /*
     * ================================================================
     * PAÍSES
     * ================================================================
     */
    private static final String[] COUNTRY_NAMES = {
            "BRASIL",
            "BRAZIL",
            "ARGENTINA",
            "CHILE",
            "URUGUAI",
            "URUGUAY",
            "PARAGUAI",
            "PARAGUAY",
            "BOLIVIA",
            "PERU",
            "COLOMBIA",
            "EQUADOR",
            "ECUADOR",
            "MEXICO",
            "ESTADOS UNIDOS",
            "UNITED STATES",
            "USA",
            "CANADA",
            "FRANCA",
            "FRANCE",
            "ITALIA",
            "ITALY",
            "ALEMANHA",
            "GERMANY",
            "ESPANHA",
            "SPAIN",
            "PORTUGAL",
            "REINO UNIDO",
            "UNITED KINGDOM",
            "INDIA",
            "CHINA",
            "JAPAO",
            "JAPAN",
            "AUSTRALIA",
            "POLONIA",
            "POLAND",
            "TURQUIA",
            "TURKEY",
            "SUICA",
            "SWITZERLAND",
            "PAISES BAIXOS",
            "NETHERLANDS"
    };

    private static final String[] BRAZIL_ORIGIN_MARKERS = {
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA BRASILEIRO",
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA BRASILEÑO",
            "FABRICADO NO BRASIL",
            "FABRICADO NO BRAZIL",
            "MADE IN BRAZIL",
            "MADE IN BRASIL",
            "PAIS DE ORIGEM BRASIL",
            "PAIS DE ORIGEM: BRASIL",
            "ORIGEM BRASIL",
            "ORIGEM: BRASIL",
            "NDUSTRIA BRASILEIRA",
            "INDUSTRA BRASILEIRA",
            "INDUSTRIA 8RASILENA",
            "INDUSTRA 8RASILENA",
            "BRASILEIRAINDUSTRIA"
    };

    /**
     * V7.1: preserva o texto que sobra depois de um heading de contato.
     * O OCR pode reconhecer o e-mail de forma incompleta, portanto este
     * padrão não exige que o endereço tenha um domínio válido.
     */
    private static final Pattern CONTACT_RESIDUAL_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:QUESTIONS?|CONTACT|CUSTOMER\\s+SERVICE|"
                            + "ATENDIMENTO(?:\\s+AO\\s+CONSUMIDOR)?|"
                            + "MAIL\\s+SUPPORT|MAIL\\s+SUPOR|MAL\\s+SOPPORT|"
                            + "SOPPORT|SUPPORI|SUPPOR|E[- ]?MAIL)\\b"
                            + "\\s*[:;,.\\-]*\\s*(.+)"
            );

    private static final Pattern SAC_PHONE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "(?:0800|0300)\\s*\\d{3,4}[ .-]?\\d{4}"
                            + "(?!\\d)"
            );

    /*
     * ================================================================
     * FABRICANTE / IMPORTADOR / ENDEREÇO
     * ================================================================
     */
    static String extrairFabricante(
            List<String> lines
    ) {
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            String line =
                    lines.get(i);
            CompanyMarker marker =
                    encontrarMarcadorEmpresa(
                            line
                    );
            if (marker == null) {
                continue;
            }
            String remainder =
                    escolherEmpresaDepoisDoMarcador(
                            marker.remainder,
                            lines,
                            i
                    );
            if (!remainder.isEmpty()) {
                return remainder;
            }
            /*
             * Só aceita texto anterior ao marcador quando
             * já parece empresa.
             */
            if (
                    !marker.prefix.isEmpty()
                            && ehEmpresaClara(
                            marker.prefix
                    )
            ) {
                return limparEmpresa(
                        marker.prefix
                );
            }
            /*
             * Nome da empresa pode estar na linha seguinte.
             */
            for (
                    int j = i + 1;
                    j <= Math.min(
                            lines.size() - 1,
                            i + 2
                    );
                    j++
            ) {
                String candidate =
                        limparEmpresa(
                                lines.get(j)
                        );
                if (
                        ehEmpresaClara(
                                candidate
                        )
                                && !pareceImportacao(
                                lines,
                                j
                        )
                ) {
                    return candidate;
                }
            }
        }
        /*
         * Caso de OCR em que o nome da empresa aparece colado ao final
         * da composição e não existe um marcador explícito como
         * "FABRICADO POR":
         *
         *   ... PEEL OIL cial Farmacêutica Ltda. Av. Rui Barbosa
         *
         * A expressão empresarial já usada como barreira do extractor
         * permite recuperar somente o bloco da empresa.
         */
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            Matcher inlineCompany =
                    INLINE_COMPANY_PATTERN.matcher(line);
            if (inlineCompany.find()) {
                String candidate =
                        limparEmpresa(inlineCompany.group());
                if (ehEmpresaClara(candidate)) {
                    return candidate;
                }
            }
        }
        /*
         * Fallback conservador:
         * empresa conhecida + sufixo empresarial.
         */
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            String line =
                    compactarEspacos(
                            normalizarOCRBasico(
                                    lines.get(i)
                            )
                    );
            if (line.isEmpty()) {
                continue;
            }
            if (
                    pareceImportacao(
                            lines,
                            i
                    )
                            || pareceDistribuicao(
                            lines,
                            i
                    )
            ) {
                continue;
            }
            if (
                    temContextoAdministrativo(
                            line
                    )
            ) {
                continue;
            }
            if (
                    contemMarcadorCorporativo(
                            line
                    )
                            && temSufixoEmpresarial(
                            line
                    )
            ) {
                String candidate =
                        limparEmpresa(
                                line
                        );
                if (
                        ehEmpresaClara(
                                candidate
                        )
                ) {
                    return candidate;
                }
            }
        }
        return EMPTY;
    }

    private static String escolherEmpresaDepoisDoMarcador(
            String remainder,
            List<String> lines,
            int index
    ) {
        if (remainder == null) {
            remainder =
                    EMPTY;
        }
        String candidate =
                limparEmpresa(
                        remainder
                );
        if (
                ehEmpresaClara(
                        candidate
                )
        ) {
            return cortarTrechoAdministrativo(
                    candidate
            );
        }
        if (
                index + 1
                        < lines.size()
        ) {
            String next =
                    limparEmpresa(
                            lines.get(
                                    index + 1
                            )
                    );
            if (
                    ehEmpresaClara(
                            next
                    )
                            && !pareceImportacao(
                            lines,
                            index + 1
                    )
            ) {
                return cortarTrechoAdministrativo(
                        next
                );
            }
        }
        return EMPTY;
    }

    private static CompanyMarker encontrarMarcadorEmpresa(
            String line
    ) {
        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return null;
        }
        String normalized =
                normalizar(
                        line
                );
        int best =
                Integer.MAX_VALUE;
        String bestMarker =
                null;
        String bestMarkerNormalized =
                null;
        for (
                String marker
                : MANUFACTURER_MARKERS
        ) {
            String m =
                    normalizar(
                            marker
                    );
            int idx =
                    indexOfMarkerWithBoundary(
                            normalized,
                            m
                    );
            if (
                    idx >= 0
                            && idx < best
            ) {
                best =
                        idx;
                bestMarker =
                        marker;
                bestMarkerNormalized =
                        m;
            }
        }
        if (bestMarker == null) {
            return null;
        }
        Matcher originalMarker = Pattern.compile(
                "(?i)(?<![A-Z0-9])"
                        + Pattern.quote(bestMarker.trim())
                        + "(?![A-Z0-9])"
        ).matcher(line);
        if (originalMarker.find()) {
            String prefix = safeSubstring(line, 0, originalMarker.start()).trim();
            String remainder = safeSubstring(line, originalMarker.end(), line.length()).trim();
            return new CompanyMarker(prefix, remainder, bestMarker, bestMarkerNormalized);
        }
        int originalIndex = localizarIndiceAproximadoOriginal(line, bestMarker);
        if (originalIndex < 0) originalIndex = best;
        String prefix = safeSubstring(line, 0, originalIndex).trim();
        String remainder = safeSubstring(line, originalIndex + bestMarker.length(), line.length()).trim();
        return new CompanyMarker(prefix, remainder, bestMarker, bestMarkerNormalized);
    }

    private static boolean ehEmpresaClara(
            String value
    ) {
        if (value == null) {
            return false;
        }
        String s =
                compactarEspacos(
                        normalizarOCRBasico(
                                value
                        )
                );
        if (
                s.length() < 5
                        || s.length() > 150
        ) {
            return false;
        }
        /*
         * Sufixos empresariais fazem parte da própria identidade da
         * empresa e, portanto, não podem ser tratados como "lixo
         * administrativo" durante a validação do candidato.
         */
        String adminCheck =
                s;
        for (String suffix : COMPANY_SUFFIXES) {
            adminCheck =
                    adminCheck.replaceAll(
                            "(?i)\\b"
                                    + Pattern.quote(
                                    normalizar(suffix)
                            )
                                    + "\\b",
                            " "
                    );
        }
        if (
                temRuidoAdministrativo(
                        adminCheck
                )
        ) {
            return false;
        }
        if (
                CNPJ_PATTERN
                        .matcher(
                                s
                        )
                        .find()
                        || CEP_PATTERN
                        .matcher(
                                s
                        )
                        .find()
        ) {
            return false;
        }
        if (
                SAC_PHONE_PATTERN
                        .matcher(
                                s
                        )
                        .find()
        ) {
            return false;
        }
        if (
                URL_PATTERN
                        .matcher(
                                s
                        )
                        .find()
        ) {
            return false;
        }
        boolean corporate =
                temSufixoEmpresarial(
                        s
                )
                        || contemMarcadorCorporativo(
                        s
                );
        if (!corporate) {
            return false;
        }
        int letters =
                contarLetras(
                        s
                );
        return letters >= 6;
    }

    private static String limparEmpresa(
            String value
    ) {
        if (value == null) {
            return EMPTY;
        }
        String s =
                compactarEspacos(
                        normalizarOCRBasico(
                                value
                        )
                );
        if (s.isEmpty()) {
            return EMPTY;
        }
        s =
                cortarTrechoAdministrativo(
                        s
                );
        s =
                s.replaceFirst(
                        "^[,:;.|\\- ]+",
                        EMPTY
                );
        return compactarEspacos(
                s
        );
    }

    private static String cortarTrechoAdministrativo(
            String s
    ) {
        String upper =
                normalizar(
                        s
                );
        int cut =
                Integer.MAX_VALUE;
        String[] stops = {
                " CNPJ",
                " CEP",
                " SAC",
                " AUTFUNC",
                " AUT FUNC",
                " REGISTRO",
                " LOTE",
                " CONTEUDO",
                " IMPORTADO POR",
                " IMPORTADO",
                " DISTRIBUIDO POR",
                " DISTRIBUIDORA",
                " INDUSTRIA BRASILEIRA",
                " PAIS DE ORIGEM",
                " WWW.",
                " HTTP",
                " AVENIDA",
                " RUA",
                " CALLE",
                " CARRERA",
                " PISO"
        };
        for (
                String stop
                : stops
        ) {
            int idx =
                    upper.indexOf(
                            normalizar(
                                    stop
                            )
                    );
            if (
                    idx > 0
                            && idx < cut
            ) {
                cut =
                        idx;
            }
        }
        if (
                cut
                        != Integer.MAX_VALUE
        ) {
            s =
                    s.substring(
                            0,
                            Math.min(
                                    cut,
                                    s.length()
                            )
                    ).trim();
        }
        return s;
    }

    static String extrairEndereco(
            List<String> lines,
            String manufacturer
    ) {
        Set<String> candidates =
                new LinkedHashSet<>();
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {
            String line =
                    compactarEspacos(
                            normalizarOCRBasico(
                                    lines.get(i)
                            )
                    );
            if (line.isEmpty()) {
                continue;
            }
            if (
                    CNPJ_PATTERN
                            .matcher(
                                    line
                            )
                            .find()
                            && !temTextoDeEndereco(
                            line
                    )
            ) {
                continue;
            }
            if (
                    STREET_PATTERN
                            .matcher(
                                    line
                            )
                            .find()
                            || pareceEnderecoInternacional(
                            line
                    )
            ) {
                if (
                        !temRuidoAdministrativoExcessivo(
                                line
                        )
                ) {
                    candidates.add(
                            limparEndereco(
                                    line
                            )
                    );
                }
            }
        }
        if (!candidates.isEmpty()) {
            for (
                    String candidate
                    : candidates
            ) {
                if (
                        CEP_PATTERN
                                .matcher(
                                        candidate
                                )
                                .find()
                                || STREET_PATTERN
                                .matcher(
                                        candidate
                                )
                                .find()
                ) {
                    return candidate;
                }
            }
            return candidates.iterator().next();
        }
        return EMPTY;
    }

    private static boolean temTextoDeEndereco(
            String line
    ) {
        return STREET_PATTERN
                .matcher(
                        line
                )
                .find()
                || line.matches(
                ".*\\b\\d{1,6}\\b.*"
        )
                || line.contains(
                "PISO"
        )
                || line.contains(
                "VITACURA"
        )
                || line.contains(
                "CEP"
        );
    }

    private static boolean pareceEnderecoInternacional(
            String line
    ) {
        if (line == null) {
            return false;
        }
        String n =
                normalizar(
                        line
                );
        return (
                n.contains(
                        "VITACURA"
                )
                        || n.contains(
                        "MUNRO"
                )
                        || n.contains(
                        "TEPALCAPA"
                )
                        || n.contains(
                        "TULTITLAN"
                )
                        || n.contains(
                        "ESTADO DE MEXICO"
                )
        )
                && line.matches(
                ".*\\d{1,6}.*"
        );
    }

    private static String limparEndereco(
            String line
    ) {
        String s =
                compactarEspacos(
                        normalizarOCRBasico(
                                line
                        )
                );
        s =
                CNPJ_PATTERN
                        .matcher(
                                s
                        )
                        .replaceAll(
                                EMPTY
                        )
                        .trim();
        /*
         * Quando empresa + endereço aparecem na mesma linha, não podemos
         * guardar o prefixo da composição como se fosse parte do endereço.
         * O endereço começa no primeiro marcador de via.
         */
        Matcher streetStart =
                STREET_PATTERN.matcher(s);
        if (streetStart.find() && streetStart.start() > 0) {
            s = s.substring(streetStart.start()).trim();
        }
        s =
                cortarTrechoAdministrativoEndereco(
                        s
                );
        return compactarEspacos(
                s
        );
    }

    private static String cortarTrechoAdministrativoEndereco(
            String s
    ) {
        String upper =
                normalizar(
                        s
                );
        int cut =
                Integer.MAX_VALUE;
        String[] stops = {
                " CNPJ",
                " SAC",
                " AUTFUNC",
                " AUT FUNC",
                " IMPORTADO POR",
                " DISTRIBUIDO POR"
        };
        for (
                String stop
                : stops
        ) {
            int idx =
                    upper.indexOf(
                            normalizar(
                                    stop
                            )
                    );
            if (
                    idx > 0
                            && idx < cut
            ) {
                cut =
                        idx;
            }
        }
        if (
                cut
                        != Integer.MAX_VALUE
        ) {
            s =
                    s.substring(
                            0,
                            Math.min(
                                    cut,
                                    s.length()
                            )
                    ).trim();
        }
        return s;
    }

    /*
     * ================================================================
     * CONTATO
     * ================================================================
     */
    static String extrairContato(
            List<String> lines
    ) {
        LinkedHashSet<String> contacts =
                new LinkedHashSet<>();
        for (
                String line
                : lines
        ) {
            String original =
                    line == null
                            ? EMPTY
                            : line;
            Matcher sac =
                    SAC_PHONE_PATTERN.matcher(
                            original
                    );
            while (sac.find()) {
                contacts.add(
                        normalizarTelefone(
                                sac.group()
                        )
                );
            }
            Matcher phone =
                    PHONE_PATTERN.matcher(
                            original
                    );
            while (phone.find()) {
                String number =
                        normalizarTelefone(
                                phone.group()
                        );
                if (
                        !number.isEmpty()
                                && temEvidenciaDeTelefone(original, phone.group())
                                && !pareceNumeroAdministrativo(
                                number
                        )
                ) {
                    contacts.add(
                            number
                    );
                }
            }
            Matcher email =
                    EMAIL_PATTERN.matcher(
                            original
                    );
            while (email.find()) {
                contacts.add(
                        email.group().trim()
                );
            }
            Matcher contactContext =
                    CONTACT_CONTEXT_PATTERN.matcher(original);
            while (contactContext.find()) {
                String candidate = compactarEspacos(contactContext.group());
                if (!candidate.isEmpty()) contacts.add(candidate);
            }
            Matcher residual = CONTACT_RESIDUAL_PATTERN.matcher(original);
            while (residual.find()) {
                String candidate = compactarEspacos(residual.group(1));
                if (pareceContatoResidual(candidate)) {
                    contacts.add(candidate);
                }
            }
        }
        return String.join(
                " | ",
                contacts
        );
    }

    private static boolean temEvidenciaDeTelefone(String line, String number) {
        return Pattern.compile("\\b(?:SAC|TEL|TELEFONE|TELEPHONE|PHONE|FONE|CALL|CONTATO)\\b")
                .matcher(normalizar(line)).find()
                || number.trim().startsWith("+")
                || number.contains("(")
                || number.matches(".*\\d{4,5}-\\d{4}.*");
    }

    private static boolean pareceContatoResidual(String value) {
        if (value == null || value.trim().length() < 3) {
            return false;
        }
        String normalized = normalizar(value);
        return normalized.contains("@")
                || normalized.contains("CALL")
                || normalized.contains("PHONE")
                || normalized.contains("FONE")
                || normalized.contains("SUPPORT")
                || normalized.contains("SOPPORT")
                || normalized.contains("SUPPOR")
                || PHONE_PATTERN.matcher(value).find()
                || EMAIL_PATTERN.matcher(value).find();
    }

    private static boolean pareceNumeroAdministrativo(
            String value
    ) {
        String digits =
                value.replaceAll(
                        "\\D",
                        EMPTY
                );
        if (
                digits.length() >= 10
                        && digits.length() <= 14
        ) {
            return digits.matches(
                    "0{6,}"
            )
                    || digits.startsWith(
                    "01.615"
            )
                    || digits.startsWith(
                    "5511"
            );
        }
        return false;
    }

    private static String normalizarTelefone(
            String value
    ) {
        return compactarEspacos(
                value
        )
                .replaceAll(
                        "[()]",
                        EMPTY
                )
                .trim();
    }

    /*
     * ================================================================
     * PAÍS
     * ================================================================
     */
    static String extrairPais(
            List<String> lines,
            String manufacturer
    ) {
        /*
         * 1) Evidência explícita de origem.
         */
        for (
                String line
                : lines
        ) {
            String n =
                    normalizar(
                            line
                    );
            for (
                    String marker
                    : BRAZIL_ORIGIN_MARKERS
            ) {
                if (
                        contémTermoComFronteira(
                                n,
                                marker
                        )
                                || n.contains(
                                normalizar(
                                        marker
                                )
                        )
                ) {
                    return "BRASIL";
                }
                if (
                        normalizar(
                                marker
                        )
                                .contains(
                                        "INDUSTRIA"
                                )
                                && (
                                n.contains(
                                        "NDUSTRIA BRASILEIRA"
                                )
                                        || n.contains(
                                        "INDUSTRA BRASILEIRA"
                                )
                                        || n.contains(
                                        "INDUSTRIA 8RASILENA"
                                )
                                        || n.contains(
                                        "INDUSTRA 8RASILENA"
                                )
                        )
                ) {
                    return "BRASIL";
                }
            }
            String explicit =
                    extrairPaisDeExpressaoExplicita(
                            n
                    );
            if (
                    !explicit.isEmpty()
            ) {
                return explicit;
            }
        }
        /*
         * 2) Fabricante explicitamente brasileiro.
         */
        if (
                manufacturer != null
                        && !manufacturer.isEmpty()
        ) {
            String m =
                    normalizar(
                            manufacturer
                    );
            if (
                    m.contains(
                            "BRASIL"
                    )
                            || m.contains(
                            "BRAZIL"
                    )
            ) {
                return "BRASIL";
            }
        }
        /*
         * 3) País em linha isolada.
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
                    n.isEmpty()
                            || n.length() > 60
            ) {
                continue;
            }
            String country =
                    matchCountryWholeLine(
                            n
                    );
            if (
                    !country.isEmpty()
            ) {
                return country;
            }
        }
        return EMPTY;
    }

    private static String extrairPaisDeExpressaoExplicita(
            String line
    ) {
        if (
                line.contains(
                        "MADE IN "
                )
        ) {
            String tail =
                    line.substring(
                            line.indexOf(
                                    "MADE IN "
                            )
                                    + 8
                    ).trim();
            return normalizarNomePais(
                    tail
            );
        }
        if (
                line.contains(
                        "FABRICADO NO "
                )
        ) {
            String tail =
                    line.substring(
                            line.indexOf(
                                    "FABRICADO NO "
                            )
                                    + 13
                    ).trim();
            return normalizarNomePais(
                    tail
            );
        }
        if (
                line.contains(
                        "PAIS DE ORIGEM"
                )
        ) {
            String tail =
                    line.replaceFirst(
                            ".*PAIS DE ORIGEM\\s*[:.-]?\\s*",
                            ""
                    );
            return normalizarNomePais(
                    tail
            );
        }
        return EMPTY;
    }

    private static String matchCountryWholeLine(
            String line
    ) {
        for (
                String country
                : COUNTRY_NAMES
        ) {
            String n =
                    normalizar(
                            country
                    );
            if (
                    line.equals(
                            n
                    )
            ) {
                return normalizarNomePais(
                        n
                );
            }
        }
        return EMPTY;
    }

    private static String normalizarNomePais(
            String value
    ) {
        String n =
                normalizar(
                        value
                )
                        .replaceAll(
                                "[^A-Z ]",
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();
        switch (n) {
            case "BRASIL":
            case "BRAZIL":
                return "BRASIL";
            case "ARGENTINA":
                return "ARGENTINA";
            case "CHILE":
                return "CHILE";
            case "COLOMBIA":
                return "COLOMBIA";
            case "MEXICO":
                return "MEXICO";
            case "PERU":
                return "PERU";
            case "URUGUAI":
            case "URUGUAY":
                return "URUGUAI";
            case "PARAGUAI":
            case "PARAGUAY":
                return "PARAGUAI";
            case "BOLIVIA":
                return "BOLIVIA";
            case "CANADA":
                return "CANADA";
            case "PORTUGAL":
                return "PORTUGAL";
            case "FRANCA":
            case "FRANCE":
                return "FRANCA";
            case "ITALIA":
            case "ITALY":
                return "ITALIA";
            case "ALEMANHA":
            case "GERMANY":
                return "ALEMANHA";
            case "ESPANHA":
            case "SPAIN":
                return "ESPANHA";
            case "INDIA":
                return "INDIA";
            case "CHINA":
                return "CHINA";
            case "JAPAO":
            case "JAPAN":
                return "JAPAO";
            case "AUSTRALIA":
                return "AUSTRALIA";
            case "POLONIA":
            case "POLAND":
                return "POLONIA";
            case "TURQUIA":
            case "TURKEY":
                return "TURQUIA";
            case "SUICA":
            case "SWITZERLAND":
                return "SUICA";
            default:
                return EMPTY;
        }
    }

    private static boolean pareceImportacao(
            List<String> lines,
            int index
    ) {
        int from =
                Math.max(
                        0,
                        index - 5
                );
        int to =
                Math.min(
                        lines.size() - 1,
                        index + 1
                );
        for (
                int i = from;
                i <= to;
                i++
        ) {
            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );
            for (
                    String marker
                    : IMPORTER_MARKERS
            ) {
                if (
                        contémTermoComFronteira(
                                n,
                                marker
                        )
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean pareceDistribuicao(
            List<String> lines,
            int index
    ) {
        int from =
                Math.max(
                        0,
                        index - 3
                );
        int to =
                Math.min(
                        lines.size() - 1,
                        index + 1
                );
        for (
                int i = from;
                i <= to;
                i++
        ) {
            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );
            for (
                    String marker
                    : DISTRIBUTOR_MARKERS
            ) {
                if (
                        contémTermoComFronteira(
                                n,
                                marker
                        )
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contemMarcadorCorporativo(
            String line
    ) {
        String n =
                normalizar(
                        line
                );
        for (
                String marker
                : CORPORATE_MARKERS
        ) {
            if (
                    contémTermoComFronteira(
                            n,
                            marker
                    )
            ) {
                return true;
            }
        }
        return false;
    }

    private static boolean temSufixoEmpresarial(
            String line
    ) {
        String n =
                normalizar(
                        line
                );
        for (
                String suffix
                : COMPANY_SUFFIXES
        ) {
            if (
                    n.matches(
                            ".*\\b"
                                    + Pattern.quote(
                                    normalizar(
                                            suffix
                                    )
                            )
                                    + "\\b.*"
                    )
            ) {
                return true;
            }
        }
        return false;
    }

    private static final class CompanyMarker {
        final String prefix;
        final String remainder;
        final String marker;
        final String normalizedMarker;
        CompanyMarker(
                String prefix,
                String remainder,
                String marker,
                String normalizedMarker
        ) {
            this.prefix =
                    prefix == null
                            ? EMPTY
                            : prefix;
            this.remainder =
                    remainder == null
                            ? EMPTY
                            : remainder;
            this.marker =
                    marker == null
                            ? EMPTY
                            : marker;
            this.normalizedMarker =
                    normalizedMarker == null
                            ? EMPTY
                            : normalizedMarker;
        }
    }
}
