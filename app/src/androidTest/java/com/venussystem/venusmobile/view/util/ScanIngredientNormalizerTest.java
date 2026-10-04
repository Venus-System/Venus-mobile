package com.venussystem.venusmobile.view.util;

import com.venussystem.venusmobile.domain.scan.ScanIngredientPolicy;
import com.venussystem.venusmobile.domain.scan.ScanIngredientVocabulary;
import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.repository.ScanSubmissionValidator;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

/** Captured text regressions. Coordinates are tested separately with synthetic boxes. */
public class ScanIngredientNormalizerTest {
    @Test public void preservesKnownNamesAndDoesNotCascadeReplacements() {
        for (String name : ScanIngredientVocabulary.NAMES) {
            assertEquals(name, ScanIngredientNormalizer.correct(name).normalized);
            assertFalse(ScanIngredientNormalizer.correct(name).corrected);
        }
        assertEquals("OOPENTASILOXANE", ScanIngredientNormalizer.correct("OOPENTASILOXANE").normalized);
        assertEquals("CYCLOPENTASILOXANE",
                ScanIngredientNormalizer.correct("CYCLOPENTASILOXANE").normalized);
    }

    @Test public void correctsOnlySmallUniqueErrorsAndKeepsOriginalCandidate() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: QLYCINE, 0CTYLDODECANOL, HEXYL CINNAMAL"));
        assertEquals("QLYCINE", data.getIngredients().get(0).getRawName());
        assertEquals("GLYCINE", data.getIngredients().get(0).getNormalizedName());
        assertEquals("OCTYLDODECANOL", data.getIngredients().get(1).getNormalizedName());
        assertTrue(data.getFullText().contains("QLYCINE"));
    }

    @Test public void neverReconstructsFormulaFromAmbiguousFragments() {
        for (String fragment : Arrays.asList("CHA16AO YOINE",
                "DISTEARDIMONIUM HECTORITE CH4M HGPLENE CARBONATE",
                "PANR Z4C NUUS SEED OIL", "ALPHA-ISOMETHIL HEXYL CINNAMAL",
                "PPG-14 BUTN EN RIUS ANINUUS SEED OIL", "0CTYLDO0ECAKL LENE CARBONATE")) {
            assertEquals(fragment, ScanIngredientNormalizer.correct(fragment).normalized);
            ScanBackData data = ScanBackExtractor.extract(Arrays.asList("INGREDIENTES: AQUA, " + fragment));
            assertTrue(data.getIngredients().stream().anyMatch(i -> i.getRawName().equals(fragment)));
                assertPreservedForReview(data);
        }
    }

    @Test public void retainsNumericInciAndUnknownFragmentsForReview() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: PPG-14 BUTYL ETHER, C12-15 ALKYL BENZOATE, CI 77491, PEG-40 TEST"));
        assertTrue(names(data).contains("PPG-14 BUTYL ETHER"));
        assertTrue(names(data).contains("C12-15 ALKYL BENZOATE"));
        assertTrue(names(data).contains("CI 77491"));
        assertTrue(names(data).contains("PEG-40 TEST"));
        assertPreservedForReview(data);
    }

    @Test public void stopsOnlyAtTerminalPackagingSuffix() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: AQUA, LIMONENE.", "S2ACG", "657381"));
        assertEquals(Arrays.asList("AQUA", "LIMONENE"), names(data));
        assertFalse(data.getIngredientsRawText().contains("S2ACG"));
        assertTrue(data.getFullText().contains("S2ACG"));
        ScanBackData continuation = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: LIMONENE.", "S2ACG", "GLYCINE, AQUA"));
        assertTrue(continuation.getIngredientsRawText().contains("S2ACG"));
        assertTrue(continuation.getIngredientsRawText().contains("GLYCINE"));
        assertPreservedForReview(continuation);
    }

    @Test public void numericFragmentInsideCompositionCannotDisappearBeforeValidation() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "INGREDIENTES: AQUA, 657381, CI, 77491"));
        assertTrue(names(data).contains("657381"));
        assertTrue(names(data).contains("CI 77491"));
        assertPreservedForReview(data);
    }

    @Test public void firstOctoberEveningCaptureIsPreservedForReview() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "HOTES; BUTANE, ISOBUTANE, PROPANE, PPG-14 BUTY",
                "FoTASILOXANE, ALUMINUM SESQUICHLOROHYDRATE PAA",
                "iNSANNUUS SEED OIL, DISTEARDIMONIUM HECTORITE CI6A",
                "CARHONATE, DIMETHICONOL, ALPHA-ISOMETHYL N",
                "AQUA, CALCIUM CHLORIDE, OCTYLDO0ECAML M",
                "OL LCiNNAMAL, LIMONENE.", "S2ACG", "657381"));
        assertTrue(data.getIngredientsRawText().contains("PPG-14 BUTY FoTASILOXANE"));
        assertFalse(data.getIngredientsRawText().contains("S2ACG"));
        assertTrue(data.getFullText().contains("657381"));
        assertFalse(names(data).contains("PPG-14 BUTYL ETHER"));
        assertPreservedForReview(data);
    }

    @Test public void secondOctoberEveningCaptureDoesNotInventMissingIngredients() {
        ScanBackData data = ScanBackExtractor.extract(Arrays.asList(
                "NREDIENTES: BUTANE, ISOBUTANE, PROPANE, PPG-14 8UN N",
                "AHTIHUS ANNUUS SEED OIL. DISTEARDIMONIUM HECTORITE CAMA",
                "OOPENTASILOXANE, ALUMINUM SESOLUICHLOROHYDRAIE",
                "HEXYL CINNAMAL, LIMONENE.",
                "OATE, GLYCINE, AQUA, CALCIUM CHLORIDE, 0CTYLDOOECAMA",
                "PLENE CARBONATE, DIMETHICONOL. AL PHA ISOMETIHH",
                "AT3332 4G", "Unilevev", "6657381"));
        assertTrue(data.getIngredientsRawText().contains("GLYCINE"));
        assertTrue(data.getIngredientsRawText().contains("OOPENTASILOXANE"));
        assertFalse(names(data).toString().contains("OCYCLOPENTASILOXANE"));
        assertFalse(names(data).contains("C12-15 ALKYL BENZOATE"));
        assertTrue(data.getFullText().contains("Unilevev"));
        assertNoAdministrativeResiduePromoted(data);
    }

    private static List<String> names(ScanBackData data) {
        return data.getIngredients().stream().map(i -> i.getRawName()).collect(Collectors.toList());
    }

    private static void assertPreservedForReview(ScanBackData data) {
        ScanSessionRequest r = new ScanSessionRequest();
        r.ocr = new ScanSessionRequest.Ocr();
        r.ocr.back = new ScanSessionRequest.Back();
        r.ocr.back.extracted = new ScanSessionRequest.BackExtracted();
        r.ocr.back.extracted.ingredientsText = data.getIngredientsRawText();
        r.ingredients = new ArrayList<>();
        data.getIngredients().forEach(i -> r.ingredients.add(
                new ScanSessionRequest.Ingredient(i.getPosition(), i.getNormalizedName())));
        // Unknown candidates are intentionally allowed through to Mongo/PENDING_REVIEW.
        // Administrative residue is still rejected by the validator's dedicated tests.
        ScanSubmissionValidator.validateIngredientQuality(r);
        assertFalse(r.ingredients.isEmpty());
    }

    private static void assertNoAdministrativeResiduePromoted(ScanBackData data) {
        data.getIngredients().forEach(i -> assertFalse(
                com.venussystem.venusmobile.domain.scan.ScanIngredientPolicy.hasAdministrativeText(
                        i.getNormalizedName())));
    }
}
