package com.venussystem.venusmobile.repository;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static com.venussystem.venusmobile.repository.ScanCatalogText.adicionarUnico;
import static com.venussystem.venusmobile.repository.ScanCatalogText.isTermoFraco;
import static com.venussystem.venusmobile.repository.ScanCatalogText.melhorSimilaridadeOCR;
import static com.venussystem.venusmobile.repository.ScanCatalogText.normalizarCampo;
import static com.venussystem.venusmobile.repository.ScanCatalogText.tokensProdutoIdentidade;
import static com.venussystem.venusmobile.repository.ScanCatalogText.tokensSignificativos;

final class ScanCatalogScoring {
    private ScanCatalogScoring() { }

    private static final double TOKEN_MATCH_THRESHOLD = 0.84;

    static ProductEvidence pontuarProduto(
            @NonNull List<String> candidatos,
            @NonNull List<String> termosFortes,
            @NonNull List<String> frases,
            @NonNull String nomeProduto
    ) {
        Set<String> tokensAlvo =
                tokensProdutoIdentidade(
                        nomeProduto
                );
        if (tokensAlvo.isEmpty()) {
            return null;
        }
        int tokensAlvoCasados = 0;
        int tokensAlvoExatos = 0;
        double somaMelhoresTokensAlvo = 0.0;
        String melhorTermo = null;
        double melhorTermoScore = 0.0;
        Set<String> tokensOCR =
                new LinkedHashSet<>();
        for (String candidato :
                candidatos) {
            tokensOCR.addAll(
                    tokensSignificativos(
                            candidato
                    )
            );
        }
        Set<String> tokensOCRUsados =
                new LinkedHashSet<>();
        for (String tokenAlvo :
                tokensAlvo) {
            double melhorDoAlvo = 0.0;
            String melhorOCRDoAlvo = null;
            boolean melhorFoiExato = false;
            for (String tokenOCR :
                    tokensOCR) {
                if (tokensOCRUsados.contains(
                        tokenOCR
                )) {
                    continue;
                }
                if (tokenOCR.equals(
                        tokenAlvo
                )) {
                    melhorDoAlvo = 1.0;
                    melhorOCRDoAlvo = tokenOCR;
                    melhorFoiExato = true;
                    break;
                }
            }
            if (!melhorFoiExato) {
                for (String tokenOCR :
                        tokensOCR) {
                    if (tokensOCRUsados.contains(
                            tokenOCR
                    )) {
                        continue;
                    }
                    double score =
                            melhorSimilaridadeOCR(
                                    tokenOCR,
                                    tokenAlvo,
                                    false
                            );
                    if (score > melhorDoAlvo) {
                        melhorDoAlvo =
                                score;
                        melhorOCRDoAlvo =
                                tokenOCR;
                    }
                }
            }
            if (melhorDoAlvo
                    >= TOKEN_MATCH_THRESHOLD) {
                tokensAlvoCasados++;
                somaMelhoresTokensAlvo +=
                        melhorDoAlvo;
                if (melhorFoiExato) {
                    tokensAlvoExatos++;
                }
                if (melhorOCRDoAlvo != null) {
                    tokensOCRUsados.add(
                            melhorOCRDoAlvo
                    );
                }
                if (melhorDoAlvo
                        > melhorTermoScore) {
                    melhorTermoScore =
                            melhorDoAlvo;
                    melhorTermo =
                            melhorOCRDoAlvo;
                }
            }
        }
        if (tokensAlvoCasados == 0) {
            return null;
        }
        double targetCoverage =
                (double) tokensAlvoCasados
                        / tokensAlvo.size();
        double targetSimilarityAverage =
                somaMelhoresTokensAlvo
                        / tokensAlvo.size();
        double exactTargetCoverage =
                (double) tokensAlvoExatos
                        / tokensAlvo.size();
        int termosFortesCasados = 0;
        for (String termoOCR :
                termosFortes) {
            double melhorDoTermo = 0.0;
            for (String tokenAlvo :
                    tokensAlvo) {
                double score =
                        melhorSimilaridadeOCR(
                                termoOCR,
                                tokenAlvo,
                                false
                        );
                if (score > melhorDoTermo) {
                    melhorDoTermo = score;
                }
            }
            if (melhorDoTermo
                    >= TOKEN_MATCH_THRESHOLD) {
                termosFortesCasados++;
            }
        }
        double melhorFraseScore = 0.0;
        String melhorFrase = null;
        for (String frase :
                frases) {
            if (frase == null
                    || frase.trim().isEmpty()) {
                continue;
            }
            if (frase.trim().matches(
                    "(?i)\\d+(?:[.,]\\d+)?\\s*(?:ML|G|GR|KG|L|FLOZ)")) {
                continue;
            }
            int quantidadePalavras =
                    frase
                            .trim()
                            .split("\\s+")
                            .length;
            if (quantidadePalavras < 2) {
                continue;
            }
            double score =
                    scoreFraseContidaNoProduto(
                            frase,
                            nomeProduto
                    );
            if (score > melhorFraseScore) {
                melhorFraseScore =
                        score;
                melhorFrase =
                        frase;
            }
        }
        double score =
                targetCoverage * 0.35
                        + targetSimilarityAverage * 0.15
                        + exactTargetCoverage * 0.25
                        + melhorFraseScore * 0.25;
        if (tokensAlvoExatos >= 2) {
            score += 0.20;
        }
        if (tokensAlvoExatos >= 2
                && targetCoverage >= 0.66) {
            score += 0.10;
        }
        if (tokensAlvoExatos >= 3) {
            score += 0.10;
        }
        if (score > 1.0) {
            score = 1.0;
        }
        String candidatoFinal =
                melhorFrase != null
                        ? melhorFrase
                        : melhorTermo;
        return new ProductEvidence(
                score,
                candidatoFinal,
                Math.max(
                        termosFortesCasados,
                        tokensAlvoExatos
                ),
                melhorTermoScore,
                tokensAlvoExatos
        );
    }

    @NonNull
    static List<String> extrairTermosFortes(
            @NonNull List<String> candidatos
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String candidato :
                candidatos) {
            if (candidato == null) {
                continue;
            }
            for (String token :
                    tokensSignificativos(
                            candidato
                    )) {
                if (isTermoFraco(token)) {
                    continue;
                }
                if (token.length() < 3) {
                    continue;
                }
                if (!resultado.contains(token)) {
                    resultado.add(
                            token
                    );
                }
            }
        }
        return resultado;
    }

    @NonNull
    static List<String> gerarFrasesProduto(
            @NonNull List<String> candidatos
    ) {
        List<String> resultado =
                new ArrayList<>();
        for (String candidato :
                candidatos) {
            if (candidato == null) {
                continue;
            }
            String normalizado =
                    normalizarCampo(
                            candidato
                    );
            String[] tokens =
                    normalizado.split(
                            "\\s+"
                    );
            if (tokens.length < 2) {
                continue;
            }
            adicionarUnico(
                    resultado,
                    normalizado
            );
            for (int i = 0;
                 i < tokens.length - 1;
                 i++) {
                adicionarUnico(
                        resultado,
                        tokens[i]
                                + " "
                                + tokens[i + 1]
                );
                if (i < tokens.length - 2) {
                    adicionarUnico(
                            resultado,
                            tokens[i]
                                    + " "
                                    + tokens[i + 1]
                                    + " "
                                    + tokens[i + 2]
                    );
                }
            }
        }
        return resultado;
    }

    private static double scoreFraseContidaNoProduto(
            @NonNull String frase,
            @NonNull String nomeProduto
    ) {
        String consulta =
                normalizarCampo(
                        frase
                );
        String alvo =
                normalizarCampo(
                        nomeProduto
                );
        String[] tokensConsulta =
                consulta.split("\\s+");
        if (tokensConsulta.length < 2) {
            return 0.0;
        }
        if (alvo.contains(
                consulta
        )) {
            if (tokensConsulta.length >= 3) {
                return 1.0;
            }
            return 0.97;
        }
        String[] tokensAlvo =
                alvo.split("\\s+");
        int casados =
                0;
        double soma =
                0.0;
        for (String tokenConsulta :
                tokensConsulta) {
            if (tokenConsulta.length() < 3) {
                continue;
            }
            double melhor =
                    0.0;
            for (String tokenAlvo :
                    tokensAlvo) {
                double score =
                        melhorSimilaridadeOCR(
                                tokenConsulta,
                                tokenAlvo,
                                false
                        );
                if (score > melhor) {
                    melhor = score;
                }
            }
            if (melhor
                    >= TOKEN_MATCH_THRESHOLD) {
                casados++;
                soma += melhor;
            }
        }
        if (casados < 2) {
            return 0.0;
        }
        double cobertura =
                (double) casados
                        / tokensConsulta.length;
        double media =
                soma
                        / tokensConsulta.length;
        return Math.min(
                0.95,
                cobertura * 0.70
                        + media * 0.30
        );
    }

    static class ProductEvidence {
        final double score;
        final String candidatoOCR;
        final int termosFortesCasados;
        final double melhorTermoScore;
        final int tokensAlvoExatos;
        ProductEvidence(
                double score,
                String candidatoOCR,
                int termosFortesCasados,
                double melhorTermoScore,
                int tokensAlvoExatos
        ) {
            this.score =
                    score;
            this.candidatoOCR =
                    candidatoOCR;
            this.termosFortesCasados =
                    termosFortesCasados;
            this.melhorTermoScore =
                    melhorTermoScore;
            this.tokensAlvoExatos =
                    tokensAlvoExatos;
        }
    }
}
