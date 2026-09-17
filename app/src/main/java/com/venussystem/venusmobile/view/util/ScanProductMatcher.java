package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;

import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanProductCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ScanProductMatcher {

    private ScanProductMatcher() {
    }

    @NonNull
    public static List<ScanProductCandidate> match(
            @NonNull List<String> ocrLines,
            @NonNull List<Produto> catalogo
    ) {

        List<ScanProductCandidate> candidatos =
                new ArrayList<>();

        List<String> linhas =
                normalizarLinhas(ocrLines);

        for (Produto produto : catalogo) {

            if (produto == null || produto.getId() == null) {
                continue;
            }

            String nomeProduto =
                    normalizar(produto.getName());

            String marca =
                    normalizar(produto.getBrandName());

            if (nomeProduto.isEmpty()) {
                continue;
            }

            double melhorScore = 0.0;
            String melhorEvidencia = "";

            /*
             * 1. Comparamos o nome completo do produto
             * contra cada linha do OCR.
             */
            for (String linha : linhas) {

                double scoreNome =
                        similaridade(linha, nomeProduto);

                if (scoreNome > melhorScore) {

                    melhorScore = scoreNome;

                    melhorEvidencia =
                            "nome=" + linha;
                }
            }

            /*
             * 2. Procuramos também o nome do produto dentro
             * do texto OCR consolidado.
             */
            String textoCompleto =
                    String.join(" ", linhas);

            if (!nomeProduto.isEmpty()
                    && textoCompleto.contains(nomeProduto)) {

                melhorScore =
                        Math.max(
                                melhorScore,
                                0.95
                        );

                melhorEvidencia =
                        "nome_contido_no_ocr";
            }

            /*
             * 3. Se a marca também aparecer, aumentamos a
             * confiança.
             */
            if (!marca.isEmpty()
                    && textoCompleto.contains(marca)) {

                if (melhorScore > 0.0) {
                    melhorScore += 0.10;
                }
            }

            /*
             * Limita o score a 1.0.
             */
            melhorScore =
                    Math.min(
                            melhorScore,
                            1.0
                    );

            /*
             * Só cria candidato se houver uma correspondência
             * minimamente relevante.
             */
            if (melhorScore >= 0.45) {

                candidatos.add(
                        new ScanProductCandidate(
                                produto,
                                melhorScore,
                                melhorEvidencia
                        )
                );
            }
        }

        candidatos.sort(
                Comparator.comparingDouble(
                        ScanProductCandidate::getScore
                ).reversed()
        );

        return limitar(candidatos, 5);
    }

    @NonNull
    private static List<String> normalizarLinhas(
            @NonNull List<String> linhas
    ) {

        List<String> resultado =
                new ArrayList<>();

        Set<String> unicas =
                new HashSet<>();

        for (String linha : linhas) {

            if (linha == null) {
                continue;
            }

            String normalizada =
                    normalizar(linha);

            if (normalizada.length() < 3) {
                continue;
            }

            if (unicas.add(normalizada)) {
                resultado.add(normalizada);
            }
        }

        return resultado;
    }

    @NonNull
    private static String normalizar(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return ScanTextNormalizer.normalize(
                texto
        );
    }

    private static double similaridade(
            @NonNull String a,
            @NonNull String b
    ) {

        if (a.equals(b)) {
            return 1.0;
        }

        if (a.contains(b) || b.contains(a)) {

            int menor =
                    Math.min(
                            a.length(),
                            b.length()
                    );

            int maior =
                    Math.max(
                            a.length(),
                            b.length()
                    );

            return (double) menor / maior;
        }

        return jaccardPorPalavras(a, b);
    }

    private static double jaccardPorPalavras(
            @NonNull String a,
            @NonNull String b
    ) {

        String[] palavrasA =
                a.split("\\s+");

        String[] palavrasB =
                b.split("\\s+");

        Set<String> conjuntoA =
                new HashSet<>();

        Set<String> conjuntoB =
                new HashSet<>();

        for (String palavra : palavrasA) {
            if (palavra.length() >= 2) {
                conjuntoA.add(palavra);
            }
        }

        for (String palavra : palavrasB) {
            if (palavra.length() >= 2) {
                conjuntoB.add(palavra);
            }
        }

        if (conjuntoA.isEmpty()
                || conjuntoB.isEmpty()) {

            return 0.0;
        }

        Set<String> intersecao =
                new HashSet<>(conjuntoA);

        intersecao.retainAll(conjuntoB);

        Set<String> uniao =
                new HashSet<>(conjuntoA);

        uniao.addAll(conjuntoB);

        return (double) intersecao.size()
                / uniao.size();
    }

    @NonNull
    private static List<ScanProductCandidate> limitar(
            @NonNull List<ScanProductCandidate> candidatos,
            int limite
    ) {

        if (candidatos.size() <= limite) {
            return candidatos;
        }

        return new ArrayList<>(
                candidatos.subList(0, limite)
        );
    }
}