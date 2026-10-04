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
    public void lowSpellingConfidenceInCompanyDoesNotDisableIngredientGeometry() {
        List<String> lines = Arrays.asList("LIMONENE.", "INDUSTRIAL LTDA",
                "INGREDIENTES: AQUA,", "GLYCINE,");
        List<ScanOcrToken> tokens = Arrays.asList(
                token(lines.get(0), 0, 120, 220, 140, 0, 0),
                new ScanOcrToken(lines.get(1), new Rect(0, 0, 220, 20), .16f, 0, 0, 1, 0),
                token(lines.get(2), 0, 60, 220, 80, 2, 0),
                token(lines.get(3), 0, 90, 220, 110, 3, 0));
        ScanBackData data = ScanBackExtractor.extract(new ScanOcrResult(String.join("\n", lines), lines, tokens));
        assertEquals(Arrays.asList("AQUA", "GLYCINE", "LIMONENE"), names(data));
        assertEquals(lines, data.getLines());
        assertEquals(String.join("\n", lines), data.getFullText());
    }

    @Test
    public void lowConfidenceIngredientTextIsRetainedWhenGeometryIsValid() {
        List<String> lines = Arrays.asList("INGREDIENTES: AQUA,", "OCTYLDO0ECAML M");
        List<ScanOcrToken> tokens = Arrays.asList(
                token(lines.get(0), 0, 0, 220, 20, 0, 0),
                new ScanOcrToken(lines.get(1), new Rect(0, 30, 220, 50), .2f, 0, 0, 1, 0));
        assertTrue(!ScanBackSpatialLayout.rows(lines, tokens).isEmpty());
        ScanBackData data = ScanBackExtractor.extract(new ScanOcrResult(String.join("\n", lines), lines, tokens));
        assertTrue(names(data).contains("OCTYLDO0ECAML M"));
    }

    @Test
    public void toleratesPerspectiveRotationWithinTwentyDegrees() {
        List<String> lines = Arrays.asList("INGREDIENTES: AQUA,", "GLYCINE,", "LIMONENE.");
        List<ScanOcrToken> tokens = Arrays.asList(
                new ScanOcrToken(lines.get(0), new Rect(0, 0, 220, 20), .9f, -17.3f, 0, 0, 0),
                new ScanOcrToken(lines.get(1), new Rect(0, 30, 220, 50), .9f, -5.1f, 0, 1, 0),
                new ScanOcrToken(lines.get(2), new Rect(0, 60, 220, 80), .9f, .2f, 0, 2, 0));
        assertTrue(!ScanBackSpatialLayout.rows(lines, tokens).isEmpty());
        ScanBackData data = ScanBackExtractor.extract(
                new ScanOcrResult(String.join("\n", lines), lines, tokens));
        assertEquals(Arrays.asList("AQUA", "GLYCINE", "LIMONENE"), names(data));
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

    @Test
    public void peripheralVerticalCodeDoesNotDisableShuffledIngredientGeometry() {
        // Synthetic coordinates: the device log does not include bounding boxes.
        for (float angle : new float[] {-90, 90}) {
            List<String> lines = Arrays.asList("LIMONENE.", "69657381",
                    "INGREDIENTES: AQUA,", "GLYCINE,", "Unilever");
            List<ScanOcrToken> tokens = Arrays.asList(
                    token(lines.get(0), 0, 90, 220, 110, 0, 0),
                    new ScanOcrToken(lines.get(1), new Rect(260, 30, 280, 130), .2f, angle, 0, 1, 0),
                    token(lines.get(2), 0, 30, 220, 50, 2, 0),
                    token(lines.get(3), 0, 60, 220, 80, 3, 0),
                    token(lines.get(4), 0, 140, 100, 160, 4, 0));
            List<List<ScanOcrToken>> rows = ScanBackSpatialLayout.rows(lines, tokens);
            assertEquals(5, rows.size());
            assertEquals("69657381", ScanBackSpatialLayout.join(rows.get(4)));
            assertEquals(tokens.size(), rows.stream().mapToInt(List::size).sum());
            ScanBackData data = ScanBackExtractor.extract(
                    new ScanOcrResult(String.join("\n", lines), lines, tokens));
            assertEquals(Arrays.asList("AQUA", "GLYCINE", "LIMONENE"), names(data));
            assertEquals(lines, data.getLines());
            assertEquals(String.join("\n", lines), data.getFullText());
            assertTrue(!data.getIngredientsRawText().contains("69657381"));
        }
    }

    @Test
    public void overlappingVerticalCodeCannotBeSeparatedAsPeripheral() {
        List<String> lines = Arrays.asList("INGREDIENTES: AQUA,", "69657381");
        List<ScanOcrToken> tokens = Arrays.asList(
                token(lines.get(0), 0, 30, 220, 50, 0, 0),
                new ScanOcrToken(lines.get(1), new Rect(100, 20, 120, 80), .9f, -90, 0, 1, 0));
        assertTrue(ScanBackSpatialLayout.rows(lines, tokens).isEmpty());
    }

    @Test
    public void rotatedIngredientOrUnknownTextIsNeverClassifiedAsPeripheralCode() {
        for (String text : Arrays.asList("CI 77491", "PPG-14 BUTYL ETHER", "AQUA", "UNKNOWN")) {
            List<String> lines = Arrays.asList("INGREDIENTES: GLYCINE,", text);
            List<ScanOcrToken> tokens = Arrays.asList(
                    token(lines.get(0), 0, 0, 220, 20, 0, 0),
                    new ScanOcrToken(text, new Rect(260, 0, 280, 130), .9f, -90, 0, 1, 0));
            assertTrue(text, ScanBackSpatialLayout.rows(lines, tokens).isEmpty());
            ScanBackData data = ScanBackExtractor.extract(
                    new ScanOcrResult(String.join("\n", lines), lines, tokens));
            assertTrue(data.getIngredientsRawText().contains(text));
        }
    }

    @Test
    public void mixedAnglesWithinCodeLineAndInvalidAnglesStillFallBack() {
        List<String> lines = Arrays.asList("INGREDIENTES: AQUA.", "6965 7381");
        for (float angle : new float[] {0, 90, Float.NaN, Float.POSITIVE_INFINITY}) {
            List<ScanOcrToken> tokens = Arrays.asList(
                    token(lines.get(0), 0, 0, 220, 20, 0, 0),
                    new ScanOcrToken("6965", new Rect(260, 0, 280, 40), .9f, -90, 0, 1, 0),
                    new ScanOcrToken("7381", new Rect(260, 40, 280, 80), .9f, angle, 0, 1, 1));
            assertTrue(ScanBackSpatialLayout.rows(lines, tokens).isEmpty());
        }
    }

    @Test
    public void peripheralPartitionStillRequiresFullTextCoverage() {
        List<String> lines = Arrays.asList("INGREDIENTES: AQUA.", "69657381");
        List<ScanOcrToken> tokens = Arrays.asList(
                token(lines.get(0), 0, 0, 220, 20, 0, 0),
                new ScanOcrToken("6965", new Rect(260, 0, 280, 80), .9f, -90, 0, 1, 0));
        assertTrue(ScanBackSpatialLayout.rows(lines, tokens).isEmpty());
    }

    @Test
    public void separatedCodePreservesAuditAndExistingSectionRulesWithoutTerminalPeriod() {
        List<String> lines = Arrays.asList("INGREDIENTES: AQUA,", "69657381");
        List<ScanOcrToken> tokens = Arrays.asList(
                token(lines.get(0), 0, 0, 220, 20, 0, 0),
                new ScanOcrToken(lines.get(1), new Rect(260, 0, 280, 80), .9f, -90, 0, 1, 0));
        ScanBackData data = ScanBackExtractor.extract(
                new ScanOcrResult(String.join("\n", lines), lines, tokens));
        // Existing section rules may identify a numeric barcode independently of rotation.
        // Orientation separation must not change that decision or erase the source.
        assertEquals(names(ScanBackExtractor.extract(lines)), names(data));
        assertEquals(ScanBackExtractor.extract(lines).getIngredientsRawText(), data.getIngredientsRawText());
        assertEquals(lines, data.getLines());
        assertEquals(String.join("\n", lines), data.getFullText());
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
