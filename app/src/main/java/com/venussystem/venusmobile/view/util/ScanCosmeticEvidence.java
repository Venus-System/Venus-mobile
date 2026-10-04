package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import com.venussystem.venusmobile.model.ScanFrontData;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.contemTermo;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.ehRuidoComum;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.pareceCapacidade;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.pareceCodigo;
import static com.venussystem.venusmobile.view.util.ScanCosmeticText.temLetras;

/** Internal collaborator for ScanCosmeticClassifier; not a second scan entry point. */
final class ScanCosmeticEvidence {
    private ScanCosmeticEvidence() { }

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

    /*
     * =============================================================
     * NÍVEL 2
     * =============================================================
     */
    static void detectarContexto(
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

    static boolean possuiAlgumContexto(
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
    static boolean detectarEstruturaRotulo(
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
    static void detectarApoio(
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

    static int contarTermos(
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
    static void detectarNaoCosmetico(
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
    static boolean possuiMarcaCosmeticaConhecida(
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

    static boolean possuiMarcaSignificativa(
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

    static boolean possuiProdutoSignificativo(
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

    /*
     * =============================================================
     * INTERFACE / DEBUG
     * =============================================================
     */
    static int contarSinaisInterface(
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
}
