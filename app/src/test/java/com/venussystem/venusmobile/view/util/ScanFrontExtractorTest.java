package com.venussystem.venusmobile.view.util;

import com.venussystem.venusmobile.model.ScanFrontData;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScanFrontExtractorTest {
    @Test public void knownBrandWinsOverLeadingOcrArtifact() {
        ScanFrontData front = ScanFrontExtractor.extract(Arrays.asList(
                "aixa", "F 2a sér", "20 Goog", "Veriu,", "NOVA/NUEVA FORMULA",
                "drogaraia.com.br/_next/image?url=https%3A%2F%2Fproduct-data.rai",
                "Rexona", "CONTROLE", "INTELIGENTE", "DE ODORE SUOR", "INVISIBLE",
                "72h", "ANTITRANSPIRANTE AEROSOL MEN", "150 ml"));

        assertEquals("REXONA", front.getBrandCandidates().get(0));
        assertTrue(front.getBrandCandidates().contains("AIXA"));
    }

    @Test public void preservesBrandAndUsefulProductEvidenceAcrossLabels() {
        ScanFrontData dove = ScanFrontExtractor.extract(Arrays.asList(
                "DOVE", "GO FRESH", "CREME HIDRATANTE", "AROMA FLOR DE LOTUS", "150 ML"));
        ScanFrontData rexona = ScanFrontExtractor.extract(Arrays.asList(
                "REXONA", "CLINICAL", "CLEAN MEN", "ANTITRANSPIRANTE", "58 G"));
        ScanFrontData cerave = ScanFrontExtractor.extract(Arrays.asList(
                "CeraVe", "ESPUMA CREMOSA", "LIMPEZA HIDRATANTE", "236 ML"));

        assertEquals("DOVE", dove.getBrandCandidates().get(0));
        assertTrue(dove.getProductCandidates().stream().anyMatch(s -> s.contains("FRESH")));
        assertEquals("REXONA", rexona.getBrandCandidates().get(0));
        assertTrue(rexona.getProductCandidates().contains("CLINICAL"));
        assertTrue(cerave.getBrandCandidates().contains("CERAVE"));
        assertTrue(cerave.getProductCandidates().stream().anyMatch(s ->
                s.contains("ESPUMA") || s.contains("LIMPEZA")));
    }

    @Test public void rexonaClinicalCleanMenGoldenFrontPreservesIdentityEvidence() {
        ScanFrontData front = ScanFrontExtractor.extract(Arrays.asList(
                "MEN", "Rexona", "clinical", "3x", "MÁS PROTECCIÓN* /",
                "MAIS PROTEÇÃO*", "CLEAN", "96H", "ANTITRANSPIRANTE",
                "EN CREMA/EM CREME", "CONT. NETO /", "PESO LÍQ. 58 G"));

        assertEquals("REXONA", front.getBrandCandidates().get(0));
        assertTrue(front.getProductCandidates().contains("CLINICAL"));
        assertTrue(front.getProductCandidates().contains("CLEAN"));
        assertTrue(front.getProductCandidates().contains("MEN"));
        assertEquals("58 G", front.getCapacity());
    }
}
