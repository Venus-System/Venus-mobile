package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.venussystem.venusmobile.view.util.ScanFrontText.adicionarProduto;
import static com.venussystem.venusmobile.view.util.ScanFrontText.adicionarUnico;
import static com.venussystem.venusmobile.view.util.ScanFrontText.contemAntiperspirante;
import static com.venussystem.venusmobile.view.util.ScanFrontText.contemAntitranspirante;
import static com.venussystem.venusmobile.view.util.ScanFrontText.contemTermo;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isDescritorAntiperspirante;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isDescritorAntitranspirante;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isSomenteNumero;
import static com.venussystem.venusmobile.view.util.ScanFrontText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.ANTIPERSPIRANTE;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.ANTITRANSPIRANTE;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CAPACIDADE_OCR_PREFIX_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.CAPACIDADE_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.FL_OZ_PATTERN;
import static com.venussystem.venusmobile.view.util.ScanFrontVocabulary.NOISE_WORDS;

/** Internal collaborator for ScanFrontExtractor; not a second scan entry point. */
final class ScanFrontProductExtractor {
    private ScanFrontProductExtractor() { }

    private static final Pattern FPS_PATTERN =
            Pattern.compile(
                    "(?i)\\b(FPS|SPF)\\s*(\\d{1,3})\\b"
            );

    private static final Pattern PA_PATTERN =
            Pattern.compile(
                    "(?i)\\b(PA)\\s*(\\+{2,4})\\b"
            );

    /*
     * =========================================================
     * APRESENTAÇÕES
     * =========================================================
     */
    private static final Set<String> PRESENTATIONS =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "SPRAY",
                            "AEROSOL",
                            "ROLL ON",
                            "ROLLON",
                            "BASTAO",
                            "CREME",
                            "LOCAO",
                            "GEL",
                            "LIQUIDO",
                            "ESPUMA",
                            "SABONETE",
                            "OLEO",
                            "SERUM",
                            "BALM",
                            "POMADA"
                    )
            );

    /*
     * =========================================================
     * TERMOS GENÉRICOS
     * =========================================================
     */
    private static final Set<String> GENERIC_PRODUCT_WORDS =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "PROTECAO",
                            "PROTECTION",
                            "PROTECCAO",
                            "PROTECCION",
                            "SECA",
                            "SECO",
                            "LONG",
                            "LASTING",
                            "PROLONGADA",
                            "PROLONGADO",
                            "PROLONGED",
                            "DRY",
                            "PERFUME",
                            "PERFUMACAO",
                            "MASCULINO",
                            "FEMININO",
                            "ADULTO",
                            "INFANTIL",
                            "SPRAY",
                            "AEROSOL",
                            "CREME",
                            "LOCAO",
                            "GEL",
                            "ESPUMA"
                    )
            );

    /*
     * =========================================================
     * EXTENSÃO GENERALISTA
     * =========================================================
     */
    private static final Set<String> EXTENDED_PRODUCT_REFERENCES =
            new LinkedHashSet<>(
                    Arrays.asList(
                            /*
                             * CABELO
                             */
                            "SHAMPOO",
                            "SHAMPOO SECO",
                            "DRY SHAMPOO",
                            "CONDICIONADOR",
                            "CONDITIONER",
                            "MASCARA CAPILAR",
                            "MASCARA DE CABELO",
                            "HAIR MASK",
                            "CREME PARA PENTEAR",
                            "CREME DE PENTEAR",
                            "LEAVE IN",
                            "LEAVE-IN",
                            "FINALIZADOR",
                            "FINALIZADOR CAPILAR",
                            "PROTETOR TERMICO",
                            "THERMAL PROTECTOR",
                            "OLEO CAPILAR",
                            "HAIR OIL",
                            "TONICO CAPILAR",
                            "HAIR TONIC",
                            "TRATAMENTO CAPILAR",
                            "HAIR TREATMENT",
                            "AMPOLA CAPILAR",
                            "RECONSTRUTOR",
                            "TINTURA",
                            "COLORACAO",
                            "DESCOLORANTE",
                            /*
                             * SKINCARE
                             */
                            "HIDRATANTE FACIAL",
                            "FACIAL MOISTURIZER",
                            "FACE MOISTURIZER",
                            "HIDRATANTE CORPORAL",
                            "BODY LOTION",
                            "BODY CREAM",
                            "LIMPEZA FACIAL",
                            "FACE CLEANSER",
                            "CLEANSER",
                            "GEL DE LIMPEZA",
                            "GEL DE LIMPEZA FACIAL",
                            "ESPUMA DE LIMPEZA",
                            "FOAM CLEANSER",
                            "AGUA MICELAR",
                            "MICELLAR WATER",
                            "SERUM FACIAL",
                            "FACE SERUM",
                            "CREME FACIAL",
                            "FACE CREAM",
                            "OLEO FACIAL",
                            "FACE OIL",
                            "MASCARA FACIAL",
                            "FACE MASK",
                            "ESFOLIANTE",
                            "FACIAL SCRUB",
                            "BODY SCRUB",
                            "TONICO FACIAL",
                            "FACIAL TONER",
                            /*
                             * PROTEÇÃO SOLAR
                             */
                            "PROTETOR SOLAR",
                            "SUNSCREEN",
                            "FILTRO SOLAR",
                            "SUN PROTECTION",
                            "BLOQUEADOR SOLAR",
                            "FOTOPROTECAO",
                            "PROTETOR SOLAR FACIAL",
                            "PROTETOR SOLAR CORPORAL",
                            /*
                             * CORPO
                             */
                            "DESODORANTE",
                            "DEODORANT",
                            "ANTITRANSPIRANTE",
                            "ANTIPERSPIRANTE",
                            "SABONETE",
                            "SOAP",
                            "GEL DE BANHO",
                            "SHOWER GEL",
                            "BODY WASH",
                            "CREME CORPORAL",
                            "BODY CREAM",
                            "HIDRATANTE CORPORAL",
                            "BODY LOTION",
                            "OLEO CORPORAL",
                            "BODY OIL",
                            /*
                             * MAQUIAGEM
                             */
                            "BASE",
                            "FOUNDATION",
                            "CORRETIVO",
                            "CONCEALER",
                            "PRIMER",
                            "PO FACIAL",
                            "PO COMPACTO",
                            "SETTING POWDER",
                            "BLUSH",
                            "BRONZER",
                            "BRONZEADOR",
                            "ILUMINADOR",
                            "HIGHLIGHTER",
                            "CONTORNO",
                            "CONTOUR",
                            "BATOM",
                            "LIPSTICK",
                            "GLOSS",
                            "LIP GLOSS",
                            "BALM LABIAL",
                            "LIP BALM",
                            "DELINEADOR",
                            "EYELINER",
                            "LAPIS DE OLHO",
                            "LAPIS LABIAL",
                            "SOMBRA",
                            "EYESHADOW",
                            "PALETA",
                            "PALETTE",
                            "MASCARA DE CILIOS",
                            "MASCARA DE PESTANAS",
                            /*
                             * UNHAS
                             */
                            "ESMALTE",
                            "NAIL POLISH",
                            "BASE DE UNHA",
                            "TOP COAT",
                            "REMOVEDOR",
                            /*
                             * PERFUMARIA
                             */
                            "PERFUME",
                            "PARFUM",
                            "FRAGRANCE",
                            "EAU DE PARFUM",
                            "EAU DE TOILETTE",
                            "EAU DE COLOGNE",
                            "COLONIA",
                            "COLOGNE",
                            "BODY SPLASH",
                            "BODY MIST",
                            /*
                             * BARBEAR
                             */
                            "GEL DE BARBEAR",
                            "SHAVING GEL",
                            "ESPUMA DE BARBEAR",
                            "SHAVING FOAM",
                            "CREME DE BARBEAR",
                            "SHAVING CREAM",
                            "POS BARBA",
                            "AFTER SHAVE",
                            /*
                             * HIGIENE
                             */
                            "CREME DENTAL",
                            "TOOTHPASTE",
                            "ENXAGUANTE BUCAL",
                            "MOUTHWASH"
                    )
            );

    /*
     * =========================================================
     * ATIVOS / INGREDIENTES DE DESTAQUE
     * =========================================================
     */
    private static final Set<String> EXTENDED_ACTIVE_REFERENCES =
            new LinkedHashSet<>(
                    Arrays.asList(
                            /*
                             * SKINCARE
                             */
                            "ACIDO HIALURONICO",
                            "HIALURONICO",
                            "HYALURONIC",
                            "HYALURONIC ACID",
                            "NIACINAMIDA",
                            "NIACINAMIDE",
                            "RETINOL",
                            "RETINAL",
                            "RETINALDEHYDE",
                            "VITAMINA C",
                            "VITAMIN C",
                            "VITAMINA E",
                            "VITAMIN E",
                            "ACIDO SALICILICO",
                            "SALICYLIC ACID",
                            "SALICYLIC",
                            "ACIDO GLICOLICO",
                            "GLYCOLIC ACID",
                            "GLYCOLIC",
                            "ACIDO LATICO",
                            "LACTIC ACID",
                            "ACIDO MANDELICO",
                            "MANDELIC ACID",
                            "ACIDO AZELAICO",
                            "AZELAIC ACID",
                            "ACIDO FERULICO",
                            "FERULIC ACID",
                            "CERAMIDAS",
                            "CERAMIDES",
                            "PEPTIDE",
                            "PEPTIDES",
                            "COLAGENO",
                            "COLLAGEN",
                            "ELASTINA",
                            "ELASTIN",
                            "CAFEINA",
                            "CAFFEINE",
                            "ZINCO",
                            "ZINC",
                            "UREIA",
                            "UREA",
                            "CENTELLA ASIATICA",
                            "CENTELLA",
                            "ALOE VERA",
                            "BABOSA",
                            "CHA VERDE",
                            "GREEN TEA",
                            "MUCINA",
                            "SNAIL",
                            /*
                             * CABELO
                             */
                            "QUERATINA",
                            "KERATIN",
                            "BIOTINA",
                            "BIOTIN",
                            "PANTENOL",
                            "PANTHENOL",
                            "PROTEINA",
                            "PROTEIN",
                            "OLEO DE ARGAN",
                            "ARGAN OIL",
                            "OLEO DE COCO",
                            "COCONUT OIL",
                            "OLEO DE JOJOBA",
                            "JOJOBA OIL",
                            "MANTEIGA DE KARITE",
                            "SHEA BUTTER"
                    )
            );

    /*
     * =========================================================
     * VARIANTES COMERCIAIS
     * =========================================================
     */
    private static final Set<String> EXTENDED_VARIANT_REFERENCES =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "EXTREME",
                            "EXTRA",
                            "INTENSE",
                            "INTENSO",
                            "INTENSA",
                            "ULTRA",
                            "MAX",
                            "MAXIMUM",
                            "PLUS",
                            "MEN",
                            "MAN",
                            "WOMEN",
                            "WOMAN",
                            "MASCULINO",
                            "FEMININO",
                            "UNISSEX",
                            "BABY",
                            "KIDS",
                            "INFANTIL",
                            "TEEN",
                            "JUNIOR",
                            "SENSITIVE",
                            "SENSIVEL",
                            "INVISIBLE",
                            "BLACK",
                            "WHITE",
                            "DRY",
                            "FRESH",
                            "SPORT",
                            "ACTIVE",
                            "CLASSIC",
                            "ORIGINAL",
                            "NIGHT",
                            "DAY",
                            "GLOW",
                            "MATTE",
                            "MATE",
                            "WATERPROOF",
                            "LONGWEAR",
                            "OIL FREE",
                            "OILFREE",
                            "NON COMEDOGENIC",
                            "ANTIFRIZZ",
                            "ANTI FRIZZ",
                            "ANTIQUEDA",
                            "ANTI QUEDA",
                            "REPAIR",
                            "RECONSTRUCAO",
                            "HIDRATACAO",
                            "HYDRATION",
                            "NUTRICAO",
                            "NUTRITION",
                            "CACHOS",
                            "CURLS",
                            "VOLUME",
                            "LISO",
                            "SMOOTH",
                            "ANTI AGE",
                            "ANTI-AGE",
                            "ANTISSINAIS",
                            "CALM",
                            "CALMING",
                            "CALMANTE",
                            "PURIFY",
                            "PURIFICANTE",
                            "DEEP",
                            "PROFUNDO",
                            "PROFUNDA"
                    )
            );

    /*
     * =========================================================
     * PRODUTO
     * =========================================================
     */
    static void adicionarTokensProduto(
            @NonNull String linha,
            @NonNull List<String> destino
    ) {
        String texto =
                normalizar(
                        linha
                );
        if (texto.isEmpty()) {
            return;
        }
        /*
         * Descobre se a própria linha contém uma unidade.
         *
         * Isso permite eliminar artefatos como:
         *
         * E250 ML
         * I250 ML
         */
        boolean possuiCapacidade =
                CAPACIDADE_PATTERN
                        .matcher(
                                texto
                        )
                        .find()
                        || CAPACIDADE_OCR_PREFIX_PATTERN
                        .matcher(
                                texto
                        )
                        .find();
        boolean possuiFlOz =
                FL_OZ_PATTERN
                        .matcher(
                                texto
                        )
                        .find();
        String[] tokens =
                texto.split(
                        "\\s+"
                );
        for (String token :
                tokens) {
            if (token == null
                    || token.isEmpty()) {
                continue;
            }
            /*
             * Números puros não são nomes.
             */
            if (isSomenteNumero(
                    token
            )) {
                continue;
            }
            /*
             * Capacidade:
             *
             * 250ML
             * 200G
             */
            if (token.matches(
                    "\\d+(?:[.,]\\d+)?(?:ML|G|GR|KG|L)"
            )) {
                continue;
            }
            /*
             * FL OZ:
             *
             * 84FLOZ
             * 8.4FLOZ
             */
            if (token.matches(
                    "(?i)\\d+(?:[.,]\\d+)?FLOZ"
            )) {
                continue;
            }
            /*
             * Fragmento OCR:
             *
             * E250
             *
             * somente é removido quando existe evidência
             * de capacidade na própria linha.
             *
             * Assim Q10, B5, C10 etc. não são afetados.
             */
            if (possuiCapacidade
                    && token.matches(
                    "(?i)[A-Z]\\d+(?:[.,]\\d+)?"
            )) {
                continue;
            }
            /*
             * Número associado a FL OZ.
             */
            if (possuiFlOz
                    && token.matches(
                    "[0-9.,]+"
            )) {
                continue;
            }
            /*
             * Horas.
             */
            if (token.matches(
                    "\\d{1,3}H"
            )) {
                adicionarProduto(
                        destino,
                        token
                );
                continue;
            }
            /*
             * Antitranspirante.
             */
            if (isDescritorAntitranspirante(
                    token
            )) {
                adicionarProduto(
                        destino,
                        ANTITRANSPIRANTE
                );
                continue;
            }
            /*
             * Antiperspirante.
             */
            if (isDescritorAntiperspirante(
                    token
            )) {
                adicionarProduto(
                        destino,
                        ANTIPERSPIRANTE
                );
                continue;
            }
            /*
             * FPS / SPF.
             */
            if (token.matches(
                    "(?i)(FPS|SPF)\\d{1,3}"
            )) {
                adicionarProduto(
                        destino,
                        token
                );
                continue;
            }
            /*
             * Ruído.
             */
            if (NOISE_WORDS.contains(
                    token
            )) {
                continue;
            }
            /*
             * Conjunções.
             */
            if (token.equals("E")
                    || token.equals("Y")
                    || token.equals("AND")
                    || token.equals("DE")
                    || token.equals("DO")
                    || token.equals("DA")
                    || token.equals("EM")
                    || token.equals("PARA")
                    || token.equals("POR")) {
                continue;
            }
            /*
             * Tokens muito curtos.
             */
            if (token.length() < 3) {
                continue;
            }
            /*
             * Mantemos exatamente a filosofia original:
             *
             * GENERIC_PRODUCT_WORDS NÃO apaga o token.
             */
            adicionarProduto(
                    destino,
                    token
            );
        }
    }

    static void limparProdutos(
            @NonNull List<String> produtos
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String produto :
                produtos) {
            if (produto == null) {
                continue;
            }
            String normalizado =
                    normalizar(
                            produto
                    );
            if (normalizado.isEmpty()) {
                continue;
            }
            /*
             * Descritores canônicos.
             */
            if (normalizado.equals(
                    ANTITRANSPIRANTE
            )
                    || normalizado.equals(
                    ANTIPERSPIRANTE
            )) {
                adicionarUnico(
                        resultado,
                        normalizado
                );
                continue;
            }
            /*
             * Horas.
             */
            if (normalizado.matches(
                    "\\d{1,3}H"
            )) {
                adicionarUnico(
                        resultado,
                        normalizado
                );
                continue;
            }
            /*
             * FPS/SPF.
             */
            if (normalizado.matches(
                    "(?i)(FPS|SPF)\\d{1,3}"
            )) {
                adicionarUnico(
                        resultado,
                        normalizado
                );
                continue;
            }
            /*
             * PA.
             */
            if (normalizado.matches(
                    "(?i)PA\\+{2,4}"
            )) {
                adicionarUnico(
                        resultado,
                        normalizado
                );
                continue;
            }
            /*
             * FL OZ nunca é nome de produto.
             */
            if (normalizado.matches(
                    "(?i)\\d+(?:[.,]\\d+)?FLOZ"
            )) {
                continue;
            }
            /*
             * Artefatos como E250 não devem chegar ao matcher.
             */
            if (normalizado.matches(
                    "(?i)[A-Z]\\d+(?:[.,]\\d+)?"
            )) {
                continue;
            }
            /*
             * Frases gigantes não são candidatas.
             */
            if (normalizado.length() > 45) {
                continue;
            }
            String[] tokens =
                    normalizado.split(
                            "\\s+"
                    );
            boolean possuiTokenUtil =
                    false;
            for (String token :
                    tokens) {
                if (token.length() < 3) {
                    continue;
                }
                if (NOISE_WORDS.contains(
                        token
                )) {
                    continue;
                }
                if (token.equals("E")
                        || token.equals("Y")
                        || token.equals("AND")) {
                    continue;
                }
                possuiTokenUtil = true;
                break;
            }
            if (possuiTokenUtil) {
                adicionarUnico(
                        resultado,
                        normalizado
                );
            }
        }
        produtos.clear();
        produtos.addAll(
                resultado
        );
    }

    /*
     * =========================================================
     * FRASES DE PRODUTO
     * =========================================================
     */
    static boolean isFraseProdutoDistintiva(
            @NonNull String texto
    ) {
        String normalizado =
                normalizar(
                        texto
                );
        String[] tokens =
                normalizado.split(
                        "\\s+"
                );
        if (tokens.length < 2) {
            return false;
        }
        if (tokens.length > 5) {
            return false;
        }
        if (tokens[0].equals("E")
                || tokens[0].equals("Y")
                || tokens[0].equals("AND")
                || tokens[0].equals("DE")
                || tokens[0].equals("DA")
                || tokens[0].equals("DO")
                || tokens[0].equals("PARA")
                || tokens[0].equals("POR")) {
            return false;
        }
        int distintos =
                0;
        for (String token :
                tokens) {
            if (token.length() < 3) {
                continue;
            }
            if (token.equals("E")
                    || token.equals("Y")
                    || token.equals("AND")
                    || token.equals("DE")
                    || token.equals("DA")
                    || token.equals("DO")) {
                continue;
            }
            if (NOISE_WORDS.contains(
                    token
            )) {
                continue;
            }
            if (!GENERIC_PRODUCT_WORDS.contains(
                    token
            )) {
                distintos++;
            }
        }
        return distintos >= 2;
    }

    /*
     * =========================================================
     * DESCRITORES ANTITRANSPIRANTE
     * =========================================================
     */
    static void extrairDescritoresProduto(
            @NonNull String linha,
            @NonNull List<String> destino
    ) {
        if (contemAntitranspirante(
                linha
        )) {
            adicionarProduto(
                    destino,
                    ANTITRANSPIRANTE
            );
        }
        if (contemAntiperspirante(
                linha
        )) {
            adicionarProduto(
                    destino,
                    ANTIPERSPIRANTE
            );
        }
    }

    static void adicionarDescritoresDetectados(
            @NonNull List<String> linhas,
            @NonNull List<String> destino
    ) {
        for (String linha :
                linhas) {
            if (contemAntitranspirante(
                    linha
            )) {
                adicionarProduto(
                        destino,
                        ANTITRANSPIRANTE
                );
            }
            if (contemAntiperspirante(
                    linha
            )) {
                adicionarProduto(
                        destino,
                        ANTIPERSPIRANTE
                );
            }
        }
    }

    /*
     * =========================================================
     * EXTENSÃO GENERALISTA
     * =========================================================
     */
    static void adicionarReferenciasExpandidas(
            @NonNull List<String> linhas,
            @NonNull List<String> destino
    ) {
        for (String linha :
                linhas) {
            if (linha == null
                    || linha.isEmpty()) {
                continue;
            }
            String texto =
                    normalizar(
                            linha
                    );
            /*
             * Tipos/categorias.
             */
            for (String referencia :
                    EXTENDED_PRODUCT_REFERENCES) {
                if (contemTermo(
                        texto,
                        referencia
                )) {
                    adicionarProduto(
                            destino,
                            referencia
                    );
                }
            }
            /*
             * Ativos.
             */
            for (String referencia :
                    EXTENDED_ACTIVE_REFERENCES) {
                if (contemTermo(
                        texto,
                        referencia
                )) {
                    adicionarProduto(
                            destino,
                            referencia
                    );
                }
            }
            /*
             * Variantes.
             */
            for (String referencia :
                    EXTENDED_VARIANT_REFERENCES) {
                if (contemTermo(
                        texto,
                        referencia
                )) {
                    adicionarProduto(
                            destino,
                            referencia
                    );
                }
            }
        }
    }

    /*
     * =========================================================
     * FPS / SPF / PA
     * =========================================================
     */
    static void extrairReferenciasNumericas(
            @NonNull List<String> linhas,
            @NonNull List<String> destino
    ) {
        for (String linha :
                linhas) {
            if (linha == null
                    || linha.isEmpty()) {
                continue;
            }
            String texto =
                    normalizar(
                            linha
                    );
            Matcher fpsMatcher =
                    FPS_PATTERN.matcher(
                            texto
                    );
            while (fpsMatcher.find()) {
                String prefixo =
                        fpsMatcher.group(1);
                String numero =
                        fpsMatcher.group(2);
                if (prefixo != null
                        && numero != null) {
                    adicionarProduto(
                            destino,
                            prefixo.toUpperCase(
                                    Locale.ROOT
                            ) + numero
                    );
                }
            }
            Matcher paMatcher =
                    PA_PATTERN.matcher(
                            texto
                    );
            while (paMatcher.find()) {
                String prefixo =
                        paMatcher.group(1);
                String maises =
                        paMatcher.group(2);
                if (prefixo != null
                        && maises != null) {
                    adicionarProduto(
                            destino,
                            prefixo.toUpperCase(
                                    Locale.ROOT
                            ) + maises
                    );
                }
            }
        }
    }

    /*
     * =========================================================
     * APRESENTAÇÕES
     * =========================================================
     */
    static void extrairApresentacoes(
            @NonNull String linha,
            @NonNull List<String> destino
    ) {
        for (String apresentacao :
                PRESENTATIONS) {
            String normalizada =
                    normalizar(
                            apresentacao
                    );
            if (linha.contains(
                    normalizada
            )) {
                adicionarUnico(
                        destino,
                        normalizada
                );
            }
        }
    }
}
