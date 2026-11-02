package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import com.venussystem.venusmobile.model.ScanFrontData;
import java.util.ArrayList;
import java.util.List;
import static com.venussystem.venusmobile.view.util.ScanFrontBrandExtractor.adicionarMarca;
import static com.venussystem.venusmobile.view.util.ScanFrontBrandExtractor.completarMarcaEmLinhas;
import static com.venussystem.venusmobile.view.util.ScanFrontBrandExtractor.extrairMarcaCompostaNasLinhas;
import static com.venussystem.venusmobile.view.util.ScanFrontBrandExtractor.extrairPossivelMarca;
import static com.venussystem.venusmobile.view.util.ScanFrontBrandExtractor.limparMarcas;
import static com.venussystem.venusmobile.view.util.ScanFrontBrandExtractor.priorizarMarcaCosmeticaConhecida;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.adicionarDescritoresDetectados;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.adicionarReferenciasExpandidas;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.adicionarTokensProduto;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.extrairApresentacoes;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.extrairDescritoresProduto;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.extrairReferenciasNumericas;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.isFraseProdutoDistintiva;
import static com.venussystem.venusmobile.view.util.ScanFrontProductExtractor.limparProdutos;
import static com.venussystem.venusmobile.view.util.ScanFrontText.adicionarProduto;
import static com.venussystem.venusmobile.view.util.ScanFrontText.extrairCapacidade;
import static com.venussystem.venusmobile.view.util.ScanFrontText.extrairConcentracao;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isLinhaDeRuidoForte;
import static com.venussystem.venusmobile.view.util.ScanFrontText.isSomenteNumero;
import static com.venussystem.venusmobile.view.util.ScanFrontText.normalizar;
import static com.venussystem.venusmobile.view.util.ScanFrontText.removerDadosNumericos;

public final class ScanFrontExtractor {

    private ScanFrontExtractor() {
    }

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
        adicionarDescritoresDetectados(
                normalizadas,
                productCandidates
        );
        adicionarReferenciasExpandidas(
                normalizadas,
                productCandidates
        );
        extrairReferenciasNumericas(
                normalizadas,
                productCandidates
        );
        limparMarcas(
                brandCandidates
        );
        priorizarMarcaCosmeticaConhecida(
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
}
