package com.venussystem.venusmobile.view.util;

import java.util.Set;
import java.util.regex.Pattern;

/** Internal collaborator for ScanBackExtractor; not a second scan entry point. */
final class ScanBackRules {
    private ScanBackRules() { }

    static final String TAG =
            "VENUS_BACK_PARSE";

    static final String EMPTY =
            "";

    /*
     * ================================================================
     * HEADINGS
     * ================================================================
     */
    static final String[] INGREDIENT_HEADINGS = {
            "INGREDIENTES",
            "INGREDIENTE",
            "INGREDIENTS",
            "INGREDIENT",
            "INGREDIENTI",
            "COMPOSICAO",
            "COMPOSITION",
            "COMPOSICION",
            "COMPOSICION INCI",
            "COMPOSICAO INCI",
            "INCI"
    };

    /*
     * ================================================================
     * REGEX
     * ================================================================
     */
    static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                    Pattern.CASE_INSENSITIVE
            );

    static final Pattern ADDRESS_INLINE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:RUA|R\\.?|AVENIDA|AV\\.?|ALAMEDA|TRAVESSA|RODOVIA|"
                            + "CALLE|CARRERA|JIRON|BOULEVARD|BLVD)\\b"
            );

    /**
     * Captura um pequeno bloco que termina claramente em sufixo empresarial.
     * Usado apenas como barreira de segurança dentro da composição,
     * principalmente quando o OCR cola empresa + endereço na mesma linha.
     */
    /** OCR contact text that may be malformed. */
    static final Pattern CONTACT_CONTEXT_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:QUESTIONS{0,2}|CONTACT|CUSTOMER\\s+SERVICE|"
                            + "ATENDIMENTO(?:\\s+AO\\s+CONSUMIDOR)?|"
                            + "MAIL\\s+SUPPORT|MAIL\\s+SUPOR|MAL\\s+SOPPORT|"
                            + "SOPPORT|SUPPORI|SUPPOR|E[- ]?MAIL)"
                            + "[^\\n]{0,180}?"
                            + "(?:@|MAIL|MAL|E[- ]?MAIL|SUPPORT|SOPPORT|SUPPORI|SUPPOR|CALL|PHONE|FONE)"
                            + "[^\\n]{0,140}"
            );

    static final Pattern INLINE_COMPANY_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:(?:\\p{L})[\\p{L}0-9'&.-]{1,}\\s+){1,4}"
                            + "(?:LTDA|LTD|S\\.?A\\.?|S/A|SA|SAS|SRL|LLC|INC|CORP|"
                            + "CORPORATION|PLC|BV|NV|GMBH|AG|KG|SPA|EIRELI|ME)\\b"
            );

    static final Pattern URL_PATTERN =
            Pattern.compile(
                    // Bare domains need a conservative suffix; OCR dots are not URLs.
                    "(?i)(?<![a-z0-9@._-])(?:"
                            + "(?:https?://|www\\.)[a-z0-9][a-z0-9.-]*"
                            + "|(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+"
                            + "(?:com|org|net|edu|gov|br|pt|uk|us|fr|de|it|es|eu)"
                            + ")(?![a-z0-9_-]|\\.[a-z])"
                            + "(?:/[a-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?"
            );

    static final Pattern PHONE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "(?:\\+?\\d{1,3}[ .-]?)?"
                            + "(?:\\(?\\d{2}\\)?[ .-]?)?"
                            + "(?:9?\\d{4})[ .-]?\\d{4}"
                            + "(?!\\d)"
            );

    static final Pattern CNPJ_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "\\d{2}[ .-]?\\d{3}[ .-]?\\d{3}"
                            + "[\\/. -]?\\d{4}[\\- .]?\\d{2}"
                            + "(?!\\d)"
            );

    static final Pattern CEP_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "\\d{5}[ .-]?\\d{3}"
                            + "(?!\\d)"
            );

    static final Pattern REGISTRATION_MARKER_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:REGISTRO(?:\\s+ANVISA)?"
                            + "|REGISTRATION"
                            + "|PROCESSO(?:\\s+ANVISA)?"
                            + "|AUT\\s*FUNC"
                            + "|AUT\\.?\\s*FUNC"
                            + "|AUTL?FUNC"
                            + "|AUTORIZACAO"
                            + "|NUMERO\\s+DE\\s+REGISTRO)\\b"
            );

    static final Pattern STREET_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:RUA|AVENIDA|AV|ALAMEDA|TRAVESSA|"
                            + "RODOVIA|CALLE|CARRERA|JIRON|BOULEVARD|BLVD)\\b"
            );

    /*
     * ================================================================
     * INCI CONHECIDOS
     * ================================================================
     */
    static final Set<String> COMMON_INCI_BOUNDARIES =
            com.venussystem.venusmobile.domain.scan.ScanIngredientVocabulary.NAMES;
}
