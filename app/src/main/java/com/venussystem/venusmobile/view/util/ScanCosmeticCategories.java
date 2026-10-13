package com.venussystem.venusmobile.view.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.contemTermo;

/** Internal collaborator for ScanCosmeticClassifier; not a second scan entry point. */
final class ScanCosmeticCategories {
    private ScanCosmeticCategories() { }

    /** Applies the existing ordered category vocabulary without changing scoring. */
    static void detectarCategorias(String texto, Set<String> nivel1Categoria, Set<String> tipos) {
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

    static boolean temCategoriaReal(
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

    static boolean temCategoriaAltaPrecisao(
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
