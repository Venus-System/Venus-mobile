package com.venussystem.venusmobile.view.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Internal collaborator for ScanFrontExtractor; not a second scan entry point. */
final class ScanFrontVocabulary {
    private ScanFrontVocabulary() { }

    /*
     * =========================================================
     * PADRÕES
     * =========================================================
     */
    static final Pattern CAPACIDADE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*(ML|M[L1]|G|GR|KG|L)\\b"
            );

    static final Pattern CAPACIDADE_OCR_PREFIX_PATTERN =
            Pattern.compile(
                    "(?i)\\b[A-Z](\\d+(?:[.,]\\d+)?)\\s*(ML|M[L1]|G|GR|KG|L)\\b"
            );

    static final Pattern CONCENTRACAO_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*%"
            );

    static final Pattern FL_OZ_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*(FL\\s*OZ|FLOZ)\\b"
            );

    /*
     * =========================================================
     * DESCRITORES IMPORTANTES
     * =========================================================
     */
    static final String ANTITRANSPIRANTE =
            "ANTITRANSPIRANTE";

    static final String ANTIPERSPIRANTE =
            "ANTIPERSPIRANTE";

    /*
     * =========================================================
     * RUÍDO DE TEXTO
     * =========================================================
     */
    static final Set<String> NOISE_WORDS =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "NOVA",
                            "NOVO",
                            "NEW",
                            "TECNOLOGIA",
                            "TECHNOLOGY",
                            "EVITA",
                            "EVITAR",
                            "PREVINE",
                            "PREVENTS",
                            "RESIDUOS",
                            "MANCHAS",
                            "STAINS",
                            "ROUPAS",
                            "CLOTHES",
                            "SUOR",
                            "SWEAT",
                            "ODOR",
                            "APROVADO",
                            "APROVADA",
                            "TESTADO",
                            "TESTADA",
                            "DERMATOLOGICAMENTE",
                            "APLICAR",
                            "APLICACAO",
                            "UTILIZAR",
                            "UTILIZACAO",
                            "MANTER",
                            "AGITE",
                            "AGITAR",
                            "PRECAUCOES",
                            "PRECAUTIONS",
                            "EXTERNO",
                            "CONTEM",
                            "CONTRA",
                            "BAIXO",
                            "POTENCIAL",
                            "SENSIVEIS",
                            "DIRETA",
                            "DIRETO",
                            "LUZ",
                            "SOLAR",
                            "FRESCO",
                            "INFLAMAVEL",
                            "NAO",
                            "USE"
                    )
            );
}
