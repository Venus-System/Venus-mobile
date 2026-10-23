package com.venussystem.venusmobile.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ScanOcrResultTest {
    @Test
    public void keepsOnlyMetadataForOcrPasses() {
        ScanOcrAttempt attempt = new ScanOcrAttempt(
                "CONTRAST", 120, 8, .72d, true, true, null
        );
        ScanOcrResult result = new ScanOcrResult(
                "texto selecionado",
                Collections.singletonList("texto selecionado"),
                Collections.emptyList(),
                Arrays.asList(attempt)
        );

        assertEquals(1, result.getAttempts().size());
        assertEquals("CONTRAST", result.getAttempts().get(0).getVariant());
        assertEquals(120, result.getAttempts().get(0).getCharacterCount());
        assertTrue(result.getAttempts().get(0).isSelected());
        assertEquals("CONTRAST", result.getSelectedVariant());
        assertTrue(result.hasSelectedSpatialGeometry());
    }

    @Test
    public void doesNotExposeSourceGeometryForASelectedCrop() {
        ScanOcrAttempt attempt = new ScanOcrAttempt(
                "CENTER_CROP", 120, 8, .72d, true, true, null
        );
        ScanOcrResult result = new ScanOcrResult(
                "texto selecionado",
                Collections.singletonList("texto selecionado"),
                Collections.emptyList(),
                Collections.singletonList(attempt)
        );

        assertFalse(result.hasSelectedSpatialGeometry());
    }
}
