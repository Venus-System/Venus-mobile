package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import com.venussystem.venusmobile.model.ScanCosmeticClassification;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static com.venussystem.venusmobile.view.util.ScanCosmeticCategories.detectarCategorias;
import static com.venussystem.venusmobile.view.util.ScanCosmeticCategories.temCategoriaAltaPrecisao;
import static com.venussystem.venusmobile.view.util.ScanCosmeticCategories.temCategoriaReal;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.contarSinaisInterface;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.contarTermos;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.detectarApoio;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.detectarContexto;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.detectarEstruturaRotulo;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.detectarNaoCosmetico;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.possuiAlgumContexto;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.possuiMarcaCosmeticaConhecida;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.possuiMarcaSignificativa;
import static com.venussystem.venusmobile.view.util.ScanCosmeticEvidence.possuiProdutoSignificativo;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.contemTermo;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.montarTextoCompleto;

public final class ScanCosmeticClassifier {
    private static final double SCORE_CONFIRMED_MIN = 0.78;

    private static final double SCORE_UNCERTAIN_MAX = 0.77;

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
        Set<String> nivel1Categoria =
                new LinkedHashSet<>();
        Set<String> nivel2Contexto =
                new LinkedHashSet<>();
        Set<String> nivel3Estrutura =
                new LinkedHashSet<>();
        Set<String> nivel4Apoio =
                new LinkedHashSet<>();
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
        detectarCategorias(texto, nivel1Categoria, tipos);
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
        if (temGenericoCreme
                && possuiAlgumContexto(
                nivel2Contexto,
                "pele",
                "corpo",
                "rosto"
        )) {
            nivel2Contexto.add("tipo de formulação: CREME + área cosmética");
        }
        if (temGenericoCreme
                && nivel2Contexto.contains("cabelo")) {
            nivel2Contexto.add("tipo de formulação: CREME + cabelo");
        }
        if (temGenericoLoção
                && nivel2Contexto.contains("corpo")) {
            nivel2Contexto.add("tipo de formulação: LOÇÃO + corpo");
        }
        if (temGenericoGel
                && possuiAlgumContexto(
                nivel2Contexto,
                "pele",
                "cabelo",
                "corpo"
        )) {
            nivel2Contexto.add("tipo de formulação: GEL + área cosmética");
        }
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
        if (temGenericoSoap
                && possuiAlgumContexto(
                nivel2Contexto,
                "higiene pessoal",
                "corpo",
                "pele"
        )) {
            nivel2Contexto.add("SOAP + contexto de higiene pessoal");
        }
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
        if (!sinaisNaoCosmeticos.isEmpty()) {
            evidencias.addAll(
                    prefixarNivel("NAO_COSMETICO", sinaisNaoCosmeticos)
            );
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
        double score = 0.0;
        if (categoriaReal) {
            score += categoriaAltaPrecisao
                    ? 0.70
                    : 0.62;
        }
        if (n1 >= 2) {
            score += 0.06;
        }
        if (contextoForte) {
            score += 0.07;
        }
        if (contextoMuitoForte) {
            score += 0.05;
        }
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
        if (marcaCosmetica) {
            score += 0.05;
        }
        if (apoioFormulaOuClaim) {
            score += 0.04;
        }
        if (apoioForte) {
            score += 0.03;
        }
        if (cosmeticoExplicito
                && estruturaProduto) {
            score += 0.07;
        }
        score = Math.min(
                1.0,
                score
        );
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
                        temGenericoCreme
                                || (temGenericoLoção
                                && nivel2Contexto.contains("corpo"))
                                || (categoriaReal)
                );
        boolean confirmado =
                caminhoA
                        || caminhoB
                        || caminhoC
                        || caminhoD
                        || caminhoE
                        || caminhoF;
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
}
