package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanProductMatch;
import com.venussystem.venusmobile.view.util.ScanFrontExtractor;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** Small catalog fixture protecting the public matching decision after extraction. */
public class ScanCatalogCharacterizationTest {
    @Test public void keepsTheBestProductDecisionAfterSplit() {
        ScanFrontData front = new ScanFrontData(
                Collections.singletonList("REXONA"),
                Arrays.asList("ANTITRANSPIRANTE", "ORIGINAL", "150 ML"),
                Collections.emptyList(), "150 ML", "");
        Produto expected = new Produto(7L, "Rexona Antitranspirante Original 150 ML", "Rexona",
                90, null, 1L, "Higiene");
        Produto other = new Produto(8L, "Rexona Antitranspirante Invisible 150 ML", "Rexona",
                90, null, 1L, "Higiene");
        ScanProductMatch result = ScanCatalogMatcher.procurarMelhorProduto(front, Arrays.asList(other, expected));
        assertTrue("found=" + result.isFound()
                        + " status=" + result.getResolutionStatus()
                        + " score=" + result.getScore()
                        + " gap=" + result.getScoreGap()
                        + " product=" + result.getMatchedProduct(), result.isFound());
        assertEquals(expected.getId(), result.getProduto().getId());
        assertEquals(ScanProductMatch.ResolutionStatus.RESOLVED, result.getResolutionStatus());
        assertFalse(result.needsReview());
    }

    @Test public void doesNotOpenAnAmbiguousProductFromEqualEvidence() {
        ScanFrontData front = new ScanFrontData(
                Collections.singletonList("REXONA"),
                Collections.singletonList("CLINICAL"),
                Collections.emptyList(), "", "");
        Produto first = new Produto(11L, "Rexona Clinical", "Rexona", 90, null, 1L, "Higiene");
        Produto second = new Produto(12L, "Rexona Clinical", "Rexona", 90, null, 1L, "Higiene");
        ScanProductMatch result = ScanCatalogMatcher.procurarMelhorProduto(front, Arrays.asList(first, second));
        assertFalse(result.isFound());
        assertEquals(ScanProductMatch.ResolutionStatus.AMBIGUOUS, result.getResolutionStatus());
        assertTrue(result.needsReview());
        assertEquals(0.0, result.getScoreGap(), 0.0001);
    }

    @Test public void prefersTheMoreSpecificClinicalVariantWhenAllIdentityTokensAreRead() {
        ScanFrontData front = new ScanFrontData(
                Collections.singletonList("REXONA"),
                Arrays.asList("CLINICAL", "CLEAN", "MEN", "ANTITRANSPIRANTE", "58 G"),
                Collections.emptyList(), "58 G", "");
        Produto shorterVariant = new Produto(
                108L, "Rexona Clinical Clean", "Rexona", 90, null, 1L, "Higiene");
        Produto expected = new Produto(
                109L, "Rexona Clinical Clean Men", "Rexona", 90, null, 1L, "Higiene");

        ScanProductMatch result = ScanCatalogMatcher.procurarMelhorProduto(
                front, Arrays.asList(shorterVariant, expected));

        assertTrue("status=" + result.getResolutionStatus()
                        + " score=" + result.getScore()
                        + " gap=" + result.getScoreGap(), result.isFound());
        assertEquals(expected.getId(), result.getProduto().getId());
        assertEquals(ScanProductMatch.ResolutionStatus.RESOLVED, result.getResolutionStatus());
        assertFalse(result.needsReview());
    }

    @Test public void usesInvisibleAsVariantEvidenceForRexonaAerosol() {
        ScanFrontData front = new ScanFrontData(
                Collections.singletonList("REXONA"),
                Arrays.asList("INVISIBLE", "MEN", "AEROSOL", "150 ML"),
                Collections.singletonList("AEROSOL"), "150 ML", "");
        Produto original = new Produto(
                201L, "Rexona Antitranspirante Aerosol Men Original 150 ML",
                "Rexona", 90, null, 1L, "Higiene");
        Produto invisible = new Produto(
                202L, "Rexona Antitranspirante Aerosol Men Invisible 150 ML",
                "Rexona", 90, null, 1L, "Higiene");

        ScanProductMatch result = ScanCatalogMatcher.procurarMelhorProduto(
                front, Arrays.asList(original, invisible));

        assertTrue("status=" + result.getResolutionStatus()
                        + " score=" + result.getScore()
                        + " gap=" + result.getScoreGap(), result.isFound());
        assertEquals(invisible.getId(), result.getProduto().getId());
        assertFalse(result.needsReview());
    }

    @Test public void rexonaClinicalCleanMenGoldenFrontResolvesCatalogProduct() {
        ScanFrontData front = ScanFrontExtractor.extract(Arrays.asList(
                "MEN", "Rexona", "clinical", "3x", "MÁS PROTECCIÓN* /",
                "MAIS PROTEÇÃO*", "CLEAN", "96H", "ANTITRANSPIRANTE",
                "EN CREMA/EM CREME", "CONT. NETO /", "PESO LÍQ. 58 G"));
        Produto expected = new Produto(
                109L, "Rexona Clinical Clean Men", "Rexona", 90, null, 1L, "Higiene");
        Produto shorterVariant = new Produto(
                108L, "Rexona Clinical Clean", "Rexona", 90, null, 1L, "Higiene");

        ScanProductMatch result = ScanCatalogMatcher.procurarMelhorProduto(
                front, Arrays.asList(shorterVariant, expected));

        assertTrue("status=" + result.getResolutionStatus()
                        + " candidates=" + front.getProductCandidates(), result.isFound());
        assertEquals(expected.getId(), result.getProduto().getId());
    }
}
