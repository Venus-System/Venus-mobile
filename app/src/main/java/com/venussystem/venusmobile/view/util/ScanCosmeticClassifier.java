package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;

import com.venussystem.venusmobile.model.ScanCosmeticClassification;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Classificador frontal de produtos para o fluxo de novo produto.
 *
 * REGRA DE NEGÓCIO:
 *
 *   produto NÃO encontrado no catálogo
 *             +
 *   evidência suficiente de cosmético
 *             ↓
 *   COSMETIC_CONFIRMED
 *             ↓
 *   captura do verso
 *
 * O classificador foi desenhado para maximizar a cobertura de cosméticos
 * sem permitir que palavras genéricas como CREME, GEL, OIL, BODY, BEAUTY
 * ou FRAGRANCE confirmem sozinhas um produto.
 *
 * 4 níveis de evidência:
 *
 * NÍVEL 1 - CATEGORIA EXPLÍCITA
 *   Shampoo, condicionador, sabonete, desodorante, creme para pentear,
 *   hidratante, perfume, protetor solar, maquiagem etc.
 *
 * NÍVEL 2 - CONTEXTO DE APLICAÇÃO
 *   cabelo, pele, rosto, corpo, axilas, barba, lábios, unhas,
 *   higiene pessoal, cuidados da pele/cabelo etc.
 *
 * NÍVEL 3 - ESTRUTURA DE PRODUTO / RÓTULO
 *   marca + produto, capacidade, apresentação, ingredientes/INCI,
 *   modo de uso, cuidados/advertências, fragrância/perfume etc.
 *
 * NÍVEL 4 - CLAIMS / COMPOSIÇÃO / VOCABULÁRIO COSMÉTICO
 *   vegan, cruelty free, dermatologicamente testado, sulfate free,
 *   moisturizing, nourishing, antiperspirant etc., além de palavras
 *   típicas de formulação cosmética.
 *
 * Importante:
 * - Os quatro níveis podem participar da confirmação.
 * - Não é obrigatório ter os quatro níveis para confirmar um cosmético.
 * - Um termo genérico isolado NÃO confirma.
 * - Categoria de alta precisão pode confirmar com estrutura mínima.
 * - Categoria de precisão média precisa de apoio contextual/estrutural.
 * - Conflitos fortes com não cosmético geram UNCERTAIN ou NON_COSMETIC.
 * - Texto de IDE/Logcat/tela contaminada gera UNCERTAIN.
 */
public final class ScanCosmeticClassifier {

    private static final String TAG = "VENUS_COSMETIC";

    /*
     * Threshold principal.
     *
     * Mantemos um threshold alto, mas a regra de confirmação ficou mais
     * inteligente: em vez de depender apenas do score, ela usa a combinação
     * dos quatro níveis.
     */
    private static final double SCORE_CONFIRMED_MIN = 0.78;
    private static final double SCORE_UNCERTAIN_MAX = 0.77;

    /*
     * Distância fuzzy controlada.
     * Não usamos fuzzy para termos muito curtos.
     */
    private static final int FUZZY_MAX_DISTANCE_SHORT = 1;
    private static final int FUZZY_MAX_DISTANCE_MEDIUM = 1;
    private static final int FUZZY_MAX_DISTANCE_LONG = 2;

    private static final Set<String> STOPWORDS =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "DE", "DA", "DO", "DAS", "DOS",
                            "EM", "NO", "NA", "NOS", "NAS",
                            "PARA", "POR", "COM", "SEM",
                            "E", "OU", "A", "O", "AS", "OS",
                            "UM", "UMA", "UN",
                            "MEN", "WOMEN", "MAN", "WOMAN",
                            "FOR", "THE", "AND", "OF"
                    )
            );

    /**
     * Marcas conhecidas por serem de cosméticos/cuidado pessoal.
     *
     * Não são requisito para classificação. Servem para situações comuns em
     * que a frente possui apenas "MARCA + CREME" ou "MARCA + CUIDADO".
     *
     * A lista é conservadora e pode ser ampliada depois com base no catálogo.
     */
    private static final Set<String> MARCAS_COSMETICAS_CONHECIDAS =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "CERAVE",
                            "LA ROCHE POSAY",
                            "LAROCHEPOSAY",
                            "EUCERIN",
                            "VICHY",
                            "LOLA COSMETICS",
                            "LOLA",
                            "SKALA",
                            "NATURA",
                            "O BOTICARIO",
                            "OBOTICARIO",
                            "NIVEA",
                            "NEUTROGENA",
                            "SKINCEUTICALS",
                            "KERASTASE",
                            "PANTENE",
                            "GRANADO",
                            "RISQUE",
                            "VULT",
                            "MAYBELLINE",
                            "DERMACYD",
                            "REXONA",
                            "DOVE",
                            "CETAPHIL",
                            "BIOSSANCE",
                            "THE BODY SHOP",
                            "EUDORA",
                            "CICLO COSMETICOS",
                            "AVENE",
                            "BIODERMA",
                            "LACTRE",
                            "PAYOT",
                            "TRUSS",
                            "WELLA",
                            "LORÉAL",
                            "LOREAL",
                            "GARNIER",
                            "ELSEVE",
                            "ELSEV",
                            "HEAD SHOULDERS",
                            "CLEAR",
                            "TRESEMME",
                            "TRESEMMÉ",
                            "JOHNSONS",
                            "JOHNSON JOHNSON",
                            "MAMAE BABY",
                            "MONANGE",
                            "MONANGE",
                            "HERBAL ESSENCES",
                            "SALON LINE",
                            "SALONLINE",
                            "EMBELLEZE",
                            "NOVEX",
                            "BIO EXTRATUS",
                            "BIOEXTRATUS",
                            "INOAR",
                            "KANECHOM",
                            "DARROW",
                            "ADCOS",
                            "EPIDU",
                            "PROTEX",
                            "LUX",
                            "PALMOLIVE"
                    )
            );

    private ScanCosmeticClassifier() {
    }

    @NonNull
    public static ScanCosmeticClassification classify(
            @NonNull ScanOcrResult ocr,
            @NonNull ScanFrontData frontData
    ) {

        String texto = montarTextoCompleto(ocr, frontData);

        Set<String> evidencias =
                new LinkedHashSet<>();

        Set<String> tipos =
                new LinkedHashSet<>();

        /*
         * =============================================================
         * 4 NÍVEIS DE EVIDÊNCIA
         * =============================================================
         */
        Set<String> nivel1Categoria =
                new LinkedHashSet<>();

        Set<String> nivel2Contexto =
                new LinkedHashSet<>();

        Set<String> nivel3Estrutura =
                new LinkedHashSet<>();

        Set<String> nivel4Apoio =
                new LinkedHashSet<>();

        /*
         * =============================================================
         * NÃO COSMÉTICOS
         * =============================================================
         */
        Set<String> sinaisNaoCosmeticos =
                new LinkedHashSet<>();

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "MEDICAMENTO", "MEDICAMENT", "MEDICINE",
                "DRUG", "PHARMACEUTICAL", "FARMACO", "FÁRMACO",
                "REMEDIO", "REMÉDIO", "MEDICAL", "MEDICINAL"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "COMPRIMIDO", "TABLET", "CAPSULA", "CÁPSULA",
                "CAPSULE", "XAROPE", "SYRUP", "INJETAVEL",
                "INJECTABLE", "AMPOLA", "VIAL", "DOSAGEM",
                "DOSE", "MG POR COMPRIMIDO"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "ANTIBIOTICO", "ANTIBIÓTICO", "ANTIBIOTIC",
                "ANALGESICO", "ANALGÉSICO", "ANALGESIC",
                "ANTI INFLAMATORIO", "ANTI-INFLAMMATORY",
                "ANTIFUNGAL", "ANTIVIRAL", "ANTICONCEPCIONAL",
                "ANTIHISTAMINICO", "ANTIHISTAMINIC"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "SUPLEMENTO", "SUPPLEMENT",
                "VITAMINA", "VITAMIN", "MINERAL SUPPLEMENT",
                "PROTEINA EM PO", "PROTEIN POWDER",
                "SHAKE PROTEICO", "PROTEIN SHAKE", "CREATINA",
                "WHEY", "BCAA", "AMINO ACID"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "ALIMENTO", "FOOD", "BEBIDA", "BEVERAGE",
                "REFRIGERANTE", "SODA", "SUCO", "JUICE",
                "CAFE", "COFFEE", "CHOCOLATE",
                "BISCOITO", "COOKIE", "CEREAL", "LEITE",
                "MILK", "IOGURTE", "YOGURT", "QUEIJO", "CHEESE",
                "BARRA DE CEREAL", "SNACK", "NUTRIÇÃO"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "DETERGENTE", "DETERGENT",
                "DESINFETANTE", "DISINFECTANT",
                "AMACIANTE", "FABRIC SOFTENER",
                "ALVEJANTE", "BLEACH",
                "LIMPADOR DOMESTICO", "HOUSEHOLD CLEANER",
                "LIMPA VIDROS", "GLASS CLEANER",
                "DESENGORDURANTE", "DEGREASER",
                "LAVANDERIA", "LAUNDRY DETERGENT",
                "SABAO EM PO", "LAUNDRY SOAP",
                "LIMPA PISO", "FLOOR CLEANER",
                "LIMPA FORNO", "OVEN CLEANER",
                "LIMPA BANHEIRO", "BATHROOM CLEANER"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "AUTOMOTIVO", "AUTOMOTIVE",
                "MOTOR", "ENGINE",
                "OLEO MOTOR", "MOTOR OIL",
                "FLUIDO DE FREIO", "BRAKE FLUID",
                "LUBRIFICANTE INDUSTRIAL", "INDUSTRIAL LUBRICANT",
                "POLIDOR AUTOMOTIVO", "CAR POLISH",
                "SHAMPOO AUTOMOTIVO", "CAR SHAMPOO",
                "CERA AUTOMOTIVA", "CAR WAX"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "ELETRONICO", "ELECTRONIC",
                "CELULAR", "SMARTPHONE", "NOTEBOOK", "COMPUTER",
                "TABLET PC", "CHARGER", "CARREGADOR",
                "CABO USB", "USB CABLE",
                "BLUETOOTH", "WIFI", "HEADPHONE",
                "FONE DE OUVIDO", "MONITOR", "TELEVISAO", "TELEVISION"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "TINTA", "PAINT",
                "SOLVENTE INDUSTRIAL", "INDUSTRIAL SOLVENT",
                "INDUSTRIAL CHEMICAL", "ADHESIVE",
                "COLA INDUSTRIAL", "RESINA", "RESIN",
                "CIMENTO", "CEMENT"
        );

        detectarNaoCosmetico(
                texto,
                sinaisNaoCosmeticos,
                "INSETICIDA", "INSECTICIDE",
                "HERBICIDA", "HERBICIDE",
                "FUNGICIDA AGRICOLA", "PESTICIDE",
                "FERTILIZANTE", "FERTILIZER", "VENENO"
        );

        /*
         * =============================================================
         * NÍVEL 1 — CATEGORIAS EXPLÍCITAS
         * =============================================================
         */

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "DESODORANTE",
                        "DESODORANTE",
                        "desodorante/higiene pessoal",
                        true,
                        "DESODORANTE", "DEODORANT", "DEODOR"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "ANTITRANSPIRANTE",
                        "ANTITRANSPIRANTE",
                        "desodorante/higiene pessoal",
                        true,
                        "ANTITRANSPIRANTE", "ANTIPERSPIRANTE",
                        "ANTITRANSPIRANT", "ANTIPERSPIRANT"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SABONETE",
                        "SABONETE",
                        "higiene pessoal",
                        true,
                        "SABONETE", "SABONET",
                        "BODY SOAP", "TOILET SOAP", "FACIAL SOAP",
                        "HAND SOAP"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "BODY_WASH",
                        "BODY WASH",
                        "higiene pessoal",
                        true,
                        "BODY WASH", "SHOWER GEL", "SHOWERGEL",
                        "SHOWER SOAP", "GEL DE BANHO", "GEL DE DUCHE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SABONETE_LIQUIDO",
                        "SABONETE LÍQUIDO",
                        "higiene pessoal",
                        true,
                        "SABONETE LIQUIDO", "SABONETE LÍQUIDO",
                        "LIQUID SOAP", "LIQUID BODY SOAP"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "HIGIENE_INTIMA",
                        "HIGIENE ÍNTIMA",
                        "higiene pessoal",
                        true,
                        "HIGIENE INTIMA", "HIGIENE ÍNTIMA",
                        "INTIMATE HYGIENE", "INTIMATE WASH",
                        "SABONETE INTIMO", "SABONETE ÍNTIMO"
                )
        );

        /*
         * Cabelos
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SHAMPOO",
                        "SHAMPOO",
                        "cabelo",
                        true,
                        "SHAMPOO", "SHAMPO", "SHAMP00",
                        "SHAMP0O", "SHAMPOOING", "XAMPOO"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CONDICIONADOR",
                        "CONDICIONADOR",
                        "cabelo",
                        true,
                        "CONDICIONADOR", "CONDICIONAD", "CONDICIONA",
                        "CONDITIONER", "CONDITIONING",
                        "HAIR CONDITIONER", "HAIR CONDITION"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "MASCARA_CAPILAR",
                        "MÁSCARA CAPILAR",
                        "cabelo",
                        true,
                        "MASCARA CAPILAR", "MÁSCARA CAPILAR",
                        "MASCARA PARA CABELO",
                        "HAIR MASK", "MASK FOR HAIR"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CREME_PARA_PENTEAR",
                        "CREME PARA PENTEAR",
                        "cabelo",
                        true,
                        "CREME PARA PENTEAR",
                        "CREME PENTEAR",
                        "CREME DE PENTEAR",
                        "CREME PARA CABELO",
                        "LEAVE IN", "LEAVE-IN", "LEAVEIN",
                        "LEAVE IN CONDITIONER",
                        "HAIR LEAVE IN"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "TRATAMENTO_CAPILAR",
                        "TRATAMENTO CAPILAR",
                        "cabelo",
                        true,
                        "TRATAMENTO CAPILAR", "HAIR TREATMENT",
                        "HAIR CARE TREATMENT", "TRATAMIENTO CAPILAR"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "OLEO_CAPILAR",
                        "ÓLEO CAPILAR",
                        "cabelo",
                        true,
                        "OLEO CAPILAR", "ÓLEO CAPILAR",
                        "OLEO PARA CABELO", "HAIR OIL",
                        "HAIR TREATMENT OIL", "HAIR OIL TREATMENT"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "GEL_CAPILAR",
                        "GEL CAPILAR",
                        "cabelo",
                        true,
                        "GEL CAPILAR", "GEL PARA CABELO",
                        "HAIR GEL", "STYLING GEL"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "MOUSSE_CAPILAR",
                        "MOUSSE CAPILAR",
                        "cabelo",
                        true,
                        "MOUSSE CAPILAR", "HAIR MOUSSE",
                        "STYLING MOUSSE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CERA_CAPILAR",
                        "CERA CAPILAR",
                        "cabelo",
                        true,
                        "CERA PARA CABELO", "CERA CAPILAR",
                        "HAIR WAX", "STYLING WAX"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SPRAY_CAPILAR",
                        "SPRAY CAPILAR",
                        "cabelo",
                        true,
                        "SPRAY CAPILAR", "HAIR SPRAY",
                        "HAIRSPRAY", "STYLING SPRAY"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SHAMPOO_SECO",
                        "SHAMPOO SECO",
                        "cabelo",
                        true,
                        "SHAMPOO SECO", "DRY SHAMPOO"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "COLORACAO_CAPILAR",
                        "COLORAÇÃO CAPILAR",
                        "cabelo",
                        true,
                        "COLORACAO CAPILAR", "COLORAÇÃO CAPILAR",
                        "TINTURA CAPILAR", "HAIR COLOR",
                        "HAIR COLOUR", "HAIR DYE",
                        "HAIR COLOURING"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "DESCOLORANTE_CAPILAR",
                        "DESCOLORANTE CAPILAR",
                        "cabelo",
                        true,
                        "DESCOLORANTE CAPILAR", "HAIR BLEACH",
                        "BLEACH FOR HAIR"
                )
        );

        /*
         * Pele / rosto / corpo
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "HIDRATANTE",
                        "HIDRATANTE",
                        "pele",
                        true,
                        "HIDRATANTE", "HIDRATANT",
                        "HIDRATACAO", "HIDRATAÇÃO",
                        "MOISTURIZER", "MOISTURISER",
                        "MOISTURIZING", "MOISTURIZING CREAM"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CREME_FACIAL",
                        "CREME FACIAL",
                        "pele",
                        true,
                        "CREME FACIAL", "FACE CREAM",
                        "FACIAL CREAM", "FACIAL MOISTURIZER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "LOCAO_CORPORAL",
                        "LOÇÃO CORPORAL",
                        "pele",
                        true,
                        "LOCAO CORPORAL", "LOÇÃO CORPORAL",
                        "BODY LOTION", "BODY CREAM",
                        "BODY MILK", "BODY MOISTURIZER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CREME_MAOS",
                        "CREME PARA AS MÃOS",
                        "pele",
                        true,
                        "CREME PARA AS MAOS", "CREME PARA AS MÃOS",
                        "HAND CREAM", "HAND LOTION",
                        "HAND MOISTURIZER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "LIMPEZA_FACIAL",
                        "LIMPEZA FACIAL",
                        "pele",
                        true,
                        "FACIAL CLEANSER", "FACIAL CLEANER",
                        "FACE CLEANSER", "FACE WASH",
                        "GEL DE LIMPEZA", "GEL LIMPEZA",
                        "SABONETE FACIAL", "LIMPEZA FACIAL"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SERUM",
                        "SÉRUM",
                        "pele",
                        true,
                        "SERUM", "SÉRUM", "SERUN", "SEROM",
                        "FACE SERUM", "SKIN SERUM"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "TONICO",
                        "TÔNICO",
                        "pele",
                        true,
                        "TONICO", "TÔNICO", "TONER",
                        "FACIAL TONER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "AGUA_MICELAR",
                        "ÁGUA MICELAR",
                        "pele",
                        true,
                        "AGUA MICELAR", "ÁGUA MICELAR",
                        "MICELLAR WATER", "MICELAR WATER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "ESFOLIANTE",
                        "ESFOLIANTE",
                        "pele",
                        true,
                        "ESFOLIANTE", "EXFOLIANTE",
                        "EXFOLIANT", "FACIAL SCRUB", "BODY SCRUB"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "MASCARA_FACIAL",
                        "MÁSCARA FACIAL",
                        "pele",
                        true,
                        "MASCARA FACIAL", "MÁSCARA FACIAL",
                        "FACE MASK", "FACIAL MASK", "CLAY MASK"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CREME_OLHOS",
                        "CREME PARA OS OLHOS",
                        "pele",
                        true,
                        "CREME PARA OS OLHOS", "EYE CREAM",
                        "EYE CONTOUR", "EYE CONTOUR CREAM"
                )
        );

        /*
         * Proteção solar
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "PROTETOR_SOLAR",
                        "PROTETOR SOLAR",
                        "proteção solar",
                        true,
                        "PROTETOR SOLAR", "SUNSCREEN",
                        "SUN SCREEN", "SUNBLOCK", "SUN BLOCK",
                        "FILTRO SOLAR", "FACIAL SUNSCREEN",
                        "BODY SUNSCREEN", "SUN PROTECTION",
                        "PROTECAO SOLAR", "PROTEÇÃO SOLAR"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "POS_SOL",
                        "PÓS-SOL",
                        "proteção solar",
                        true,
                        "POS SOL", "PÓS SOL",
                        "AFTER SUN", "AFTERSUN"
                )
        );

        /*
         * Perfumaria
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "PERFUME",
                        "PERFUME",
                        "perfumaria",
                        true,
                        "PERFUME", "PERFUM", "PARFUM",
                        "PERFUME SPRAY", "EAU DE PARFUM"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "EAU_DE_TOILETTE",
                        "EAU DE TOILETTE",
                        "perfumaria",
                        true,
                        "EAU DE TOILETTE", "EAU TOILETTE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "COLONIA",
                        "COLÔNIA",
                        "perfumaria",
                        true,
                        "COLONIA", "COLÔNIA", "COLOGNE",
                        "EAU DE COLOGNE", "COLONIA SPRAY"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "BODY_SPLASH",
                        "BODY SPLASH",
                        "perfumaria",
                        true,
                        "BODY SPLASH", "BODY MIST",
                        "BODY SPRAY", "FRAGRANCE MIST"
                )
        );

        /*
         * Maquiagem
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "MAQUIAGEM",
                        "MAQUIAGEM",
                        "maquiagem",
                        true,
                        "MAQUIAGEM", "MAKEUP", "MAKE UP",
                        "COSMETIC MAKEUP"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "BASE_MAQUIAGEM",
                        "BASE DE MAQUIAGEM",
                        "maquiagem",
                        true,
                        "BASE DE MAQUIAGEM", "BASE FACIAL",
                        "BASE LIQUIDA", "BASE LÍQUIDA",
                        "FOUNDATION", "LIQUID FOUNDATION",
                        "FACE FOUNDATION"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CORRETIVO",
                        "CORRETIVO",
                        "maquiagem",
                        true,
                        "CORRETIVO", "CONCEALER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "PO_FACIAL",
                        "PÓ FACIAL",
                        "maquiagem",
                        true,
                        "PO FACIAL", "PÓ FACIAL",
                        "FACE POWDER", "SETTING POWDER",
                        "COMPACT POWDER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "BLUSH",
                        "BLUSH",
                        "maquiagem",
                        true,
                        "BLUSH", "ROUGE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "BRONZER",
                        "BRONZER",
                        "maquiagem",
                        true,
                        "BRONZER", "BRONZEADOR FACIAL",
                        "BRONZER POWDER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "ILUMINADOR",
                        "ILUMINADOR",
                        "maquiagem",
                        true,
                        "ILUMINADOR", "HIGHLIGHTER",
                        "LUMINIZER", "ILLUMINATOR"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "BATOM",
                        "BATOM",
                        "maquiagem",
                        true,
                        "BATOM", "LIPSTICK", "LIP STICK"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "GLOSS",
                        "GLOSS",
                        "maquiagem",
                        true,
                        "LIP GLOSS", "GLOSS LABIAL", "LIPGLOSS"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "LIP_TINT",
                        "LIP TINT",
                        "maquiagem",
                        true,
                        "LIP TINT", "TINT LABIAL", "LIP STAIN"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "DELINEADOR",
                        "DELINEADOR",
                        "maquiagem",
                        true,
                        "DELINEADOR", "EYELINER", "EYE LINER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "MASCARA_CILIOS",
                        "MÁSCARA DE CÍLIOS",
                        "maquiagem",
                        true,
                        "MASCARA DE CILIOS", "MÁSCARA DE CÍLIOS",
                        "MASCARA PARA CILIOS",
                        "MASCARA CILIOS", "MASCARA DE PESTANAS",
                        "MASCARA DE PESTAÑAS"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SOMBRA_OLHOS",
                        "SOMBRA DE OLHOS",
                        "maquiagem",
                        true,
                        "SOMBRA DE OLHOS", "EYESHADOW",
                        "EYE SHADOW"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "PRIMER",
                        "PRIMER",
                        "maquiagem",
                        true,
                        "MAKEUP PRIMER", "FACE PRIMER"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "FIXADOR_MAQUIAGEM",
                        "FIXADOR DE MAQUIAGEM",
                        "maquiagem",
                        true,
                        "FIXADOR DE MAQUIAGEM",
                        "SETTING SPRAY", "MAKEUP SETTING SPRAY"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "DEMAQUILANTE",
                        "DEMAQUILANTE",
                        "maquiagem",
                        true,
                        "DEMAQUILANTE", "MAKEUP REMOVER",
                        "MAKE UP REMOVER", "CLEANSING BALM"
                )
        );

        /*
         * Unhas
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "ESMALTE",
                        "ESMALTE",
                        "unhas",
                        true,
                        "ESMALTE", "NAIL POLISH", "NAILPOLISH"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "BASE_UNHAS",
                        "BASE PARA UNHAS",
                        "unhas",
                        true,
                        "BASE PARA UNHAS", "BASE COAT", "NAIL BASE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "TOP_COAT",
                        "TOP COAT",
                        "unhas",
                        true,
                        "TOP COAT", "TOPCOAT", "NAIL TOP COAT"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "REMOVEDOR_ESMALTE",
                        "REMOVEDOR DE ESMALTE",
                        "unhas",
                        true,
                        "REMOVEDOR DE ESMALTE",
                        "NAIL POLISH REMOVER", "POLISH REMOVER"
                )
        );

        /*
         * Barbear
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "ESPUMA_BARBEAR",
                        "ESPUMA DE BARBEAR",
                        "barbear",
                        true,
                        "ESPUMA DE BARBEAR", "SHAVING FOAM",
                        "SHAVING MOUSSE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CREME_BARBEAR",
                        "CREME DE BARBEAR",
                        "barbear",
                        true,
                        "CREME DE BARBEAR", "SHAVING CREAM",
                        "SHAVE CREAM"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "POS_BARBA",
                        "PÓS-BARBA",
                        "barbear",
                        true,
                        "POS BARBA", "AFTERSHAVE", "AFTER SHAVE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "OLEO_BARBA",
                        "ÓLEO PARA BARBA",
                        "barbear",
                        true,
                        "OLEO PARA BARBA", "OLEO DE BARBA",
                        "BEARD OIL", "BEARD BALM"
                )
        );

        /*
         * Higiene oral
         *
         * Mantemos porque está presente na base anterior do classificador.
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "CREME_DENTAL",
                        "CREME DENTAL",
                        "higiene oral",
                        true,
                        "CREME DENTAL", "TOOTHPASTE", "TOOTH PASTE"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "ENXAGUANTE_BUCAL",
                        "ENXAGUANTE BUCAL",
                        "higiene oral",
                        true,
                        "ENXAGUANTE BUCAL", "MOUTHWASH",
                        "MOUTH WASH"
                )
        );

        /*
         * Infantil
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SHAMPOO_INFANTIL",
                        "SHAMPOO INFANTIL",
                        "infantil",
                        true,
                        "SHAMPOO INFANTIL", "BABY SHAMPOO",
                        "KIDS SHAMPOO"
                )
        );

        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "SABONETE_INFANTIL",
                        "SABONETE INFANTIL",
                        "infantil",
                        true,
                        "SABONETE INFANTIL", "BABY SOAP",
                        "KIDS SOAP"
                )
        );

        /*
         * Cosmético explícito.
         *
         * "COSMÉTICO" sozinho não basta: precisa de estrutura/contexto.
         */
        detectarCategoria(
                texto,
                nivel1Categoria,
                tipos,
                new TermGroup(
                        "COSMETICO_EXPLICITO",
                        "COSMÉTICO",
                        "cosméticos",
                        false,
                        "COSMETICO", "COSMETICOS", "COSMETICS",
                        "COSMETIC PRODUCT", "COSMETIC"
                )
        );

        /*
         * =============================================================
         * NÍVEL 2 — CONTEXTO DE APLICAÇÃO
         * =============================================================
         */
        detectarContexto(
                texto,
                nivel2Contexto,
                "pele",
                "PELE", "SKIN", "SKINCARE", "SKIN CARE",
                "CUIDADO DA PELE", "CUIDADOS COM A PELE",
                "CUIDADO FACIAL", "FACIAL CARE", "FACE CARE",
                "ROSTO", "FACIAL", "FACE"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "cabelo",
                "CABELO", "CABELOS", "HAIR",
                "HAIRCARE", "HAIR CARE",
                "CUIDADO DOS CABELOS", "CUIDADOS COM O CABELO",
                "COURO CABELUDO", "SCALP"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "corpo",
                "CORPO", "BODY", "BODY CARE",
                "BODYCARE", "CUIDADO CORPORAL",
                "CUIDADOS COM O CORPO"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "axilas",
                "AXILA", "AXILAS", "ARMPIT", "UNDERARM"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "barba/barbear",
                "BARBA", "BARBEAR", "BEARD", "SHAVE", "SHAVING"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "labios",
                "LABIOS", "LÁBIOS", "LIPS", "LIP CARE"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "olhos",
                "OLHOS", "EYES", "EYE AREA", "EYE CONTOUR"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "unhas",
                "UNHAS", "NAILS", "NAIL CARE"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "higiene pessoal",
                "HIGIENE PESSOAL", "PERSONAL CARE",
                "PERSONAL HYGIENE", "CUIDADO PESSOAL"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "perfumaria",
                "FRAGRANCE", "FRAGRANCIA", "FRAGRÂNCIA",
                "PERFUMERY", "PERFUMARIA"
        );

        detectarContexto(
                texto,
                nivel2Contexto,
                "maquiagem",
                "BEAUTY", "BELEZA", "MAKEUP", "MAKE UP",
                "MAQUILLAGE", "MAQUILLAJE"
        );

        /*
         * Termos genéricos úteis apenas em contexto:
         */
        boolean temGenericoCreme =
                contemTermo(texto, "CREME", false)
                        || contemTermo(texto, "CREAM", false);

        boolean temGenericoLoção =
                contemTermo(texto, "LOCAO", false)
                        || contemTermo(texto, "LOTION", false);

        boolean temGenericoGel =
                contemTermo(texto, "GEL", false);

        boolean temGenericoOleo =
                contemTermo(texto, "OLEO", false)
                        || contemTermo(texto, "OIL", false);

        boolean temGenericoSoap =
                contemTermo(texto, "SOAP", false);

        /*
         * "CREME + pele" / "CREME + body" / "CREME + face" é contexto
         * suficiente para reforçar um cosmético.
         */
        if (temGenericoCreme
                && possuiAlgumContexto(
                nivel2Contexto,
                "pele",
                "corpo",
                "rosto"
        )) {
            nivel2Contexto.add("tipo de formulação: CREME + área cosmética");
        }

        /*
         * Creme para cabelo / leave in / hair context.
         */
        if (temGenericoCreme
                && nivel2Contexto.contains("cabelo")) {
            nivel2Contexto.add("tipo de formulação: CREME + cabelo");
        }

        if (temGenericoLoção
                && nivel2Contexto.contains("corpo")) {
            nivel2Contexto.add("tipo de formulação: LOÇÃO + corpo");
        }

        /*
         * GEL não confirma sozinho. GEL + cabelo/pele/corpo pode reforçar.
         */
        if (temGenericoGel
                && possuiAlgumContexto(
                nivel2Contexto,
                "pele",
                "cabelo",
                "corpo"
        )) {
            nivel2Contexto.add("tipo de formulação: GEL + área cosmética");
        }

        /*
         * OIL não confirma sozinho.
         */
        if (temGenericoOleo
                && possuiAlgumContexto(
                nivel2Contexto,
                "cabelo",
                "barba/barbear",
                "pele",
                "corpo"
        )) {
            nivel2Contexto.add("tipo de formulação: OIL + área cosmética");
        }

        /*
         * SOAP genérico em inglês pode ser limpeza doméstica. Só reforça
         * quando acompanhado por contexto pessoal/body/face/hand.
         */
        if (temGenericoSoap
                && possuiAlgumContexto(
                nivel2Contexto,
                "higiene pessoal",
                "corpo",
                "pele"
        )) {
            nivel2Contexto.add("SOAP + contexto de higiene pessoal");
        }

        /*
         * =============================================================
         * NÍVEL 3 — ESTRUTURA DE PRODUTO / RÓTULO
         * =============================================================
         */
        boolean temMarca =
                possuiMarcaSignificativa(frontData);

        boolean temProduto =
                possuiProdutoSignificativo(frontData);

        boolean temApresentacao =
                frontData.getPresentationCandidates() != null
                        && !frontData.getPresentationCandidates().isEmpty();

        boolean temCapacidade =
                frontData.getCapacity() != null
                        && !frontData.getCapacity().trim().isEmpty();

        boolean temMarcaCosmeticaConhecida =
                possuiMarcaCosmeticaConhecida(frontData, texto);

        boolean temEstruturaRotulo =
                detectarEstruturaRotulo(
                        texto,
                        nivel3Estrutura
                );

        if (temMarca) {
            nivel3Estrutura.add("marca detectada");
        }

        if (temProduto) {
            nivel3Estrutura.add("descritor de produto detectado");
        }

        if (temApresentacao) {
            nivel3Estrutura.add("apresentação detectada");
        }

        if (temCapacidade) {
            nivel3Estrutura.add("capacidade detectada");
        }

        if (temMarcaCosmeticaConhecida) {
            nivel3Estrutura.add("marca cosmética conhecida");
        }

        /*
         * =============================================================
         * NÍVEL 4 — CLAIMS / COMPOSIÇÃO / VOCABULÁRIO
         * =============================================================
         */
        detectarApoio(
                texto,
                nivel4Apoio,
                "INGREDIENTES/INCI",
                "INGREDIENTES", "INGREDIENTE", "INGREDIENT",
                "INGREDIENTS", "COMPOSICAO", "COMPOSIÇÃO",
                "COMPOSITION", "INCI"
        );

        detectarApoio(
                texto,
                nivel4Apoio,
                "CLAIM COSMÉTICO",
                "VEGAN", "VEGANO", "VEGANA",
                "CRUELTY FREE", "DERMATOLOGICAMENTE TESTADO",
                "DERMATOLOGICALLY TESTED",
                "HIPOALERGENICO", "HIPOALERGÊNICO",
                "HYPOALLERGENIC",
                "SEM PARABENOS", "PARABEN FREE",
                "SEM SULFATOS", "SULFATE FREE",
                "SEM SILICONE", "SILICONE FREE",
                "SEM ALCOOL", "ALCOHOL FREE",
                "OIL FREE", "WATERPROOF",
                "VEGAN FRIENDLY", "NATURAL COSMETICS"
        );

        detectarApoio(
                texto,
                nivel4Apoio,
                "VOCABULÁRIO DE BENEFÍCIO COSMÉTICO",
                "HIDRATANTE", "MOISTURIZING", "HYDRATING",
                "NOURISHING", "NUTRITIVO", "NUTRICAO",
                "REPAIR", "REPARAÇÃO", "REPARADOR",
                "ANTI AGING", "ANTI-AGING",
                "ANTIOXIDANTE", "ANTIOXIDANT",
                "CALMANTE", "SOOTHING",
                "PROTETOR", "PROTECTION",
                "MATTIFYING", "MATIFICANTE"
        );

        /*
         * Algumas palavras químicas/fórmula são úteis, mas não podem
         * confirmar sozinhas.
         */
        int sinaisFormula =
                contarTermos(
                        texto,
                        "AQUA",
                        "PARFUM",
                        "GLYCERIN",
                        "GLYCERINE",
                        "DIMETHICONOL",
                        "LIMONENE",
                        "LINALOOL",
                        "CITRIC ACID",
                        "SODIUM BENZOATE",
                        "TOCOPHEROL",
                        "BUTANE",
                        "ISOBUTANE",
                        "PROPANE"
                );

        if (sinaisFormula >= 2) {
            nivel4Apoio.add(
                    "vocabulário de formulação cosmética"
            );
        }

        /*
         * =============================================================
         * CONTAMINAÇÃO DE INTERFACE
         * =============================================================
         */
        int sinaisInterface =
                contarSinaisInterface(texto);

        if (sinaisInterface >= 2) {

            evidencias.add(
                    "texto de interface/IDE/Logcat detectado"
            );

            evidencias.add(
                    "captura possivelmente contaminada"
            );

            logResultado(
                    "UNCERTAIN",
                    0.10,
                    evidencias,
                    tipos,
                    nivel1Categoria,
                    nivel2Contexto,
                    nivel3Estrutura,
                    nivel4Apoio,
                    sinaisNaoCosmeticos
            );

            return new ScanCosmeticClassification(
                    ScanCosmeticClassification.Status.UNCERTAIN,
                    0.10,
                    new ArrayList<>(evidencias),
                    new ArrayList<>(tipos)
            );
        }

        /*
         * =============================================================
         * PREPARA EVIDÊNCIAS
         * =============================================================
         */
        evidencias.addAll(
                prefixarNivel("N1", nivel1Categoria)
        );

        evidencias.addAll(
                prefixarNivel("N2", nivel2Contexto)
        );

        evidencias.addAll(
                prefixarNivel("N3", nivel3Estrutura)
        );

        evidencias.addAll(
                prefixarNivel("N4", nivel4Apoio)
        );

        /*
         * =============================================================
         * CONFLITO NÃO COSMÉTICO
         * =============================================================
         */
        if (!sinaisNaoCosmeticos.isEmpty()) {

            evidencias.addAll(
                    prefixarNivel("NAO_COSMETICO", sinaisNaoCosmeticos)
            );

            /*
             * Estrutura genérica (marca/produto/capacidade) NÃO é evidência
             * de cosmético por si só. Portanto, um alimento como
             * "CHOCOLATE 90 G" deve continuar NON_COSMETIC mesmo possuindo
             * marca, produto e capacidade.
             *
             * Conflito só existe quando há alguma evidência cosmética real:
             * N1, N2 ou N4, ou uma marca cosmética conhecida.
             */
            boolean temEvidenciaCosmeticaReal =
                    !nivel1Categoria.isEmpty()
                            || !nivel2Contexto.isEmpty()
                            || !nivel4Apoio.isEmpty()
                            || temMarcaCosmeticaConhecida;

            if (temEvidenciaCosmeticaReal) {

                evidencias.add(
                        "sinais cosméticos e não cosméticos em conflito"
                );

                logResultado(
                        "UNCERTAIN_CONFLICT",
                        0.30,
                        evidencias,
                        tipos,
                        nivel1Categoria,
                        nivel2Contexto,
                        nivel3Estrutura,
                        nivel4Apoio,
                        sinaisNaoCosmeticos
                );

                return new ScanCosmeticClassification(
                        ScanCosmeticClassification.Status.UNCERTAIN,
                        0.30,
                        new ArrayList<>(evidencias),
                        new ArrayList<>(tipos)
                );
            }

            logResultado(
                    "NON_COSMETIC",
                    0.98,
                    evidencias,
                    tipos,
                    nivel1Categoria,
                    nivel2Contexto,
                    nivel3Estrutura,
                    nivel4Apoio,
                    sinaisNaoCosmeticos
            );

            return new ScanCosmeticClassification(
                    ScanCosmeticClassification.Status.NON_COSMETIC,
                    0.98,
                    new ArrayList<>(evidencias),
                    new ArrayList<>(tipos)
            );
        }

        /*
         * =============================================================
         * MÉTRICAS DOS 4 NÍVEIS
         * =============================================================
         */
        int n1 = nivel1Categoria.size();
        int n2 = nivel2Contexto.size();
        int n3 = nivel3Estrutura.size();
        int n4 = nivel4Apoio.size();

        boolean categoriaReal =
                temCategoriaReal(nivel1Categoria);

        boolean cosmeticoExplicito =
                nivel1Categoria.contains("COSMÉTICO");

        boolean categoriaAltaPrecisao =
                temCategoriaAltaPrecisao(nivel1Categoria);

        boolean contextoForte =
                n2 >= 1;

        boolean contextoMuitoForte =
                n2 >= 2;

        boolean estruturaProduto =
                temProduto
                        || temMarca
                        || temApresentacao
                        || temCapacidade;

        boolean estruturaRotulo =
                temEstruturaRotulo;

        boolean apoioFormulaOuClaim =
                n4 >= 1;

        boolean apoioForte =
                n4 >= 2;

        boolean marcaCosmetica =
                temMarcaCosmeticaConhecida;

        /*
         * =============================================================
         * SCORE
         *
         * Cada nível tem participação:
         *
         * N1 = até 0.68
         * N2 = até 0.14
         * N3 = até 0.12
         * N4 = até 0.10
         *
         * Score é complemento da regra estrutural, não substituto.
         * =============================================================
         */
        double score = 0.0;

        /*
         * NÍVEL 1
         */
        if (categoriaReal) {
            score += categoriaAltaPrecisao
                    ? 0.70
                    : 0.62;
        }

        if (n1 >= 2) {
            score += 0.06;
        }

        /*
         * NÍVEL 2
         */
        if (contextoForte) {
            score += 0.07;
        }

        if (contextoMuitoForte) {
            score += 0.05;
        }

        /*
         * NÍVEL 3
         */
        if (temMarca) {
            score += 0.04;
        }

        if (temProduto) {
            score += 0.04;
        }

        if (temApresentacao) {
            score += 0.02;
        }

        if (temCapacidade) {
            score += 0.02;
        }

        if (estruturaRotulo) {
            score += 0.04;
        }

        /*
         * Marca cosmética conhecida reforça muito em casos de:
         * "NIVEA CREME", "DOVE ...", etc.
         */
        if (marcaCosmetica) {
            score += 0.05;
        }

        /*
         * NÍVEL 4
         */
        if (apoioFormulaOuClaim) {
            score += 0.04;
        }

        if (apoioForte) {
            score += 0.03;
        }

        /*
         * Cosmético explícito com estrutura recebe pequeno bônus.
         */
        if (cosmeticoExplicito
                && estruturaProduto) {
            score += 0.07;
        }

        score = Math.min(
                1.0,
                score
        );

        /*
         * =============================================================
         * REGRAS DE CONFIRMAÇÃO
         *
         * Vários caminhos possíveis.
         *
         * CAMINHO A
         * Categoria de alta precisão.
         *
         * CAMINHO B
         * Categoria real + contexto.
         *
         * CAMINHO C
         * Categoria real + estrutura.
         *
         * CAMINHO D
         * Categoria real + apoio.
         *
         * CAMINHO E
         * Contexto forte + estrutura + apoio.
         *
         * CAMINHO F
         * Marca cosmética conhecida + formulação/contexto.
         *
         * Assim sabonete, creme Nivea, creme para pentear e condicionador
         * deixam de depender de um único score rígido.
         * =============================================================
         */

        boolean caminhoA =
                categoriaAltaPrecisao;

        boolean caminhoB =
                categoriaReal
                        && contextoForte;

        boolean caminhoC =
                categoriaReal
                        && estruturaProduto;

        boolean caminhoD =
                categoriaReal
                        && apoioFormulaOuClaim;

        boolean caminhoE =
                contextoMuitoForte
                        && estruturaProduto
                        && (apoioFormulaOuClaim || marcaCosmetica);

        boolean caminhoF =
                marcaCosmetica
                        && (
                        /*
                         * Casos muito comuns de frente curta, como
                         * "NIVEA CREME". A marca é uma evidência forte
                         * de contexto cosmético, enquanto CREME identifica
                         * a forma do produto. A combinação é aceita sem
                         * exigir que o OCR tenha lido "pele" ou "rosto".
                         */
                        temGenericoCreme
                                || (temGenericoLoção
                                && nivel2Contexto.contains("corpo"))
                                || (categoriaReal)
                );

        /*
         * Categoria real explícita + uma estrutura mínima:
         * praticamente todos os cosméticos frontais passam aqui.
         */
        boolean confirmado =
                caminhoA
                        || caminhoB
                        || caminhoC
                        || caminhoD
                        || caminhoE
                        || caminhoF;

        /*
         * "COSMÉTICO" genérico sozinho não confirma.
         * Deve haver estrutura.
         */
        if (cosmeticoExplicito
                && !categoriaReal) {

            confirmado =
                    estruturaProduto
                            && (
                            contextoForte
                                    || apoioFormulaOuClaim
                                    || marcaCosmetica
                    );
        }

        /*
         * Score mínimo.
         *
         * Para categorias de alta precisão permitimos um caminho
         * excepcional com estrutura mínima mesmo se o score ficar
         * marginal devido a OCR.
         */
        if (confirmado) {

            boolean scoreAceito =
                    score >= SCORE_CONFIRMED_MIN
                            || (
                            categoriaAltaPrecisao
                                    && score >= 0.70
                    )
                            || (
                            marcaCosmetica
                                    && (
                                    categoriaReal
                                            || (temGenericoCreme
                                            && contextoForte)
                            )
                                    && score >= 0.70
                    );

            if (scoreAceito) {

                logResultado(
                        "COSMETIC_CONFIRMED",
                        score,
                        evidencias,
                        tipos,
                        nivel1Categoria,
                        nivel2Contexto,
                        nivel3Estrutura,
                        nivel4Apoio,
                        sinaisNaoCosmeticos
                );

                return new ScanCosmeticClassification(
                        ScanCosmeticClassification.Status.COSMETIC_CONFIRMED,
                        score,
                        new ArrayList<>(evidencias),
                        new ArrayList<>(tipos)
                );
            }
        }

        /*
         * =============================================================
         * CASOS INCERTOS
         * =============================================================
         */
        double scoreIncerto =
                Math.min(
                        SCORE_UNCERTAIN_MAX,
                        score
                );

        if (scoreIncerto <= 0.0
                && (
                !nivel2Contexto.isEmpty()
                        || !nivel3Estrutura.isEmpty()
                        || !nivel4Apoio.isEmpty()
        )) {
            scoreIncerto = 0.20;
        }

        logResultado(
                "UNCERTAIN",
                scoreIncerto,
                evidencias,
                tipos,
                nivel1Categoria,
                nivel2Contexto,
                nivel3Estrutura,
                nivel4Apoio,
                sinaisNaoCosmeticos
        );

        return new ScanCosmeticClassification(
                ScanCosmeticClassification.Status.UNCERTAIN,
                scoreIncerto,
                new ArrayList<>(evidencias),
                new ArrayList<>(tipos)
        );
    }

    /**
     * Guarda usada pela ScanSuccessFrontActivity.
     *
     * O catálogo precisa ter falhado antes.
     */
    public static boolean shouldOpenNewCosmeticFlow(
            boolean productFoundInCatalog,
            @NonNull ScanCosmeticClassification classification
    ) {

        if (classification == null) {
            return false;
        }

        return !productFoundInCatalog
                && classification.isConfirmedCosmetic();
    }

    /*
     * =============================================================
     * NÍVEL 1
     * =============================================================
     */
    private static void detectarCategoria(
            String texto,
            Set<String> sinais,
            Set<String> tipos,
            TermGroup grupo
    ) {

        if (grupo == null
                || texto == null) {
            return;
        }

        for (String alias :
                grupo.aliases) {

            if (contemTermo(
                    texto,
                    alias,
                    true
            )) {

                sinais.add(
                        grupo.evidencia
                );

                tipos.add(
                        grupo.tipo
                );

                return;
            }
        }
    }

    private static boolean temCategoriaReal(
            Set<String> sinais
    ) {

        if (sinais.isEmpty()) {
            return false;
        }

        for (String sinal :
                sinais) {

            if (!"COSMÉTICO".equals(
                    sinal
            )) {
                return true;
            }
        }

        return false;
    }

    private static boolean temCategoriaAltaPrecisao(
            Set<String> sinais
    ) {

        /*
         * Categorias que praticamente já identificam a natureza do produto
         * mesmo quando a frente tem pouco texto.
         */
        String[] altaPrecisao = {
                "DESODORANTE",
                "ANTITRANSPIRANTE",
                "SHAMPOO",
                "CONDICIONADOR",
                "MÁSCARA CAPILAR",
                "CREME PARA PENTEAR",
                "TRATAMENTO CAPILAR",
                "ÓLEO CAPILAR",
                "GEL CAPILAR",
                "MOUSSE CAPILAR",
                "CERA CAPILAR",
                "SPRAY CAPILAR",
                "SHAMPOO SECO",
                "COLORAÇÃO CAPILAR",
                "DESCOLORANTE CAPILAR",
                "SABONETE",
                "SABONETE LÍQUIDO",
                "HIGIENE ÍNTIMA",
                "BODY WASH",
                "HIDRATANTE",
                "CREME FACIAL",
                "LOÇÃO CORPORAL",
                "CREME PARA AS MÃOS",
                "LIMPEZA FACIAL",
                "SÉRUM",
                "TÔNICO",
                "ÁGUA MICELAR",
                "ESFOLIANTE",
                "MÁSCARA FACIAL",
                "CREME PARA OS OLHOS",
                "PROTETOR SOLAR",
                "PÓS-SOL",
                "PERFUME",
                "EAU DE TOILETTE",
                "COLÔNIA",
                "BODY SPLASH",
                "MAQUIAGEM",
                "BASE DE MAQUIAGEM",
                "CORRETIVO",
                "PÓ FACIAL",
                "BLUSH",
                "BRONZER",
                "ILUMINADOR",
                "BATOM",
                "GLOSS",
                "LIP TINT",
                "DELINEADOR",
                "MÁSCARA DE CÍLIOS",
                "SOMBRA DE OLHOS",
                "FIXADOR DE MAQUIAGEM",
                "DEMAQUILANTE",
                "ESMALTE",
                "BASE PARA UNHAS",
                "TOP COAT",
                "REMOVEDOR DE ESMALTE",
                "ESPUMA DE BARBEAR",
                "CREME DE BARBEAR",
                "PÓS-BARBA",
                "ÓLEO PARA BARBA",
                "CREME DENTAL",
                "ENXAGUANTE BUCAL",
                "SHAMPOO INFANTIL",
                "SABONETE INFANTIL"
        };

        for (String sinal :
                sinais) {

            for (String categoria :
                    altaPrecisao) {

                if (categoria.equals(
                        sinal
                )) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * =============================================================
     * NÍVEL 2
     * =============================================================
     */
    private static void detectarContexto(
            String texto,
            Set<String> sinais,
            String tipo,
            String... termos
    ) {

        if (texto == null
                || termos == null) {
            return;
        }

        for (String termo :
                termos) {

            if (contemTermo(
                    texto,
                    termo,
                    true
            )) {

                sinais.add(
                        tipo
                );

                return;
            }
        }
    }

    private static boolean possuiAlgumContexto(
            Set<String> contextos,
            String... valores
    ) {

        if (contextos == null
                || valores == null) {
            return false;
        }

        for (String valor :
                valores) {

            if (contextos.contains(
                    valor
            )) {
                return true;
            }
        }

        return false;
    }

    /*
     * =============================================================
     * NÍVEL 3
     * =============================================================
     */
    private static boolean detectarEstruturaRotulo(
            String texto,
            Set<String> estrutura
    ) {

        boolean encontrou = false;

        if (contemTermo(
                texto,
                "INGREDIENTES",
                false
        ) || contemTermo(
                texto,
                "INCI",
                false
        ) || contemTermo(
                texto,
                "COMPOSICAO",
                false
        ) || contemTermo(
                texto,
                "INGREDIENTS",
                false
        )) {

            estrutura.add(
                    "estrutura de composição/INCI"
            );

            encontrou = true;
        }

        if (contemTermo(
                texto,
                "MODO DE USO",
                true
        ) || contemTermo(
                texto,
                "HOW TO USE",
                true
        ) || contemTermo(
                texto,
                "DIRECTIONS",
                true
        ) || contemTermo(
                texto,
                "INSTRUCOES DE USO",
                true
        )) {

            estrutura.add(
                    "modo de uso detectado"
            );

            encontrou = true;
        }

        if (contemTermo(
                texto,
                "ADVERTENCIA",
                true
        ) || contemTermo(
                texto,
                "ADVERTENCIAS",
                true
        ) || contemTermo(
                texto,
                "PRECAUCOES",
                true
        ) || contemTermo(
                texto,
                "PRECAUTIONS",
                true
        )) {

            estrutura.add(
                    "advertência/precaução de rótulo"
            );

            encontrou = true;
        }

        if (contemTermo(
                texto,
                "SAC",
                false
        ) || contemTermo(
                texto,
                "SERVICO DE ATENDIMENTO",
                false
        ) || contemTermo(
                texto,
                "CUSTOMER SERVICE",
                false
        )) {

            estrutura.add(
                    "SAC/atendimento ao consumidor"
            );

            encontrou = true;
        }

        return encontrou;
    }

    /*
     * =============================================================
     * NÍVEL 4
     * =============================================================
     */
    private static void detectarApoio(
            String texto,
            Set<String> sinais,
            String evidencia,
            String... termos
    ) {

        if (texto == null
                || termos == null) {
            return;
        }

        for (String termo :
                termos) {

            if (contemTermo(
                    texto,
                    termo,
                    true
            )) {

                sinais.add(
                        evidencia
                );

                return;
            }
        }
    }

    private static int contarTermos(
            String texto,
            String... termos
    ) {

        int encontrados = 0;

        if (texto == null
                || termos == null) {
            return 0;
        }

        for (String termo :
                termos) {

            if (contemTermo(
                    texto,
                    termo,
                    true
            )) {

                encontrados++;
            }
        }

        return encontrados;
    }

    /*
     * =============================================================
     * NÃO COSMÉTICO
     * =============================================================
     */
    private static void detectarNaoCosmetico(
            String texto,
            Set<String> sinais,
            String... termos
    ) {

        if (texto == null
                || termos == null) {
            return;
        }

        for (String termo :
                termos) {

            if (contemTermo(
                    texto,
                    termo,
                    true
            )) {

                sinais.add(
                        normalizar(termo)
                );
            }
        }
    }

    /*
     * =============================================================
     * MARCA
     * =============================================================
     */
    private static boolean possuiMarcaCosmeticaConhecida(
            @NonNull ScanFrontData frontData,
            String texto
    ) {

        if (frontData.getBrandCandidates() != null) {

            for (String candidato :
                    frontData
                            .getBrandCandidates()) {

                if (ehMarcaCosmeticaConhecida(
                        candidato
                )) {
                    return true;
                }
            }
        }

        /*
         * Fallback no OCR.
         */
        for (String marca :
                MARCAS_COSMETICAS_CONHECIDAS) {

            if (contemTermo(
                    texto,
                    marca,
                    true
            )) {
                return true;
            }
        }

        return false;
    }

    private static boolean ehMarcaCosmeticaConhecida(
            String candidato
    ) {

        String valor =
                normalizar(candidato);

        if (valor.isEmpty()) {
            return false;
        }

        for (String marca :
                MARCAS_COSMETICAS_CONHECIDAS) {

            if (valor.equals(
                    normalizar(marca)
            )
                    || contemTermo(
                    valor,
                    marca,
                    false
            )
                    || contemTermo(
                    marca,
                    valor,
                    false
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean possuiMarcaSignificativa(
            @NonNull ScanFrontData frontData
    ) {

        if (frontData.getBrandCandidates() == null) {
            return false;
        }

        for (String candidato :
                frontData
                        .getBrandCandidates()) {

            String valor =
                    normalizar(candidato);

            if (valor.isEmpty()
                    || ehRuidoComum(valor)
                    || STOPWORDS.contains(valor)) {
                continue;
            }

            if (pareceCodigo(valor)) {
                continue;
            }

            if (temLetras(valor)
                    && valor.length() >= 3) {

                return true;
            }
        }

        return false;
    }

    private static boolean possuiProdutoSignificativo(
            @NonNull ScanFrontData frontData
    ) {

        if (frontData.getProductCandidates() == null) {
            return false;
        }

        for (String candidato :
                frontData
                        .getProductCandidates()) {

            String valor =
                    normalizar(candidato);

            if (valor.isEmpty()
                    || ehRuidoComum(valor)
                    || STOPWORDS.contains(valor)
                    || pareceCodigo(valor)
                    || pareceCapacidade(valor)) {
                continue;
            }

            /*
             * Termos genéricos isolados não contam como descritor forte.
             */
            if (valor.equals("CREME")
                    || valor.equals("CREAM")
                    || valor.equals("GEL")
                    || valor.equals("OIL")
                    || valor.equals("OLEO")
                    || valor.equals("SOAP")
                    || valor.equals("BODY")
                    || valor.equals("BEAUTY")
                    || valor.equals("FRAGRANCE")
            ) {
                continue;
            }

            if (temLetras(valor)
                    && valor.length() >= 4) {

                return true;
            }
        }

        return false;
    }

    private static boolean temLetras(
            String valor
    ) {

        if (valor == null
                || valor.isEmpty()) {
            return false;
        }

        for (int i = 0;
             i < valor.length();
             i++) {

            if (Character.isLetter(
                    valor.charAt(i)
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean pareceCodigo(
            String valor
    ) {

        if (valor == null
                || valor.isEmpty()) {
            return false;
        }

        int digitos = 0;

        for (int i = 0;
             i < valor.length();
             i++) {

            if (Character.isDigit(
                    valor.charAt(i)
            )) {
                digitos++;
            }
        }

        int semEspacos =
                valor
                        .replace(
                                " ",
                                ""
                        )
                        .length();

        return digitos >= 5
                && semEspacos > 0
                && digitos >= semEspacos * 0.60;
    }

    private static boolean pareceCapacidade(
            String valor
    ) {

        if (valor == null
                || valor.isEmpty()) {
            return false;
        }

        String v =
                valor
                        .replace(
                                ",",
                                "."
                        )
                        .replace(
                                " ",
                                ""
                        );

        return v.matches(
                "\\d+(\\.\\d+)?"
                        + "(ML|L|G|KG|MG|OZ|FLOZ)"
        );
    }

    /*
     * =============================================================
     * MATCH DE TEXTO / OCR
     * =============================================================
     */
    private static boolean contemTermo(
            String texto,
            String termo,
            boolean permitirFuzzy
    ) {

        String nTexto =
                normalizar(texto);

        String nTermo =
                normalizar(termo);

        if (nTexto.isEmpty()
                || nTermo.isEmpty()) {

            return false;
        }

        /*
         * Match exato por token/frase.
         */
        String paddedTexto =
                " " + nTexto + " ";

        String paddedTermo =
                " " + nTermo + " ";

        if (paddedTexto.contains(
                paddedTermo
        )) {

            return true;
        }

        /*
         * Não executar fuzzy para termos curtos.
         */
        if (!permitirFuzzy
                || nTermo.length() < 7) {
            return false;
        }

        String[] tokensTexto =
                nTexto.split("\\s+");

        String[] tokensTermo =
                nTermo.split("\\s+");

        /*
         * Frases.
         */
        if (tokensTermo.length > 1
                && tokensTexto.length >= tokensTermo.length) {

            for (int i = 0;
                 i <= tokensTexto.length
                         - tokensTermo.length;
                 i++) {

                boolean todosCasam =
                        true;

                for (int j = 0;
                     j < tokensTermo.length;
                     j++) {

                    if (!tokenPareceIgual(
                            tokensTexto[i + j],
                            tokensTermo[j]
                    )) {

                        todosCasam =
                                false;

                        break;
                    }
                }

                if (todosCasam) {
                    return true;
                }
            }
        }

        /*
         * Palavra única.
         */
        if (tokensTermo.length == 1) {

            for (String tokenTexto :
                    tokensTexto) {

                if (tokenPareceIgual(
                        tokenTexto,
                        nTermo
                )) {

                    return true;
                }
            }
        }

        /*
         * OCR que grudou palavras.
         */
        if (tokensTermo.length == 1
                && nTermo.length() >= 8) {

            String compactado =
                    nTexto.replace(
                            " ",
                            ""
                    );

            if (compactado.contains(
                    nTermo
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean tokenPareceIgual(
            String tokenTexto,
            String tokenTermo
    ) {

        if (tokenTexto == null
                || tokenTermo == null) {
            return false;
        }

        if (tokenTexto.equals(
                tokenTermo
        )) {

            return true;
        }

        if (tokenTexto.length() < 6
                || tokenTermo.length() < 6) {

            return false;
        }

        int maxDistance;

        if (tokenTermo.length() <= 7) {

            maxDistance =
                    FUZZY_MAX_DISTANCE_SHORT;

        } else if (tokenTermo.length() <= 12) {

            maxDistance =
                    FUZZY_MAX_DISTANCE_MEDIUM;

        } else {

            maxDistance =
                    FUZZY_MAX_DISTANCE_LONG;
        }

        return levenshtein(
                tokenTexto,
                tokenTermo
        ) <= maxDistance;
    }

    private static int levenshtein(
            String a,
            String b
    ) {

        int[] previous =
                new int[
                        b.length() + 1
                        ];

        int[] current =
                new int[
                        b.length() + 1
                        ];

        for (int j = 0;
             j <= b.length();
             j++) {

            previous[j] =
                    j;
        }

        for (int i = 1;
             i <= a.length();
             i++) {

            current[0] =
                    i;

            for (int j = 1;
                 j <= b.length();
                 j++) {

                int custo =
                        a.charAt(i - 1)
                                == b.charAt(j - 1)
                                ? 0
                                : 1;

                current[j] =
                        Math.min(
                                Math.min(
                                        current[j - 1]
                                                + 1,
                                        previous[j]
                                                + 1
                                ),
                                previous[j - 1]
                                        + custo
                        );
            }

            int[] temp =
                    previous;

            previous =
                    current;

            current =
                    temp;
        }

        return previous[
                b.length()
                ];
    }

    /*
     * =============================================================
     * INTERFACE / DEBUG
     * =============================================================
     */
    private static int contarSinaisInterface(
            String texto
    ) {

        String[][] grupos = {

                {
                        "ANDROID",
                        "ANDROID V"
                },

                {
                        "LOGCAT"
                },

                {
                        "LOGINACTIVITY"
                },

                {
                        "MENUACTIVITY"
                },

                {
                        "SCANCONTROLLER"
                },

                {
                        "CANFRONTANALYZERJAVA"
                },

                {
                        "WIFISTAIFACEHIDLIMPL"
                },

                {
                        "SURFACEFLINGER"
                },

                {
                        "VENUSMOBILE"
                },

                {
                        "COM VENUSSYSTEM"
                },

                {
                        "SRC MAIN JAVA"
                },

                {
                        "SYSTEM SERVER"
                },

                {
                        "SAMSUNG",
                        "SM-S911B"
                },

                {
                        "CTRL",
                        "CTRL I",
                        "CTRL TL"
                }
        };

        int encontrados =
                0;

        for (String[] grupo :
                grupos) {

            boolean encontrado =
                    false;

            for (String termo :
                    grupo) {

                if (contemTermo(
                        texto,
                        termo,
                        false
                )) {

                    encontrado =
                            true;

                    break;
                }
            }

            if (encontrado) {
                encontrados++;
            }
        }

        return encontrados;
    }

    /*
     * =============================================================
     * NORMALIZAÇÃO
     * =============================================================
     */
    @NonNull
    private static String normalizar(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return ScanTextNormalizer
                .normalize(texto)
                .replaceAll(
                        "[^A-Z0-9% ]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    /*
     * =============================================================
     * TEXTO COMPLETO
     * =============================================================
     */
    @NonNull
    private static String montarTextoCompleto(
            @NonNull ScanOcrResult ocr,
            @NonNull ScanFrontData frontData
    ) {

        StringBuilder builder =
                new StringBuilder();

        adicionarTexto(
                builder,
                ocr.getFullText()
        );

        if (ocr.getLines() != null) {

            for (String linha :
                    ocr.getLines()) {

                adicionarTexto(
                        builder,
                        linha
                );
            }
        }

        if (frontData.getBrandCandidates() != null) {

            for (String item :
                    frontData
                            .getBrandCandidates()) {

                adicionarTexto(
                        builder,
                        item
                );
            }
        }

        if (frontData.getProductCandidates() != null) {

            for (String item :
                    frontData
                            .getProductCandidates()) {

                adicionarTexto(
                        builder,
                        item
                );
            }
        }

        if (frontData.getPresentationCandidates() != null) {

            for (String item :
                    frontData
                            .getPresentationCandidates()) {

                adicionarTexto(
                        builder,
                        item
                );
            }
        }

        return normalizar(
                builder.toString()
        );
    }

    private static void adicionarTexto(
            @NonNull StringBuilder builder,
            String texto
    ) {

        if (texto == null
                || texto.trim().isEmpty()) {

            return;
        }

        builder
                .append(' ')
                .append(texto);
    }

    /*
     * =============================================================
     * LOG
     * =============================================================
     */
    private static void logResultado(
            String status,
            double score,
            Set<String> evidencias,
            Set<String> tipos,
            Set<String> n1,
            Set<String> n2,
            Set<String> n3,
            Set<String> n4,
            Set<String> naoCosmeticos
    ) {

        android.util.Log.d(
                TAG,
                "RESULTADO="
                        + status
                        + " SCORE="
                        + String.format(
                        Locale.US,
                        "%.2f",
                        score
                )
        );

        android.util.Log.d(
                TAG,
                "TIPOS="
                        + tipos
        );

        android.util.Log.d(
                TAG,
                "N1_CATEGORIA="
                        + n1
        );

        android.util.Log.d(
                TAG,
                "N2_CONTEXTO="
                        + n2
        );

        android.util.Log.d(
                TAG,
                "N3_ESTRUTURA="
                        + n3
        );

        android.util.Log.d(
                TAG,
                "N4_APOIO="
                        + n4
        );

        android.util.Log.d(
                TAG,
                "NAO_COSMETICO="
                        + naoCosmeticos
        );

        android.util.Log.d(
                TAG,
                "EVIDENCIAS="
                        + evidencias
        );
    }

    private static List<String> prefixarNivel(
            String prefixo,
            Set<String> valores
    ) {

        List<String> resultado =
                new ArrayList<>();

        if (valores == null) {
            return resultado;
        }

        for (String valor :
                valores) {

            resultado.add(
                    prefixo
                            + ": "
                            + valor
            );
        }

        return resultado;
    }

    /*
     * =============================================================
     * RUÍDO
     * =============================================================
     */
    private static boolean ehRuidoComum(
            String valor
    ) {

        if (valor == null
                || valor.isEmpty()) {
            return true;
        }

        return valor.equals("ANDROID")
                || valor.equals("ANDROID V")
                || valor.equals("LOGCAT")
                || valor.equals("LOGINACTIVITY")
                || valor.equals("LOGIN")
                || valor.equals("JAVA")
                || valor.equals("KOTLIN")
                || valor.equals("SRC")
                || valor.equals("MAIN")
                || valor.equals("PUBLIC")
                || valor.equals("PRIVATE")
                || valor.equals("STATIC")
                || valor.equals("CLASS")
                || valor.equals("FINAL")
                || valor.equals("INTENT")
                || valor.equals("ACTIVITY")
                || valor.equals("SYSTEM")
                || valor.equals("SERVER")
                || valor.equals("SYSTEM SERVER")
                || valor.equals("SURFACEFLINGER")
                || valor.equals("SCANCONTROLLER")
                || valor.equals("CANFRONTANALYZERJAVA")
                || valor.equals("WIFISTAIFACEHIDLIMPL")
                || valor.equals("VENUSMOBILE")
                || valor.equals("SAMSUNG")
                || valor.equals("DEBUG")
                || valor.equals("CTRL")
                || valor.equals("CTRL I")
                || valor.equals("CTRL TL");
    }

    /*
     * =============================================================
     * MODELO INTERNO
     * =============================================================
     */
    private static final class TermGroup {

        private final String id;
        private final String evidencia;
        private final String tipo;
        private final boolean altaPrecisao;
        private final List<String> aliases;

        private TermGroup(
                String id,
                String evidencia,
                String tipo,
                boolean altaPrecisao,
                String... aliases
        ) {

            this.id =
                    id;

            this.evidencia =
                    evidencia;

            this.tipo =
                    tipo;

            this.altaPrecisao =
                    altaPrecisao;

            this.aliases =
                    new ArrayList<>();

            if (aliases != null) {

                this.aliases.addAll(
                        Arrays.asList(
                                aliases
                        )
                );
            }
        }
    }
}
