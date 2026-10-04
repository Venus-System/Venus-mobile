package com.venussystem.venusmobile.view.util;

import androidx.annotation.NonNull;
import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.model.ScanBackIngredientCandidate;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanOcrToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.venussystem.venusmobile.view.util.ScanBackCompany.extrairContato;
import static com.venussystem.venusmobile.view.util.ScanBackCompany.extrairEndereco;
import static com.venussystem.venusmobile.view.util.ScanBackCompany.extrairFabricante;
import static com.venussystem.venusmobile.view.util.ScanBackCompany.extrairPais;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.IngredientSection;
import static com.venussystem.venusmobile.view.util.ScanBackIngredientSection.localizarSecaoIngredientes;
import static com.venussystem.venusmobile.view.util.ScanBackIngredients.extrairIngredientes;
import static com.venussystem.venusmobile.view.util.ScanBackIngredients.gerarAssinaturaOCR;
import static com.venussystem.venusmobile.view.util.ScanBackLabelSections.extrairClaims;
import static com.venussystem.venusmobile.view.util.ScanBackLabelSections.extrairOtherText;
import static com.venussystem.venusmobile.view.util.ScanBackLabelSections.extrairSeguranca;
import static com.venussystem.venusmobile.view.util.ScanBackLabelSections.extrairUso;
import static com.venussystem.venusmobile.view.util.ScanBackMetadata.extrairBarcode;
import static com.venussystem.venusmobile.view.util.ScanBackMetadata.extrairConteudo;
import static com.venussystem.venusmobile.view.util.ScanBackMetadata.extrairLote;
import static com.venussystem.venusmobile.view.util.ScanBackMetadata.extrairRegistro;
import static com.venussystem.venusmobile.view.util.ScanBackRules.EMPTY;
import static com.venussystem.venusmobile.view.util.ScanBackText.joinLines;
import static com.venussystem.venusmobile.view.util.ScanBackText.normalizarLinhas;

/**
 * Parser estrutural do verso de embalagens.
 *
 * V7.1
 *
 * Objetivos:
 *
 * 1) preservar a maior quantidade possível de texto da composição;
 * 2) não transformar texto administrativo em ingrediente;
 * 3) não inventar fabricante, lote, país, conteúdo ou código de barras;
 * 4) tolerar OCR ruim em português/espanhol/inglês;
 * 5) entregar candidatos de ingredientes para um matcher posterior;
 * 6) impedir vazamento de telefone, e-mail, URL, CNPJ e CEP para ingredientes;
 * 7) tratar marcadores administrativos colados ao final de um ingrediente;
 * 8) manter candidatos OCR imperfeitos como UNRESOLVED.
 *
 * IMPORTANTE:
 *
 * Esta classe NÃO consulta banco, API, Mongo ou Cloudinary.
 *
 * O status inicial dos ingredientes permanece UNRESOLVED.
 *
 * Exemplo:
 *
 * OCR:
 *
 * INGREDIENTES: AQUA, GLYCNE, PARFUM
 * LINALOOL .FABRICADO
 * POR UNILEVER BRASIL LTDA
 * AUTFUNC: 2.05610-6
 *
 * O parser deve preservar:
 *
 * AQUA
 * GLYCNE
 * PARFUM
 * LINALOOL
 *
 * e impedir:
 *
 * FABRICADO
 * UNILEVER
 * AUTFUNC
 * 2.05610-6
 *
 * de entrarem como ingredientes.
 */
public final class ScanBackExtractor {

    private ScanBackExtractor() {
    }

    /*
     * ================================================================
     * ENTRADAS PÚBLICAS
     * ================================================================
     */
    @NonNull
    public static ScanBackData extract(
            @NonNull ScanOcrResult ocr
    ) {
        return extract(
                ocr.getFullText(),
                ocr.getLines(),
                ocr.getTokens()
        );
    }

    @NonNull
    public static ScanBackData extract(
            @NonNull List<String> ocrLines
    ) {
        String full =
                joinLines(
                        ocrLines
                );
        return extract(
                full,
                ocrLines
        );
    }

    @NonNull
    public static ScanBackData extract(
            @NonNull String fullText,
            @NonNull List<String> sourceLines
    ) {
        return extract(
                fullText,
                sourceLines,
                Collections.emptyList()
        );
    }

    @NonNull
    public static ScanBackData extract(
            @NonNull String fullText,
            @NonNull List<String> sourceLines,
            @NonNull List<ScanOcrToken> spatialTokens
    ) {
        List<String> originalLines =
                normalizarLinhas(
                        sourceLines
                );
        List<String> lines = originalLines;
        List<ScanOcrToken> orderedTokens = new ArrayList<>();
        List<List<ScanOcrToken>> visualRows = ScanBackSpatialLayout.rows(sourceLines, spatialTokens);
        if (!visualRows.isEmpty()) {
            lines = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < visualRows.size(); rowIndex++) {
                List<ScanOcrToken> row = visualRows.get(rowIndex);
                lines.add(ScanBackSpatialLayout.join(row));
                for (int element = 0; element < row.size(); element++) {
                    ScanOcrToken t = row.get(element);
                    orderedTokens.add(new ScanOcrToken(t.getText(), t.getBoundingBox(),
                            t.getConfidence(), t.getRotation(), t.getBlockIndex(), rowIndex, element));
                }
            }
        }
        IngredientSection section =
                localizarSecaoIngredientes(
                        lines
                );
        List<ScanBackIngredientCandidate> ingredients =
                extrairIngredientes(
                        section,
                        orderedTokens
                );
        String manufacturer =
                extrairFabricante(
                        lines
                );
        String address =
                extrairEndereco(
                        lines,
                        manufacturer
                );
        String contact =
                extrairContato(
                        lines
                );
        String country =
                extrairPais(
                        lines,
                        manufacturer
                );
        String batch =
                extrairLote(
                        lines
                );
        String registration =
                extrairRegistro(
                        lines
                );
        String netContent =
                extrairConteudo(
                        lines
                );
        String usage =
                extrairUso(
                        lines,
                        section
                );
        String precautions =
                extrairSeguranca(
                        lines,
                        false,
                        section
                );
        String warnings =
                extrairSeguranca(
                        lines,
                        true,
                        section
                );
        List<String> claims =
                extrairClaims(
                        lines
                );
        String barcode =
                extrairBarcode(
                        lines
                );
        String otherText =
                extrairOtherText(
                        lines,
                        section
                );
        String signature =
                gerarAssinaturaOCR(
                        ingredients
                );
        return new ScanBackData(
                fullText == null
                        ? EMPTY
                        : fullText,
                originalLines,
                section.found,
                section.rawText,
                ingredients,
                manufacturer,
                address,
                contact,
                country,
                batch,
                registration,
                netContent,
                usage,
                precautions,
                warnings,
                claims,
                barcode,
                otherText,
                signature
        );
    }
}
