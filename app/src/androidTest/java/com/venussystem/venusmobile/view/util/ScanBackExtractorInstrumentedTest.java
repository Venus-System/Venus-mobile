package com.venussystem.venusmobile.view.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;


import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanOcrToken;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ScanBackExtractorInstrumentedTest {

    @Test
    public void keepsResidualContactAfterQuestionsOutOfIngredients() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTS: AQUA, PARFUM",
                "QUESTIONS SUPPORT@MYSPRAYGROUP OR CALL 0800 123 4567"
        ));

        assertTrue(data.isIngredientSectionFound());
        assertEquals(2, data.getIngredients().size());
        assertTrue(data.getContact().contains("SUPPORT@MYSPRAYGROUP"));
    }

    @Test
    public void acceptsPlausibleBatchAfterExplicitHeading() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTS: AQUA, PARFUM",
                "LOT: ABC123"
        ));

        assertEquals("ABC123", data.getBatch());
        assertEquals(Arrays.asList("AQUA", "PARFUM"), names(data));
    }

    @Test
    public void rejectsAlphabeticFalsePositiveAsBatch() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTS: AQUA, PARFUM",
                "LOTE: HANDMXPIRATION"
        ));

        assertEquals("", data.getBatch());
    }

    @Test
    public void reconstructsAlignedInciAndTranslationColumns() {
        List<String> lines = Arrays.asList(
                "INGREDIENTS",
                "ALOE EXTRATO",
                "BARBADENSIS LEAF EXTRACT DA FOLHA",
                "AQUA AGUA",
                "QUESTIONS SUPPORT@MYSPRAYGROUP"
        );

        List<ScanOcrToken> tokens = Arrays.asList(
                token("INGREDIENTS", 0, 0, 130, 20, 0, 0),
                token("ALOE", 0, 30, 55, 50, 1, 0),
                token("EXTRATO", 300, 30, 380, 50, 1, 1),
                token("BARBADENSIS", 0, 55, 115, 75, 2, 0),
                token("LEAF", 120, 55, 160, 75, 2, 1),
                token("EXTRACT", 165, 55, 235, 75, 2, 2),
                token("DA", 300, 55, 325, 75, 2, 3),
                token("FOLHA", 330, 55, 390, 75, 2, 4),
                token("AQUA", 0, 80, 55, 100, 3, 0),
                token("AGUA", 300, 80, 355, 100, 3, 1),
                token("QUESTIONS SUPPORT@MYSPRAYGROUP", 0, 120, 390, 140, 4, 0)
        );

        ScanBackData data = ScanBackExtractor.extract(
                new ScanOcrResult(String.join("\n", lines), lines, tokens)
        );

        assertEquals(2, data.getIngredients().size());
        assertEquals(
                "ALOE BARBADENSIS LEAF EXTRACT (EXTRATO DA FOLHA)",
                data.getIngredients().get(0).getRawName()
        );
        assertEquals("AQUA (AGUA)", data.getIngredients().get(1).getRawName());
        assertEquals("UNRESOLVED", data.getIngredients().get(0).getStatus());
    }

    @Test
    public void rejectsPackageCodeAsQuantityAndContact() {
        for (String line : Arrays.asList("80133324 G", "CONTEUDO: 80133324 G")) {
            ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                    "INGREDIENTES: AQUA, PARFUM", line));
            assertEquals("", data.getNetContent());
            assertEquals("", data.getContact());
        }
    }

    @Test
    public void keepsOrdinaryQuantityAndExplicitTelephone() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: AQUA, PARFUM", "CONTEUDO: 150 ML", "TEL: 33334444"));
        assertEquals("150 ML", data.getNetContent());
        assertEquals(Arrays.asList("AQUA", "PARFUM"), names(data));
        assertTrue(data.getContact().contains("33334444"));
        assertEquals("90 G", ScanBackExtractor.extract(Arrays.asList("90 G")).getNetContent());
    }

    @Test
    public void stopsBeforeSafetyAndMarketingText() {
        for (String stop : Arrays.asList("PROCURAR ORIENTAÇÃO MEDICA",
                "SE OCORRER ALERGIA, SUSPENDER", "NÃO CONTÉM CFC",
                "SSO DE 15OM S WS VENDIDO DO MERCADO DE DESODORANTES NO BRASL")) {
            ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                    "INGREDIENTES: AQUA, PARFUM", stop, "OUTRA FRASE"));
            assertEquals(stop, 2, data.getIngredients().size());
            assertEquals("PARFUM", data.getIngredients().get(1).getRawName());
        }
    }

    @Test
    public void inlineSafetyStopsCollectionOfSubsequentLines() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: AQUA, PARFUM. PROCURAR ORIENTAÇÃO MEDICA", "OUTRA FRASE"));
        assertEquals(2, data.getIngredients().size());
    }

    @Test
    public void ordersShuffledOcrBeforeFindingSection() {
        // Synthetic geometry resembling the single-paragraph can, not a photo replay.
        List<String> lines = Arrays.asList("DIMETHICONOL, LIMONENE.",
                "PROCURAR ORIENTAÇÃO MEDICA", "ISOBUTANE, PROPANE,",
                "KiREDIENTES: BUTANE,", "80133324 G");
        List<ScanOcrToken> tokens = Arrays.asList(
                token(lines.get(0), 0, 110, 300, 130, 0, 0),
                token(lines.get(1), 0, 0, 300, 20, 1, 0),
                token(lines.get(2), 220, 80, 520, 100, 2, 0),
                token(lines.get(3), 0, 80, 210, 100, 3, 0),
                token(lines.get(4), 0, 200, 150, 220, 4, 0));
        String fullText = String.join("\n", lines);
        ScanBackData data = ScanBackExtractor.extract(new ScanOcrResult(fullText, lines, tokens));
        assertEquals(Arrays.asList("BUTANE", "ISOBUTANE", "PROPANE", "DIMETHICONOL", "LIMONENE"),
                names(data));
        assertEquals(fullText, data.getFullText());
        assertEquals(lines, data.getLines());
        assertEquals("", data.getNetContent());
        assertEquals("", data.getContact());
    }

    @Test
    public void wideSpacesBetweenIngredientsAreNotTranslationColumns() {
        List<String> lines = Arrays.asList("INGREDIENTES", "AQUA PARFUM", "BUTANE PROPANE");
        List<ScanOcrToken> tokens = Arrays.asList(
                token(lines.get(0), 0, 0, 150, 20, 0, 0),
                token("AQUA", 0, 30, 60, 50, 1, 0),
                token("PARFUM", 300, 30, 390, 50, 1, 1),
                token("BUTANE", 0, 60, 80, 80, 2, 0),
                token("PROPANE", 300, 60, 400, 80, 2, 1));
        assertEquals(names(ScanBackExtractor.extract(lines)), names(ScanBackExtractor.extract(
                new ScanOcrResult(String.join("\n", lines), lines, tokens))));
    }

    @Test
    public void partialOrUnreliableGeometryFallsBackWithoutDroppingText() {
        List<String> lines = Arrays.asList("INGREDIENTES: AQUA, PARFUM", "LIMONENE");
        List<String> expected = names(ScanBackExtractor.extract(lines));
        for (List<ScanOcrToken> tokens : Arrays.asList(
                Arrays.asList(token(lines.get(0), 0, 30, 300, 50, 0, 0)),
                Arrays.asList(token(lines.get(0), 0, 30, 300, 50, 0, 0),
                        new ScanOcrToken(lines.get(1), new Rect(0, 0, 100, 20), .2f, 0, 0, 1, 0)),
                Arrays.asList(token(lines.get(0), 0, 30, 300, 50, 0, 0),
                        new ScanOcrToken(lines.get(1), new Rect(0, 0, 100, 20), .9f, 90, 0, 1, 0)))) {
            assertTrue(ScanBackSpatialLayout.rows(lines, tokens).isEmpty());
            assertEquals(expected, names(ScanBackExtractor.extract(
                    new ScanOcrResult(String.join("\n", lines), lines, tokens))));
        }
    }

    @Test
    public void preservesUnrecognizedOcrSpelling() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: AQUA, AUIOATE, 0CTYLDODECAN"));
        assertEquals(Arrays.asList("AQUA", "AUIOATE", "0CTYLDODECAN"), names(data));
        data.getIngredients().forEach(i -> assertEquals("UNRESOLVED", i.getStatus()));
    }

    @Test
    public void ocrDotsDoNotStopTheRemainingIngredientLines() {
        // Synthetic reproduction of STOP=URL, not the missing BACK_LINE[21] capture.
        List<String> lines = Arrays.asList(
                "INGREDIENTES: BUTANE, ISOBUTANE, PROPANE, PPG-14 BUTL ETE,",
                "CYCLOPENTASILOXANE.ALUMINUM SESQUICHLOROHYDRATE, PARFUM,",
                "GLYCINE, AQUA, CALCIUM CHLORIDE,",
                "DIMETHICONOL, HEXYL CINNAMAL, LIMONENE.",
                "www.example.com.br", "TEXTO ADMINISTRATIVO");
        ScanBackData data = ScanBackExtractor.extract(lines);
        assertTrue(data.getIngredientsRawText().contains("CYCLOPENTASILOXANE.ALUMINUM"));
        assertTrue(names(data).contains("GLYCINE"));
        assertTrue(names(data).contains("LIMONENE"));
        assertTrue(data.getIngredients().size() > 4);
        assertTrue(!data.getIngredientsRawText().contains("example"));
        assertTrue(!data.getIngredientsRawText().contains("ADMINISTRATIVO"));
    }

    @Test
    public void unknownDottedOcrTextIsNotDiscardedAsAWebsite() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: AQUA, XANTHAM.GUN, AUIOATE.OCTYLDODECAN,",
                "PARFUM, LIMONENE"));
        assertTrue(data.getIngredientsRawText().contains("XANTHAM.GUN"));
        assertTrue(data.getIngredientsRawText().contains("AUIOATE.OCTYLDODECAN"));
        assertTrue(names(data).stream().anyMatch(n -> n.contains("XANTHAM")));
        assertTrue(names(data).stream().anyMatch(n -> n.contains("AUIOATE")));
        assertTrue(names(data).contains("LIMONENE"));
    }

    @Test
    public void actualWebsitesStillStopIngredientCollection() {
        for (String site : Arrays.asList("www.example.com.br", "https://example.beauty/info",
                "http://example.org", "example.com", "example.com.br", "example.net",
                "www.example.beauty")) {
            ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                    "INGREDIENTES: AQUA, PARFUM", site, "TEXTO ADMINISTRATIVO"));
            assertEquals(site, Arrays.asList("AQUA", "PARFUM"), names(data));
        }
    }

    @Test
    public void keepsIngredientsBeforeWebsiteOnTheSameLine() {
        for (String site : Arrays.asList("www.example.com.br", "https://example.beauty",
                "example.com", "SITE: www.example.com.br")) {
            ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                    "INGREDIENTES: AQUA, PARFUM", "DIMETHICONOL, LIMONENE. " + site,
                    "TEXTO ADMINISTRATIVO"));
            assertEquals(site, Arrays.asList("AQUA", "PARFUM", "DIMETHICONOL", "LIMONENE"), names(data));
        }
    }

    @Test
    public void keepsInlineIngredientsWhenWebsiteIsOnHeadingLine() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: AQUA, PARFUM. www.example.com", "TEXTO ADMINISTRATIVO"));
        assertEquals(Arrays.asList("AQUA", "PARFUM"), names(data));
    }

    @Test
    public void preservesRemainingIngredientsFromSeptember29OcrCapture() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "NGREOIENTES: BUTANE, ISOBUTANE, PROPANE, PPG-14 BUTL ETE",
                "DO.OPENTASILOXANE, ALUMINUM SESQUICHLOROHYDRATE, PARIN",
                "EJANTHUS ANNUUS SEED OIL. DISTEARDIMONIUM HECTORITE CI2494",
                "tOATE, GLYCINE, AQUA, CALCIUM CHLORIDE, 0CTYLDODECANO, MI",
                "ROPYLENE CARBONATE, DIMETHICONOL, ALPHA-ISOMETHYL ONN",
                "INIO, HEXYL CINNAMAL, LIMONENE.",
                "Y33824C", "WOFENSNO", "Onilevew", "69657381"));
        assertTrue(data.getIngredientsRawText().contains("DO.OPENTASILOXANE"));
        assertTrue(data.getIngredientsRawText().contains("ALPHA-ISOMETHYL ONN"));
        assertTrue(names(data).contains("GLYCINE"));
        assertTrue(names(data).contains("DIMETHICONOL"));
        assertTrue(names(data).contains("LIMONENE"));
        assertTrue(data.getIngredients().size() > 4);
        data.getIngredients().forEach(i -> assertEquals("UNRESOLVED", i.getStatus()));
    }

    private static List<String> names(ScanBackData data) {
        List<String> names = new java.util.ArrayList<>();
        data.getIngredients().forEach(i -> names.add(i.getRawName()));
        return names;
    }

    private static ScanOcrToken token(
            String text,
            int left,
            int top,
            int right,
            int bottom,
            int lineIndex,
            int elementIndex
    ) {
        return new ScanOcrToken(
                text,
                new Rect(left, top, right, bottom),
                0.9f,
                0f,
                0,
                lineIndex,
                elementIndex
        );
    }
}
