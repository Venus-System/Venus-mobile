package com.venussystem.venusmobile.repository;

import androidx.annotation.NonNull;
import com.venussystem.venusmobile.view.util.ScanTextNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Internal collaborator for ScanProductMatchRepository; not a second scan entry point. */
final class ScanCatalogText {
    private ScanCatalogText() { }

    private static final double MIN_SHORT_BRAND_SCORE = 0.92;

    /**
     * Termos realmente genéricos.
     *
     * Importante:
     * MEN / WOMEN / UNISEX / 72H etc. NÃO são mais
     * descartados aqui, pois podem diferenciar produtos
     * da mesma marca.
     */
    static boolean isTermoFraco(
            @NonNull String token
    ) {
        switch (token) {
            /*
             * Tipos genéricos de produto.
             */
            case "GEL":
            case "LOCAO":
            case "LOÇÃO":
            case "LIQUIDO":
            case "LIQUID":
            case "CREME":
            case "CREMA":
            case "CREAM":
            case "SPRAY":
            case "AEROSOL":
                /*
                 * Desodorante / antitranspirante.
                 */
            case "DESODORANTE":
            case "DEODORANTE":
            case "ANTITRANSPIRANTE":
            case "ANTIPERSPIRANTE":
            case "ANTITRANSPIRANT":
            case "ANTIPERSPIRANT":
                /*
                 * Categorias / descrições genéricas.
                 */
            case "SHAMPOO":
            case "CONDICIONADOR":
            case "CONDITIONER":
            case "SABONETE":
            case "SOAP":
                /*
                 * Termos genéricos de proteção/descrição.
                 */
            case "PROTECAO":
            case "PROTECCION":
            case "PROTECTION":
            case "PERFUME":
            case "PERFUMACAO":
            case "FRAGRANCE":
            case "BODY":
            case "SKIN":
            case "HAIR":
                /*
                 * Termos muito genéricos de descrição.
                 */
            case "DRY":
            case "LONG":
            case "LASTING":
            case "INVISIBLE":
            case "INVISIVEL":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    static List<String> limparCandidatosMarca(
            @NonNull List<String> entrada
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String item :
                entrada) {
            if (item == null) {
                continue;
            }
            String normalizado =
                    normalizarCampo(
                            item
                    );
            if (normalizado.length() < 4) {
                continue;
            }
            if (!resultado.contains(
                    normalizado
            )) {
                resultado.add(
                        normalizado
                );
            }
        }
        return resultado;
    }

    @NonNull
    static List<String> limparCandidatosProduto(
            @NonNull List<String> entrada
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String item :
                entrada) {
            if (item == null) {
                continue;
            }
            String normalizado =
                    normalizarCampo(
                            item
                    );
            if (normalizado.length() < 3) {
                continue;
            }
            if (!resultado.contains(
                    normalizado
            )) {
                resultado.add(
                        normalizado
                );
            }
        }
        return resultado;
    }

    /**
     * Remove a marca de uma string.
     *
     * Também funciona para marcas compostas:
     *
     * O BOTICARIO
     * LA ROCHE POSAY
     * VICTORIA SECRET
     */
    @NonNull
    static String removerMarcaDoTexto(
            @NonNull String texto,
            String marca
    ) {
        String resultado =
                normalizarCampo(
                        texto
                );
        String marcaNormalizada =
                normalizarCampo(
                        marca
                );
        if (marcaNormalizada.isEmpty()) {
            return resultado;
        }
        /*
         * Remove a expressão inteira.
         */
        resultado =
                resultado
                        .replace(
                                marcaNormalizada,
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();
        /*
         * Exemplo:
         *
         * OCR = OBOTICARIO
         * Banco = O BOTICARIO
         *
         * O matcher de marca pode reconhecer os dois.
         * Aqui apenas evitamos que um texto composto
         * só da marca entre como nome de produto.
         */
        String marcaSemEspacos =
                marcaNormalizada
                        .replace(
                                " ",
                                ""
                        );
        String textoSemEspacos =
                resultado
                        .replace(
                                " ",
                                ""
                        );
        if (!marcaSemEspacos.isEmpty()
                && textoSemEspacos.equals(
                marcaSemEspacos
        )) {
            return "";
        }
        return resultado;
    }

    @NonNull
    static double melhorSimilaridadeOCR(
            @NonNull String consulta,
            @NonNull String alvo,
            boolean campoMarca
    ) {
        double melhor =
                0.0;
        for (String variante :
                gerarVariantesOCR(
                        consulta
                )) {
            if (variante == null
                    || variante.isEmpty()) {
                continue;
            }
            double score =
                    similaridade(
                            variante,
                            alvo
                    );
            /*
             * Marcas curtas exigem mais precisão.
             */
            if (campoMarca
                    && variante.length() <= 4
                    && score
                    < MIN_SHORT_BRAND_SCORE) {
                continue;
            }
            if (score > melhor) {
                melhor = score;
            }
        }
        return melhor;
    }

    @NonNull
    private static List<String> gerarVariantesOCR(
            @NonNull String entrada
    ) {
        String base =
                normalizarCampo(
                        entrada
                );
        Set<String> variantes =
                new LinkedHashSet<>();
        variantes.add(
                base
        );
        /*
         * Substituições comuns de OCR.
         */
        variantes.add(
                base.replace(
                        '0',
                        'O'
                )
        );
        variantes.add(
                base.replace(
                        '1',
                        'I'
                )
        );
        variantes.add(
                base.replace(
                        '1',
                        'L'
                )
        );
        variantes.add(
                base.replace(
                        '5',
                        'S'
                )
        );
        variantes.add(
                base.replace(
                        '8',
                        'B'
                )
        );
        variantes.add(
                base.replace(
                        '2',
                        'Z'
                )
        );
        variantes.add(
                base.replace(
                        '4',
                        'A'
                )
        );
        variantes.add(
                base.replace(
                        "11",
                        "H"
                )
        );
        variantes.add(
                base.replace(
                        "RN",
                        "M"
                )
        );
        variantes.add(
                base.replace(
                        "VV",
                        "W"
                )
        );
        return new ArrayList<>(
                variantes
        );
    }

    private static double similaridade(
            @NonNull String consulta,
            @NonNull String alvo
    ) {
        if (consulta.equals(
                alvo
        )) {
            return 1.0;
        }
        if (alvo.contains(
                consulta
        )
                || consulta.contains(
                alvo
        )) {
            int menor =
                    Math.min(
                            consulta.length(),
                            alvo.length()
                    );
            int maior =
                    Math.max(
                            consulta.length(),
                            alvo.length()
                    );
            if (menor >= 4
                    && maior > 0) {
                double cobertura =
                        (double) menor
                                / maior;
                return Math.min(
                        0.97,
                        0.84
                                + cobertura * 0.13
                );
            }
        }
        return Math.max(
                similaridadePorTokens(
                        consulta,
                        alvo
                ),
                similaridadePorCaracteres(
                        consulta,
                        alvo
                )
        );
    }

    private static double similaridadePorTokens(
            @NonNull String consulta,
            @NonNull String alvo
    ) {
        Set<String> consultaTokens =
                tokensSignificativos(
                        consulta
                );
        Set<String> alvoTokens =
                tokensSignificativos(
                        alvo
                );
        if (consultaTokens.isEmpty()
                || alvoTokens.isEmpty()) {
            return 0.0;
        }
        int encontrados =
                0;
        for (String a :
                consultaTokens) {
            for (String b :
                    alvoTokens) {
                if (a.equals(
                        b
                )) {
                    encontrados++;
                    break;
                }
                if (a.length() >= 4
                        && b.length() >= 4
                        && similaridadePorCaracteres(
                        a,
                        b
                ) >= 0.80) {
                    encontrados++;
                    break;
                }
            }
        }
        return (double) encontrados
                / consultaTokens.size();
    }

    private static double similaridadePorCaracteres(
            @NonNull String a,
            @NonNull String b
    ) {
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
                (double) distancia
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
            anterior[j] = j;
        }
        for (int i = 1;
             i <= a.length();
             i++) {
            atual[0] = i;
            for (int j = 1;
                 j <= b.length();
                 j++) {
                int custo =
                        a.charAt(i - 1)
                                == b.charAt(j - 1)
                                ? 0
                                : 1;
                atual[j] =
                        Math.min(
                                Math.min(
                                        atual[j - 1]
                                                + 1,
                                        anterior[j]
                                                + 1
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

    /**
     * Retorna somente os tokens que realmente ajudam
     * a identificar o produto.
     *
     * Palavras como CREME, LOCAO, PARA, DE etc. não servem
     * como identidade.
     *
     * Já:
     *
     * CLINICAL
     * CLEAN
     * MEN
     * NATIVA
     * SPA
     * QUINOA
     *
     * continuam.
     */
    @NonNull
    static Set<String> tokensProdutoIdentidade(
            @NonNull String texto
    ) {
        Set<String> resultado =
                new LinkedHashSet<>();
        for (String token :
                tokensSignificativos(
                        texto
                )) {
            if (token.length() < 3) {
                continue;
            }
            if (isStopwordProduto(
                    token
            )) {
                continue;
            }
            if (isTermoFraco(
                    token
            )) {
                continue;
            }
            /*
             * Capacidades não devem pesar como identidade.
             */
            if (token.matches(
                    "\\d+(?:[.,]\\d+)?(?:ML|G|GR|KG|L)"
            )) {
                continue;
            }
            if (token.matches(
                    "\\d+(?:[.,]\\d+)?FLOZ"
            )) {
                continue;
            }
            resultado.add(
                    token
            );
        }
        return resultado;
    }

    /**
     * Palavras estruturais sem poder de identificação.
     */
    private static boolean isStopwordProduto(
            @NonNull String token
    ) {
        switch (token) {
            case "A":
            case "AS":
            case "O":
            case "OS":
            case "UM":
            case "UMA":
            case "DE":
            case "DO":
            case "DA":
            case "DOS":
            case "DAS":
            case "EM":
            case "NO":
            case "NA":
            case "NOS":
            case "NAS":
            case "E":
            case "PARA":
            case "COM":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    static Set<String> tokensSignificativos(
            @NonNull String texto
    ) {
        Set<String> resultado =
                new LinkedHashSet<>();
        String[] partes =
                texto.split(
                        "\\s+"
                );
        for (String parte :
                partes) {
            String token =
                    parte.trim();
            if (token.length() >= 3) {
                resultado.add(
                        token
                );
            }
        }
        return resultado;
    }

    @NonNull
    static String normalizarCampo(
            String texto
    ) {
        if (texto == null) {
            return "";
        }
        String normalizado =
                ScanTextNormalizer.normalize(
                        texto
                );
        return normalizado
                .replaceAll(
                        "[^A-Z0-9 ]",
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

    static void adicionarUnico(
            @NonNull List<String> lista,
            String valor
    ) {
        if (valor == null) {
            return;
        }
        String normalizado =
                normalizarCampo(
                        valor
                );
        if (!normalizado.isEmpty()
                && !lista.contains(
                normalizado
        )) {
            lista.add(
                    normalizado
            );
        }
    }
}
