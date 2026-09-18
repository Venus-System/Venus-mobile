package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;

import com.venussystem.venusmobile.model.ScanFrontData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai dados úteis da frente da embalagem.
 *
 * Responsabilidades:
 *
 * - candidatos de marca;
 * - candidatos de produto;
 * - apresentações;
 * - capacidade;
 * - concentração.
 *
 * IMPORTANTE:
 *
 * O extractor não escolhe o produto do banco.
 *
 * Ele somente preserva e organiza evidências do OCR.
 * A decisão final fica no ScanProductMatchRepository.
 *
 * ESTRATÉGIA:
 *
 * - mantém a lógica original de extração;
 * - amplia referências para diferentes categorias cosméticas;
 * - adiciona ativos de destaque;
 * - adiciona variantes comerciais;
 * - adiciona FPS/SPF e PA;
 * - suporta marcas compostas na mesma linha;
 * - suporta marcas compostas quebradas em várias linhas;
 * - não assume que a primeira linha comercial é necessariamente
 *   a marca verdadeira;
 * - preserva múltiplas candidatas de marca para validação
 *   posterior no catálogo.
 */
public final class ScanFrontExtractor {

    private ScanFrontExtractor() {
        // Utility class.
    }

    /*
     * =========================================================
     * PADRÕES
     * =========================================================
     */

    private static final Pattern CAPACIDADE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*(ML|M[L1]|G|GR|KG|L)\\b"
            );

    private static final Pattern CAPACIDADE_OCR_PREFIX_PATTERN =
            Pattern.compile(
                    "(?i)\\b[A-Z](\\d+(?:[.,]\\d+)?)\\s*(ML|M[L1]|G|GR|KG|L)\\b"
            );

    private static final Pattern CONCENTRACAO_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*%"
            );

    private static final Pattern HORAS_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d{1,3})\\s*H\\b"
            );

    private static final Pattern FPS_PATTERN =
            Pattern.compile(
                    "(?i)\\b(FPS|SPF)\\s*(\\d{1,3})\\b"
            );

    private static final Pattern PA_PATTERN =
            Pattern.compile(
                    "(?i)\\b(PA)\\s*(\\+{2,4})\\b"
            );

    private static final Pattern FL_OZ_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*(FL\\s*OZ|FLOZ)\\b"
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
     * DESCRITORES IMPORTANTES
     * =========================================================
     */

    private static final String ANTITRANSPIRANTE =
            "ANTITRANSPIRANTE";

    private static final String ANTIPERSPIRANTE =
            "ANTIPERSPIRANTE";

    /*
     * =========================================================
     * TERMOS QUE NÃO DEVEM SER MARCA
     *
     * Eles continuam podendo ser candidatos de PRODUTO.
     * =========================================================
     */

    private static final Set<String> NON_BRAND_WORDS =
            new LinkedHashSet<>(
                    Arrays.asList(

                            "EXTREME",

                            "MEN",
                            "MAN",
                            "WOMEN",
                            "WOMAN",

                            "MASCULINO",
                            "FEMININO",
                            "ADULTO",
                            "INFANTIL",
                            "KIDS",
                            "BABY",

                            "INVISIBLE",
                            "BLACK",
                            "WHITE",
                            "DRY",
                            "FRESH",
                            "SPORT",
                            "CLASSIC",
                            "ORIGINAL",

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

                            "PERFUME",
                            "PERFUMACAO",

                            "SPRAY",
                            "AEROSOL",
                            "ROLL",
                            "ROLLON",

                            "CREME",
                            "LOCAO",
                            "GEL",
                            "ESPUMA",
                            "SABONETE",

                            "ANTITRANSPIRANTE",
                            "ANTITRANSPIRANT",
                            "ANTITRANSPIRAN",
                            "ANTITRANSPIRANI",

                            "ANTIPERSPIRANTE",
                            "ANTIPERSPIRANT",
                            "ANTIPERSPIRAN",
                            "ANTIPERSPIRANI",

                            "USO",
                            "EXTERNO",
                            "ATIVO",
                            "ATIVA",

                            "TESTADO",
                            "TESTADA",
                            "APROVADO",
                            "APROVADA",

                            "NOVA",
                            "NOVO",
                            "NEW",
                            "TECNOLOGIA",
                            "TECHNOLOGY",

                            "FORMULA",

                            "EVITA",
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

                            "CONTRA",

                            "MANTER",
                            "AGITE",
                            "AGITAR",

                            "PRECAUCOES",
                            "PRECAUTIONS",

                            "CONTEM",

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
                            "USE",

                            "E",
                            "Y",
                            "AND"
                    )
            );

    /*
     * =========================================================
     * RUÍDO DE TEXTO
     * =========================================================
     */

    private static final Set<String> NOISE_WORDS =
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
     * CONTINUAÇÕES DE MARCAS COMPOSTAS
     * =========================================================
     */

    private static final Set<String> BRAND_CONTINUATION_WORDS =
            new LinkedHashSet<>(
                    Arrays.asList(

                            "SECRET",

                            "ROCHE",
                            "POSAY",

                            "BODY",
                            "SHOP",

                            "BOTICARIO",

                            "COSMETICS",
                            "COSMETICOS",

                            "BEAUTY",

                            "LAB",
                            "LABS",

                            "PARIS",
                            "LONDON",
                            "YORK",

                            "THE",

                            "DE",
                            "LA",
                            "LE",
                            "LES",

                            "AND",

                            "CO",
                            "COMPANY",

                            "PROFESSIONAL",
                            "PROFISSIONAL"
                    )
            );

    /*
     * =========================================================
     * MÉTODO PRINCIPAL
     * =========================================================
     */

    @NonNull
    public static ScanFrontData extract(
            @NonNull List<String> linhas
    ) {

        List<String> brandCandidates =
                new ArrayList<>();

        List<String> productCandidates =
                new ArrayList<>();

        List<String> presentationCandidates =
                new ArrayList<>();

        String capacidade = "";
        String concentracao = "";

        if (linhas.isEmpty()) {

            return criarResultado(
                    brandCandidates,
                    productCandidates,
                    presentationCandidates,
                    capacidade,
                    concentracao
            );
        }

        /*
         * -----------------------------------------------------
         * NORMALIZA AS LINHAS SEM PERDER A ORDEM
         * -----------------------------------------------------
         */

        List<String> normalizadas =
                new ArrayList<>();

        for (String linha :
                linhas) {

            if (linha == null) {
                continue;
            }

            String normalizada =
                    normalizar(
                            linha
                    );

            if (normalizada.isEmpty()) {
                continue;
            }

            normalizadas.add(
                    normalizada
            );
        }

        /*
         * -----------------------------------------------------
         * PASSAGEM 1
         * -----------------------------------------------------
         */

        for (String linha :
                normalizadas) {

            if (capacidade.isEmpty()) {

                capacidade =
                        extrairCapacidade(
                                linha
                        );
            }

            if (concentracao.isEmpty()) {

                concentracao =
                        extrairConcentracao(
                                linha
                        );
            }

            extrairApresentacoes(
                    linha,
                    presentationCandidates
            );

            extrairDescritoresProduto(
                    linha,
                    productCandidates
            );
        }

        /*
         * -----------------------------------------------------
         * PASSAGEM 2
         * -----------------------------------------------------
         */

        for (String linha :
                normalizadas) {

            if (isSomenteNumero(
                    linha
            )) {

                continue;
            }

            adicionarTokensProduto(
                    linha,
                    productCandidates
            );

            if (linha.length() > 70) {
                continue;
            }

            if (isLinhaDeRuidoForte(
                    linha
            )) {

                continue;
            }

            String semNumeros =
                    removerDadosNumericos(
                            linha
                    );

            if (semNumeros.isEmpty()) {
                continue;
            }

            if (isFraseProdutoDistintiva(
                    semNumeros
            )) {

                adicionarProduto(
                        productCandidates,
                        semNumeros
                );
            }
        }

        /*
         * -----------------------------------------------------
         * PASSAGEM 3
         *
         * MARCA
         *
         * NÃO usamos break na primeira candidata.
         *
         * O OCR pode mostrar:
         *
         * NATIVA SPA
         * ...
         * OBOTICARIO
         *
         * A marca verdadeira pode ser a segunda.
         *
         * O catálogo é quem valida.
         * -----------------------------------------------------
         */

        int limiteMarca =
                Math.min(
                        normalizadas.size(),
                        12
                );

        for (int i = 0;
             i < limiteMarca;
             i++) {

            String linha =
                    normalizadas.get(i);

            if (linha.length() > 50) {
                continue;
            }

            if (isSomenteNumero(
                    linha
            )) {
                continue;
            }

            if (isLinhaDeRuidoForte(
                    linha
            )) {
                continue;
            }

            /*
             * Primeiro tenta a lógica original.
             */
            String semNumeros =
                    removerDadosNumericos(
                            linha
                    );

            if (!semNumeros.isEmpty()) {

                String possivelMarca =
                        extrairPossivelMarca(
                                semNumeros
                        );

                if (!possivelMarca.isEmpty()) {

                    String marcaCompleta =
                            completarMarcaEmLinhas(
                                    possivelMarca,
                                    normalizadas,
                                    i
                            );

                    if (!marcaCompleta.isEmpty()) {

                        adicionarMarca(
                                brandCandidates,
                                marcaCompleta
                        );
                    }
                }
            }

            /*
             * Segundo:
             *
             * tenta detectar marcas quebradas onde a primeira
             * palavra isoladamente não passaria pelo
             * "pareceMarca".
             *
             * Ex:
             *
             * O
             * BOTICARIO
             */
            String marcaComposta =
                    extrairMarcaCompostaNasLinhas(
                            normalizadas,
                            i
                    );

            if (!marcaComposta.isEmpty()) {

                adicionarMarca(
                        brandCandidates,
                        marcaComposta
                );
            }
        }

        /*
         * -----------------------------------------------------
         * GARANTIA FINAL DOS DESCRITORES
         * -----------------------------------------------------
         */

        adicionarDescritoresDetectados(
                normalizadas,
                productCandidates
        );

        /*
         * -----------------------------------------------------
         * EXTENSÃO GENERALISTA
         * -----------------------------------------------------
         */

        adicionarReferenciasExpandidas(
                normalizadas,
                productCandidates
        );

        extrairReferenciasNumericas(
                normalizadas,
                productCandidates
        );

        /*
         * -----------------------------------------------------
         * LIMPEZA FINAL
         * -----------------------------------------------------
         */

        limparMarcas(
                brandCandidates
        );

        limparProdutos(
                productCandidates
        );

        return criarResultado(
                brandCandidates,
                productCandidates,
                presentationCandidates,
                capacidade,
                concentracao
        );
    }

    /*
     * =========================================================
     * RESULTADO
     * =========================================================
     */

    @NonNull
    private static ScanFrontData criarResultado(
            @NonNull List<String> marcas,
            @NonNull List<String> produtos,
            @NonNull List<String> apresentacoes,
            String capacidade,
            String concentracao
    ) {

        return new ScanFrontData(
                marcas,
                produtos,
                apresentacoes,
                capacidade,
                concentracao
        );
    }

    /*
     * =========================================================
     * EXTRAÇÃO DE MARCA
     * =========================================================
     */

    @NonNull
    private static String extrairPossivelMarca(
            @NonNull String linha
    ) {

        String normalizada =
                normalizar(
                        linha
                );

        String[] tokens =
                normalizada.split(
                        "\\s+"
                );

        if (tokens.length == 0) {
            return "";
        }

        StringBuilder marca =
                new StringBuilder();

        for (String token :
                tokens) {

            if (token.isEmpty()) {
                continue;
            }

            /*
             * Ignora número no começo do OCR.
             */
            if (token.matches(
                    "\\d+"
            )) {

                if (marca.length() == 0) {
                    continue;
                }

                break;
            }

            /*
             * A partir daqui começou algo que claramente
             * parece produto/descritivo, não marca.
             */
            if (isBloqueadoComoMarca(
                    token
            )) {

                break;
            }

            if (token.length() < 2) {
                continue;
            }

            if (marca.length() > 0) {
                marca.append(' ');
            }

            marca.append(
                    token
            );

            if (marca.length() >= 30) {
                break;
            }
        }

        String resultado =
                marca
                        .toString()
                        .trim();

        if (!pareceMarca(
                resultado
        )) {

            return "";
        }

        return resultado;
    }

    /*
     * =========================================================
     * COMPLETAR MARCA
     * =========================================================
     *
     * Casos:
     *
     * VICTORIA
     * SECRET
     *
     * -> VICTORIA SECRET
     *
     * NATIVA
     * SPA
     *
     * -> NATIVA SPA
     *
     * Não é usado para decidir se a marca é verdadeira.
     * Apenas reconstrói texto.
     */
    @NonNull
    private static String completarMarcaEmLinhas(
            @NonNull String marcaInicial,
            @NonNull List<String> linhas,
            int indiceInicial
    ) {

        String marca =
                normalizar(
                        marcaInicial
                );

        if (marca.isEmpty()) {
            return "";
        }

        String[] tokensIniciais =
                marca.split(
                        "\\s+"
                );

        if (tokensIniciais.length >= 3) {
            return marca;
        }

        int palavrasAdicionadas = 0;

        for (int i = indiceInicial + 1;
             i < linhas.size()
                     && palavrasAdicionadas < 2;
             i++) {

            String proximaLinha =
                    normalizar(
                            linhas.get(i)
                    );

            if (proximaLinha.isEmpty()) {
                continue;
            }

            if (proximaLinha.length() > 25) {
                break;
            }

            if (isLinhaDeRuidoForte(
                    proximaLinha
            )) {
                break;
            }

            if (CAPACIDADE_PATTERN
                    .matcher(
                            proximaLinha
                    )
                    .find()
                    || CAPACIDADE_OCR_PREFIX_PATTERN
                    .matcher(
                            proximaLinha
                    )
                    .find()) {

                break;
            }

            if (CONCENTRACAO_PATTERN
                    .matcher(
                            proximaLinha
                    )
                    .find()) {

                break;
            }

            if (proximaLinha.matches(
                    ".*\\d{1,3}H.*"
            )) {

                break;
            }

            String[] tokens =
                    proximaLinha.split(
                            "\\s+"
                    );

            if (tokens.length == 0
                    || tokens.length > 2) {

                break;
            }

            boolean linhaValida =
                    true;

            StringBuilder continuacao =
                    new StringBuilder();

            for (String token :
                    tokens) {

                if (token.isEmpty()) {
                    continue;
                }

                if (!BRAND_CONTINUATION_WORDS.contains(
                        token
                )) {

                    linhaValida = false;
                    break;
                }

                if (isBloqueadoComoMarca(
                        token
                )
                        && !ehConectorDeMarca(
                        token
                )) {

                    linhaValida = false;
                    break;
                }

                if (continuacao.length() > 0) {
                    continuacao.append(' ');
                }

                continuacao.append(
                        token
                );
            }

            if (!linhaValida
                    || continuacao.length() == 0) {

                break;
            }

            String candidata =
                    marca
                            + " "
                            + continuacao;

            if (!marcaPodeSerMarcaComposta(
                    candidata
            )) {

                break;
            }

            marca =
                    candidata;

            palavrasAdicionadas +=
                    tokens.length;
        }

        return marca.trim();
    }

    /*
     * =========================================================
     * MARCA COMPOSTA INICIADA POR FRAGMENTO
     * =========================================================
     *
     * Resolve:
     *
     * O
     * BOTICARIO
     *
     * LA
     * ROCHE
     * POSAY
     *
     * THE
     * BODY
     * SHOP
     */
    @NonNull
    private static String extrairMarcaCompostaNasLinhas(
            @NonNull List<String> linhas,
            int indice
    ) {

        if (indice < 0
                || indice >= linhas.size()) {

            return "";
        }

        String atual =
                normalizar(
                        linhas.get(indice)
                );

        if (atual.isEmpty()) {
            return "";
        }

        String[] tokensAtuais =
                atual.split(
                        "\\s+"
                );

        /*
         * Caso já seja uma expressão de duas ou mais palavras,
         * ainda podemos verificar uma continuação conhecida.
         */
        if (tokensAtuais.length >= 2) {

            if (!todosPodemFazerParteDeMarca(
                    tokensAtuais
            )) {

                return "";
            }

            String resultado =
                    atual;

            int adicionadas = 0;

            for (int i = indice + 1;
                 i < linhas.size()
                         && adicionadas < 2;
                 i++) {

                String proxima =
                        normalizar(
                                linhas.get(i)
                        );

                if (proxima.isEmpty()) {
                    continue;
                }

                if (proxima.length() > 25) {
                    break;
                }

                String[] tokens =
                        proxima.split(
                                "\\s+"
                        );

                if (tokens.length != 1) {
                    break;
                }

                String token =
                        tokens[0];

                if (!BRAND_CONTINUATION_WORDS.contains(
                        token
                )) {

                    break;
                }

                if (isBloqueadoComoMarca(
                        token
                )
                        && !ehConectorDeMarca(
                        token
                )) {

                    break;
                }

                resultado =
                        resultado
                                + " "
                                + token;

                adicionadas++;
            }

            return resultado;
        }

        /*
         * -----------------------------------------------------
         * CASO DE UMA PALAVRA:
         *
         * VICTORIA
         * SECRET
         *
         * -----------------------------------------------------
         */

        if (tokensAtuais.length == 1
                && pareceMarca(atual)) {

            String completa =
                    completarMarcaEmLinhas(
                            atual,
                            linhas,
                            indice
                    );

            if (!completa.equals(atual)) {
                return completa;
            }
        }

        /*
         * -----------------------------------------------------
         * CASO DE PREFIXO:
         *
         * O
         * BOTICARIO
         *
         * LA
         * ROCHE
         * POSAY
         *
         * THE
         * BODY
         * SHOP
         * -----------------------------------------------------
         */

        if (!ehPrefixoDeMarca(atual)) {
            return "";
        }

        StringBuilder resultado =
                new StringBuilder(atual);

        int palavrasAdicionadas = 0;

        for (int i = indice + 1;
             i < linhas.size()
                     && palavrasAdicionadas < 3;
             i++) {

            String proxima =
                    normalizar(
                            linhas.get(i)
                    );

            if (proxima.isEmpty()) {
                continue;
            }

            if (proxima.length() > 25) {
                break;
            }

            if (isLinhaDeRuidoForte(
                    proxima
            )) {
                break;
            }

            if (CAPACIDADE_PATTERN
                    .matcher(
                            proxima
                    )
                    .find()
                    || CAPACIDADE_OCR_PREFIX_PATTERN
                    .matcher(
                            proxima
                    )
                    .find()) {

                break;
            }

            String[] tokens =
                    proxima.split(
                            "\\s+"
                    );

            /*
             * Para um fragmento inicial queremos uma palavra
             * por linha.
             */
            if (tokens.length != 1) {
                break;
            }

            String token =
                    tokens[0];

            if (isBloqueadoComoMarca(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {

                break;
            }

            /*
             * Se a palavra não é permitida como continuação,
             * paramos imediatamente.
             */
            if (!BRAND_CONTINUATION_WORDS.contains(
                    token
            )) {

                break;
            }

            resultado.append(' ')
                    .append(token);

            palavrasAdicionadas++;
        }

        String candidata =
                resultado
                        .toString()
                        .trim();

        /*
         * Uma marca composta válida precisa ter pelo menos
         * duas palavras.
         */
        if (candidata.equals(atual)) {
            return "";
        }

        if (!marcaPodeSerMarcaComposta(
                candidata
        )) {

            return "";
        }

        return candidata;
    }

    /*
     * =========================================================
     * VALIDAÇÃO DE MARCA COMPOSTA
     * =========================================================
     */

    private static boolean marcaPodeSerMarcaComposta(
            @NonNull String texto
    ) {

        String normalizada =
                normalizar(
                        texto
                );

        if (normalizada.length() < 4
                || normalizada.length() > 40) {

            return false;
        }

        String[] tokens =
                normalizada.split(
                        "\\s+"
                );

        if (tokens.length > 4) {
            return false;
        }

        for (String token :
                tokens) {

            if (token.length() < 2
                    && !ehConectorDeMarca(
                    token
            )) {

                return false;
            }

            /*
             * Variantes de produto não fazem parte da marca.
             *
             * Ex:
             *
             * ABOVE EXTREME
             *
             * não pode virar uma marca composta.
             */
            if (NON_BRAND_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {

                return false;
            }

            if (NOISE_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {

                return false;
            }

            if (token.matches(
                    "\\d+"
            )) {

                return false;
            }

            if (token.matches(
                    "\\d{1,3}H"
            )) {

                return false;
            }
        }

        return possuiLetras(
                normalizada
        );
    }

    private static boolean todosPodemFazerParteDeMarca(
            @NonNull String[] tokens
    ) {

        if (tokens.length == 0
                || tokens.length > 4) {

            return false;
        }

        for (String token :
                tokens) {

            if (token.isEmpty()) {
                return false;
            }

            if (NON_BRAND_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {

                return false;
            }

            if (NOISE_WORDS.contains(
                    token
            )
                    && !ehConectorDeMarca(
                    token
            )) {

                return false;
            }

            if (token.matches(
                    "\\d+"
            )
                    || token.matches(
                    "\\d{1,3}H"
            )) {

                return false;
            }
        }

        return true;
    }

    /*
     * =========================================================
     * PREFIXOS DE MARCAS
     * =========================================================
     */

    private static boolean ehPrefixoDeMarca(
            @NonNull String token
    ) {

        return token.equals("O")
                || token.equals("THE")
                || token.equals("LA")
                || token.equals("LE")
                || token.equals("DE");
    }

    /*
     * =========================================================
     * CONECTORES DE MARCA
     * =========================================================
     */

    private static boolean ehConectorDeMarca(
            @NonNull String token
    ) {

        return token.equals("O")
                || token.equals("THE")
                || token.equals("DE")
                || token.equals("LA")
                || token.equals("LE")
                || token.equals("LES")
                || token.equals("AND")
                || token.equals("CO");
    }

    /*
     * =========================================================
     * MARCA
     * =========================================================
     */

    private static boolean pareceMarca(
            String texto
    ) {

        if (texto == null) {
            return false;
        }

        String normalizada =
                normalizar(
                        texto
                );

        if (normalizada.length() < 4
                || normalizada.length() > 30) {

            return false;
        }

        if (isSomenteNumero(
                normalizada
        )) {

            return false;
        }

        if (!possuiLetras(
                normalizada
        )) {

            return false;
        }

        String[] tokens =
                normalizada.split(
                        "\\s+"
                );

        for (String token :
                tokens) {

            if (isBloqueadoComoMarca(
                    token
            )) {

                return false;
            }
        }

        return true;
    }

    private static boolean isBloqueadoComoMarca(
            @NonNull String token
    ) {

        if (token.matches(
                "\\d{1,3}H"
        )) {

            return true;
        }

        if (NON_BRAND_WORDS.contains(
                token
        )) {

            return true;
        }

        if (NOISE_WORDS.contains(
                token
        )) {

            return true;
        }

        if (isReferenciaGenericaCosmetica(
                token
        )) {

            return true;
        }

        if (isDescritorAntitranspirante(
                token
        )) {

            return true;
        }

        return isDescritorAntiperspirante(
                token
        );
    }

    private static void adicionarMarca(
            @NonNull List<String> destino,
            String valor
    ) {

        if (valor == null) {
            return;
        }

        String normalizado =
                normalizar(
                        valor
                );

        if (!pareceMarca(
                normalizado
        )
                && !marcaPodeSerMarcaComposta(
                normalizado
        )) {

            return;
        }

        adicionarUnico(
                destino,
                normalizado
        );
    }

    /*
     * =========================================================
     * REFERÊNCIA GENÉRICA DE COSMÉTICO
     * =========================================================
     */

    private static boolean isReferenciaGenericaCosmetica(
            @NonNull String token
    ) {

        String valor =
                normalizar(
                        token
                );

        switch (valor) {

            case "SHAMPOO":

            case "CONDICIONADOR":
            case "CONDITIONER":

            case "HIDRATANTE":

            case "CLEANSER":

            case "TONICO":
            case "TONER":

            case "SERUM":

            case "ESFOLIANTE":

            case "PRIMER":

            case "PROTETOR":

            case "SOLAR":

            case "SUNSCREEN":

            case "DESODORANTE":
            case "DEODORANT":

            case "SABONETE":
            case "SOAP":

            case "PERFUME":
            case "PARFUM":
            case "FRAGRANCE":

            case "COLONIA":
            case "COLOGNE":

            case "BASE":
            case "FOUNDATION":

            case "CORRETIVO":
            case "CONCEALER":

            case "BATOM":
            case "LIPSTICK":

            case "GLOSS":

            case "BLUSH":
            case "BRONZER":

            case "ILUMINADOR":
            case "HIGHLIGHTER":

            case "DELINEADOR":
            case "EYELINER":

            case "SOMBRA":
            case "EYESHADOW":

            case "ESMALTE":
            case "REMOVEDOR":

            case "SPRAY":
            case "AEROSOL":

            case "CREME":
            case "CREAM":

            case "LOCAO":
            case "LOTION":

            case "GEL":

            case "ESPUMA":
            case "FOAM":

            case "OLEO":
            case "OIL":

                return true;

            default:

                return false;
        }
    }

    /*
     * =========================================================
     * PRODUTO
     * =========================================================
     */

    private static void adicionarTokensProduto(
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

    private static void limparProdutos(
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

    private static boolean isFraseProdutoDistintiva(
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

    private static void extrairDescritoresProduto(
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

    private static void adicionarDescritoresDetectados(
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

    private static boolean contemAntitranspirante(
            @NonNull String linha
    ) {

        String compacta =
                linha
                        .replaceAll(
                                "[^A-Z0-9]",
                                ""
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (compacta.contains(
                "ANTITRANSPIR"
        )) {

            return true;
        }

        return procurarTokenParecido(
                linha,
                ANTITRANSPIRANTE
        );
    }

    private static boolean contemAntiperspirante(
            @NonNull String linha
    ) {

        String compacta =
                linha
                        .replaceAll(
                                "[^A-Z0-9]",
                                ""
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (compacta.contains(
                "ANTIPERSPIR"
        )) {

            return true;
        }

        return procurarTokenParecido(
                linha,
                ANTIPERSPIRANTE
        );
    }

    private static boolean procurarTokenParecido(
            @NonNull String linha,
            @NonNull String esperado
    ) {

        String[] tokens =
                linha.split(
                        "\\s+"
                );

        for (String token :
                tokens) {

            String normalizado =
                    normalizar(
                            token
                    );

            if (normalizado.length() < 8) {
                continue;
            }

            double score =
                    similaridade(
                            normalizado,
                            esperado
                    );

            if (score >= 0.75) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDescritorAntitranspirante(
            @NonNull String texto
    ) {

        String valor =
                normalizar(
                        texto
                );

        return valor.equals(
                ANTITRANSPIRANTE
        )
                || valor.equals(
                "ANTITRANSPIRANT"
        )
                || valor.equals(
                "ANTITRANSPIRAN"
        )
                || valor.equals(
                "ANTITRANSPIRANI"
        )
                || valor.startsWith(
                "ANTITRANSPIR"
        );
    }

    private static boolean isDescritorAntiperspirante(
            @NonNull String texto
    ) {

        String valor =
                normalizar(
                        texto
                );

        return valor.equals(
                ANTIPERSPIRANTE
        )
                || valor.equals(
                "ANTIPERSPIRANT"
        )
                || valor.equals(
                "ANTIPERSPIRAN"
        )
                || valor.equals(
                "ANTIPERSPIRANI"
        )
                || valor.startsWith(
                "ANTIPERSPIR"
        );
    }

    /*
     * =========================================================
     * EXTENSÃO GENERALISTA
     * =========================================================
     */

    private static void adicionarReferenciasExpandidas(
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

    private static void extrairReferenciasNumericas(
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
     * TERMOS EXATOS
     * =========================================================
     */

    private static boolean contemTermo(
            @NonNull String texto,
            @NonNull String termo
    ) {

        String textoNormalizado =
                normalizar(
                        texto
                );

        String termoNormalizado =
                normalizar(
                        termo
                );

        if (textoNormalizado.isEmpty()
                || termoNormalizado.isEmpty()) {

            return false;
        }

        String textoComEspacos =
                " "
                        + textoNormalizado
                        + " ";

        String termoComEspacos =
                " "
                        + termoNormalizado
                        + " ";

        return textoComEspacos.contains(
                termoComEspacos
        );
    }

    /*
     * =========================================================
     * CAPACIDADE
     * =========================================================
     */

    @NonNull
    private static String extrairCapacidade(
            @NonNull String texto
    ) {

        /*
         * Padrão normal:
         *
         * 250 ML
         * 200 G
         * 1 L
         */
        Matcher matcher =
                CAPACIDADE_PATTERN.matcher(
                        texto
                );

        if (matcher.find()) {

            String numero =
                    matcher.group(1);

            String unidade =
                    matcher.group(2);

            if (numero != null
                    && unidade != null) {

                unidade =
                        unidade
                                .toUpperCase(
                                        Locale.ROOT
                                )
                                .replace(
                                        "M1",
                                        "ML"
                                )
                                .replace(
                                        "ML1",
                                        "ML"
                                );

                return numero
                        + " "
                        + unidade;
            }
        }

        /*
         * Tolerância OCR:
         *
         * E250 ML
         * I250 ML
         * O250 ML
         */
        Matcher ocrMatcher =
                CAPACIDADE_OCR_PREFIX_PATTERN.matcher(
                        texto
                );

        if (ocrMatcher.find()) {

            String numero =
                    ocrMatcher.group(1);

            String unidade =
                    ocrMatcher.group(2);

            if (numero != null
                    && unidade != null) {

                unidade =
                        unidade
                                .toUpperCase(
                                        Locale.ROOT
                                )
                                .replace(
                                        "M1",
                                        "ML"
                                )
                                .replace(
                                        "ML1",
                                        "ML"
                                );

                return numero
                        + " "
                        + unidade;
            }
        }

        return "";
    }

    /*
     * =========================================================
     * CONCENTRAÇÃO
     * =========================================================
     */

    @NonNull
    private static String extrairConcentracao(
            @NonNull String texto
    ) {

        Matcher matcher =
                CONCENTRACAO_PATTERN.matcher(
                        texto
                );

        if (!matcher.find()) {
            return "";
        }

        String numero =
                matcher.group(1);

        if (numero == null
                || numero.isEmpty()) {

            return "";
        }

        return numero
                + "%";
    }

    /*
     * =========================================================
     * APRESENTAÇÕES
     * =========================================================
     */

    private static void extrairApresentacoes(
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

    /*
     * =========================================================
     * RUÍDO FORTE
     * =========================================================
     */

    private static boolean isLinhaDeRuidoForte(
            @NonNull String linha
    ) {

        String texto =
                normalizar(
                        linha
                );

        if (texto.contains(
                "PRECAU"
        )) {
            return true;
        }

        if (texto.contains(
                "MODO DE USO"
        )) {
            return true;
        }

        if (texto.contains(
                "APLICAR O PRODUTO"
        )) {
            return true;
        }

        if (texto.contains(
                "MANTER FORA"
        )) {
            return true;
        }

        if (texto.contains(
                "NAO USAR"
        )) {
            return true;
        }

        if (texto.contains(
                "EVITAR A INALACAO"
        )) {
            return true;
        }

        if (texto.contains(
                "PROTEGER OS OLHOS"
        )) {
            return true;
        }

        if (texto.contains(
                "EM CASO DE"
        )) {
            return true;
        }

        if (texto.contains(
                "INGREDIENTES"
        )) {
            return true;
        }

        if (texto.contains(
                "INGREDIENTS"
        )) {
            return true;
        }

        return false;
    }

    /*
     * =========================================================
     * LIMPEZA NUMÉRICA
     * =========================================================
     */

    @NonNull
    private static String removerDadosNumericos(
            @NonNull String texto
    ) {

        String resultado =
                CAPACIDADE_PATTERN
                        .matcher(
                                texto
                        )
                        .replaceAll(
                                " "
                        );

        /*
         * Remove também:
         *
         * E250 ML
         * I250 ML
         * O250 ML
         */
        resultado =
                CAPACIDADE_OCR_PREFIX_PATTERN
                        .matcher(
                                resultado
                        )
                        .replaceAll(
                                " "
                        );

        /*
         * Remove:
         *
         * 84 FLOZ
         * 84FLOZ
         * 8.4 FL OZ
         */
        resultado =
                FL_OZ_PATTERN
                        .matcher(
                                resultado
                        )
                        .replaceAll(
                                " "
                        );

        resultado =
                CONCENTRACAO_PATTERN
                        .matcher(
                                resultado
                        )
                        .replaceAll(
                                " "
                        );

        /*
         * 72 H -> 72H
         *
         * Continua válido como evidência do produto.
         */
        Matcher matcher =
                HORAS_PATTERN.matcher(
                        resultado
                );

        StringBuffer buffer =
                new StringBuffer();

        while (matcher.find()) {

            String numero =
                    matcher.group(1);

            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(
                            numero + "H"
                    )
            );
        }

        matcher.appendTail(
                buffer
        );

        return normalizar(
                buffer.toString()
        );
    }

    /*
     * =========================================================
     * LIMPEZA FINAL DA MARCA
     * =========================================================
     */

    private static void limparMarcas(
            @NonNull List<String> marcas
    ) {

        List<String> resultado =
                new ArrayList<>();

        for (String marca :
                marcas) {

            if (marca == null) {
                continue;
            }

            String normalizado =
                    normalizar(
                            marca
                    );

            if (pareceMarca(
                    normalizado
            )
                    || marcaPodeSerMarcaComposta(
                    normalizado
            )) {

                adicionarUnico(
                        resultado,
                        normalizado
                );
            }
        }

        marcas.clear();

        marcas.addAll(
                resultado
        );
    }

    /*
     * =========================================================
     * NORMALIZAÇÃO
     * =========================================================
     */

    @NonNull
    private static String normalizar(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        String resultado =
                ScanTextNormalizer.normalize(
                        texto
                );

        resultado =
                resultado
                        .replaceAll(
                                "[^A-Z0-9 ]",
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        /*
         * 72 H -> 72H
         */
        Matcher matcher =
                Pattern.compile(
                        "(?i)\\b(\\d{1,3})\\s+H\\b"
                ).matcher(
                        resultado
                );

        StringBuffer buffer =
                new StringBuffer();

        while (matcher.find()) {

            String numero =
                    matcher.group(1);

            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(
                            numero + "H"
                    )
            );
        }

        matcher.appendTail(
                buffer
        );

        return buffer
                .toString()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    /*
     * =========================================================
     * UTILITÁRIOS
     * =========================================================
     */

    private static void adicionarProduto(
            @NonNull List<String> destino,
            String valor
    ) {

        if (valor == null) {
            return;
        }

        String normalizado =
                normalizar(
                        valor
                );

        if (normalizado.isEmpty()) {
            return;
        }

        if (!possuiLetras(
                normalizado
        )) {

            return;
        }

        adicionarUnico(
                destino,
                normalizado
        );
    }

    private static void adicionarUnico(
            @NonNull List<String> destino,
            String valor
    ) {

        if (valor == null) {
            return;
        }

        String normalizado =
                normalizar(
                        valor
                );

        if (normalizado.isEmpty()) {
            return;
        }

        if (!destino.contains(
                normalizado
        )) {

            destino.add(
                    normalizado
            );
        }
    }

    private static boolean isSomenteNumero(
            @NonNull String texto
    ) {

        return texto.matches(
                "\\d+(?:[.,]\\d+)?"
        );
    }

    private static boolean possuiLetras(
            @NonNull String texto
    ) {

        for (int i = 0;
             i < texto.length();
             i++) {

            if (Character.isLetter(
                    texto.charAt(i)
            )) {

                return true;
            }
        }

        return false;
    }

    /*
     * =========================================================
     * SIMILARIDADE
     * =========================================================
     */

    private static double similaridade(
            @NonNull String a,
            @NonNull String b
    ) {

        if (a.equals(
                b
        )) {

            return 1.0;
        }

        int maior =
                Math.max(
                        a.length(),
                        b.length()
                );

        if (maior == 0) {
            return 1.0;
        }

        int distancia =
                levenshtein(
                        a,
                        b
                );

        return 1.0
                - (
                (double)
                        distancia
                        / maior
        );
    }

    private static int levenshtein(
            @NonNull String a,
            @NonNull String b
    ) {

        int[] anterior =
                new int[
                        b.length() + 1
                        ];

        int[] atual =
                new int[
                        b.length() + 1
                        ];

        for (int j = 0;
             j <= b.length();
             j++) {

            anterior[j] =
                    j;
        }

        for (int i = 1;
             i <= a.length();
             i++) {

            atual[0] =
                    i;

            for (int j = 1;
                 j <= b.length();
                 j++) {

                int custo =
                        a.charAt(
                                i - 1
                        )
                                == b.charAt(
                                j - 1
                        )
                                ? 0
                                : 1;

                atual[j] =
                        Math.min(
                                Math.min(
                                        atual[j - 1] + 1,
                                        anterior[j] + 1
                                ),
                                anterior[j - 1]
                                        + custo
                        );
            }

            int[] temp =
                    anterior;

            anterior =
                    atual;

            atual =
                    temp;
        }

        return anterior[
                b.length()
                ];
    }
}