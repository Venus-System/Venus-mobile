package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanProductMatch;
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
        assertTrue(result.isFound());
        assertEquals(expected.getId(), result.getProduto().getId());
    }
}
