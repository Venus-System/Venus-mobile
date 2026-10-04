package com.venussystem.venusmobile.repository;

import static org.junit.Assert.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.venussystem.venusmobile.model.*;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ScanSubmissionPreparationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ScanSubmissionDraft draft() throws Exception {
        Path front = temporary.newFile().toPath();
        Path back = temporary.newFile().toPath();
        Files.write(front, new byte[]{1, 2, 3}); // File-copy fixture, not an image quality test.
        Files.write(back, new byte[]{4, 5, 6});
        ScanSubmissionDraft d = ScanSubmissionDraft.fromFront(front.toString(),
                new ScanOcrResult("MARCA PRODUTO", Arrays.asList("MARCA", "PRODUTO")),
                new ScanFrontData(Arrays.asList("MARCA"), Arrays.asList("PRODUTO"),
                        Arrays.asList("SPRAY"), "150 ML", ""),
                new ScanCosmeticClassification(ScanCosmeticClassification.Status.COSMETIC_CONFIRMED,
                        .9, Collections.emptyList(), Arrays.asList("DESODORANTE")));
        d.bindToUser("user-a");
        d.setBackPhotoPath(back.toString());
        d.setBackOcrText("INGREDIENTES: AQUA, PARFUM");
        d.setBackOcrLines(Arrays.asList(d.getBackOcrText()));
        d.setBackData(new ScanBackData(d.getBackOcrText(), d.getBackOcrLines(), true,
                "AQUA, PARFUM", Arrays.asList(new ScanBackIngredientCandidate(1, "AQUA", "AQUA", "UNRESOLVED"),
                new ScanBackIngredientCandidate(2, "PARFUM", "PARFUM", "UNRESOLVED")),
                "", "", "", "", "", "", "150 ML", "", "", "", Collections.emptyList(), "", "", "OCR:hash"));
        return d;
    }
    private ScanSessionRequest request() throws Exception {
        return ScanSubmissionMapper.prepare(draft(), "user-a",
                new ScanSessionRequest.Device("installation-1", "Android", "1.0", "Test device"),
                new ScanSessionRequest.QualityCheck(.1, .5, true, "COMPLETED")); // Synthetic measured values.
    }

    @Test public void mapsOnlyServerFieldsAndKeepsRawNames() throws Exception {
        ScanSessionRequest r = request();
        ScanSubmissionValidator.validate(r, false);
        JsonObject json = new Gson().toJsonTree(r).getAsJsonObject();
        assertEquals("PARFUM", r.ingredients.get(1).rawName);
        assertEquals(2, json.getAsJsonArray("ingredients").get(0).getAsJsonObject().size());
        String body = json.toString();
        assertFalse(body.contains("PhotoPath"));
        assertFalse(body.contains("UNRESOLVED"));
        assertFalse(body.contains("formulaSignature"));
        assertNull(r.ocr.front.extracted.category);
        assertEquals("150 ML", r.ocr.back.extracted.netContent);
    }
    @Test public void capsDerivedBackFieldsAtApiLimitAndKeepsFullOcr() throws Exception {
        // Verso real de 2659 caracteres em que 2200 caíram no otherText: a API
        // aceita 2000 por campo extraído, e o envio parava no validador.
        String sobra = String.join("", Collections.nCopies(220, "TEXTO SOLTO"));
        String verso = "INGREDIENTES: AQUA, PARFUM " + sobra;
        ScanSubmissionDraft d = draft();
        ScanBackData old = d.getBackData();
        d.setBackOcrText(verso);
        d.setBackData(new ScanBackData(verso, old.getLines(), true,
                old.getIngredientsRawText(), old.getIngredients(),
                "", "", "", "", "", "", "150 ML", sobra, "", "", Collections.emptyList(), "", sobra, "OCR:hash"));
        ScanSessionRequest r = ScanSubmissionMapper.prepare(d, "user-a",
                new ScanSessionRequest.Device("installation-1", "Android", "1.0", "Test device"),
                new ScanSessionRequest.QualityCheck(.1, .5, true, "COMPLETED"));
        ScanSubmissionValidator.validate(r, false);
        assertEquals(2000, r.ocr.back.extracted.otherText.length());
        assertEquals(sobra.substring(0, 2000), r.ocr.back.extracted.otherText);
        assertEquals(2000, r.ocr.back.extracted.usage.length());
        assertEquals(verso, r.ocr.back.fullText);
    }
    @Test public void sendsHighConfidenceIngredientCorrectionToExistingApiField() throws Exception {
        ScanSubmissionDraft d = draft();
        d.setBackData(new ScanBackData(d.getBackData().getFullText(), d.getBackData().getLines(), true,
                d.getBackData().getIngredientsRawText(), Arrays.asList(
                new ScanBackIngredientCandidate(1, "QLYCINE", "GLYCINE", "UNRESOLVED")),
                "", "", "", "", "", "", "150 ML", "", "", "", Collections.emptyList(), "", "", "OCR:hash"));
        ScanSessionRequest r = ScanSubmissionMapper.prepare(d, "user-a",
                new ScanSessionRequest.Device("installation-1", "Android", "1.0", "Test device"),
                new ScanSessionRequest.QualityCheck(.1, .5, true, "COMPLETED"));
        assertEquals("GLYCINE", r.ingredients.get(0).rawName);
        assertTrue(r.ocr.back.fullText.contains("PARFUM"));
    }
    @Test public void promotesBestFrontCandidatesWithoutDiscardingRawOcr() throws Exception {
        ScanSubmissionDraft d = draft();
        ScanSubmissionDraft selected = ScanSubmissionDraft.fromFront(
                d.getFrontPhotoPath(),
                new ScanOcrResult("DOVE GO FRESH CREME", Arrays.asList("DOVE", "GO FRESH", "CREME")),
                new ScanFrontData(Arrays.asList("DOVE", "CREME"), Arrays.asList("CREME", "GO FRESH"),
                        Arrays.asList("SPRAY"), "150 ML", ""),
                d.getClassification());
        selected.bindToUser("user-a");
        selected.setBackPhotoPath(d.getBackPhotoPath());
        selected.setBackOcrText(d.getBackOcrText());
        selected.setBackOcrLines(d.getBackOcrLines());
        selected.setBackData(d.getBackData());
        ScanSessionRequest r = ScanSubmissionMapper.prepare(selected, "user-a",
                new ScanSessionRequest.Device("installation-1", "Android", "1.0", "Test device"),
                new ScanSessionRequest.QualityCheck(.1, .5, true, "COMPLETED"));
        assertEquals("DOVE", r.ocr.front.extracted.brand);
        assertEquals("GO FRESH", r.ocr.front.extracted.productName);
        assertTrue(r.ocr.front.fullText.contains("DOVE GO FRESH"));
    }
    @Test public void rejectsAdministrativeResidueBeforeMongoPreparation() throws Exception {
        ScanSessionRequest r = request();
        r.ocr.back.extracted.ingredientsText = "AQUA, iM333 2 4G Unilever";
        r.ingredients.add(new ScanSessionRequest.Ingredient(3, "iM333 2 4G Unilever"));
        assertThrows(IllegalArgumentException.class,
                () -> ScanSubmissionValidator.validateIngredientQuality(r));
    }

    @Test public void preservesNewOcrCandidatesForAdministrativeReview() throws Exception {
        for (String raw : Arrays.asList("S2ACG 657381", "AT3332 4G Unilevev 6657381",
                "PPG-14 BUTY FOTASILOXANE", "OOPENTASILOXANE", "OATE",
                "DIMETHICONOL. AL PHA ISOMETIHH AT3332 4G Unilevev 6657381")) {
            ScanSessionRequest r = request();
            r.ingredients.add(new ScanSessionRequest.Ingredient(3, raw));
            ScanSubmissionValidator.validateIngredientQuality(r);
        }
    }

    @Test public void mapperRechecksLegacyCorrectionsWithoutMutatingDraft() throws Exception {
        ScanSubmissionDraft d = draft();
        ScanBackData old = d.getBackData();
        d.setBackData(new ScanBackData(old.getFullText(), old.getLines(), true,
                "CHA16AO YOINE", Arrays.asList(new ScanBackIngredientCandidate(
                1, "CHA16AO YOINE", "GLYCINE", "UNRESOLVED")),
                "", "", "", "", "", "", "", "", "", "", Collections.emptyList(), "", "", "OCR:hash"));
        ScanSessionRequest mapped = ScanSubmissionMapper.prepare(d, "user-a",
                new ScanSessionRequest.Device("installation-1", "Android", "1.0", "Test device"),
                new ScanSessionRequest.QualityCheck(.1, .5, true, "COMPLETED"));
        assertEquals("CHA16AO YOINE", mapped.ingredients.get(0).rawName);
        assertEquals("CHA16AO YOINE", d.getBackData().getIngredients().get(0).getRawName());
    }

    @Test public void acceptsSingleIngredientAndRejectsEmptyListWithoutTargetCount() throws Exception {
        ScanSessionRequest r = request();
        r.ingredients = new java.util.ArrayList<>(Arrays.asList(new ScanSessionRequest.Ingredient(1, "AQUA")));
        r.ocr.back.extracted.ingredientsText = "AQUA";
        ScanSubmissionValidator.validateIngredientQuality(r);
        r.ingredients.clear();
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionValidator.validateIngredientQuality(r));
    }
    @Test public void idsAndEndDateRemainStable() throws Exception {
        ScanSubmissionDraft d = draft();
        String id = d.getScanId(), finished = d.getFinishedAt();
        d.setBackData(d.getBackData());
        assertEquals(id, d.getScanId());
        assertEquals(finished, d.getFinishedAt());
        assertFalse(OffsetDateTime.parse(finished).isBefore(OffsetDateTime.parse(d.getStartedAt())));
    }
    @Test public void qualityCannotBeFabricatedOrOmitted() throws Exception {
        ScanSessionRequest r = request();
        r.qualityCheck = null;
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionValidator.validate(r, false));
        r.qualityCheck = new ScanSessionRequest.QualityCheck(Double.NaN, .5, true, "COMPLETED");
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionValidator.validate(r, false));
    }
    @Test public void rejectsExcessDataWithoutTruncatingIt() throws Exception {
        ScanSessionRequest r = request();
        r.ocr.back.fullText = "A".repeat(20001);
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionValidator.validate(r, false));
        assertEquals(20001, r.ocr.back.fullText.length());
    }
    @Test public void rejectsRepeatedIngredientPositions() throws Exception {
        ScanSessionRequest r = request();
        r.ingredients.get(1).position = 1;
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionValidator.validate(r, false));
    }
    @Test public void finalRequestRequiresBothUploadedImages() throws Exception {
        ScanSessionRequest r = request();
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionValidator.validate(r, true));
        r.images.front = photo(r.scanId, "front");
        r.images.back = photo(r.scanId, "back");
        ScanSubmissionValidator.validate(r, true);
        r.images.back.publicId = r.images.front.publicId;
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionValidator.validate(r, true));
    }
    @Test public void mapperRefusesDifferentAccount() throws Exception {
        ScanSubmissionDraft d = draft();
        assertThrows(IllegalArgumentException.class, () -> ScanSubmissionMapper.prepare(d, "user-b", null, null));
        assertThrows(IllegalStateException.class, () -> d.bindToUser("user-b"));
    }
    @Test public void savesPhotosAndRestoresWithoutOriginalFiles() throws Exception {
        ScanSubmissionDraft d = draft();
        ScanDraftStore store = new ScanDraftStore(temporary.newFolder().toPath());
        ScanDraftStore.SavedDraft saved = store.save("user-a", d);
        Files.delete(Path.of(d.getFrontPhotoPath()));
        Files.delete(Path.of(d.getBackPhotoPath()));
        ScanDraftStore.SavedDraft restored = store.read("user-a", d.getScanId());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(Path.of(restored.draft.getFrontPhotoPath())));
        assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(Path.of(restored.draft.getBackPhotoPath())));
        assertEquals(saved.draft.getScanId(), restored.draft.getScanId());
        assertEquals("WAITING_UPLOAD", restored.draft.getStatus());
        assertEquals(1, store.pending("user-a").size());
        assertTrue(store.pending("user-b").isEmpty());
    }
    @Test public void catalogEvidenceSurvivesReloadWithoutChangingOcrOrUploadState() throws Exception {
        ScanSubmissionDraft d = draft();
        ScanDraftStore store = new ScanDraftStore(temporary.newFolder().toPath());
        ScanDraftStore.SavedDraft initial = store.save("user-a", d);
        ScanIngredientCatalog.Report report = new ScanIngredientCatalog.Report();
        report.checkedAt = OffsetDateTime.now().toString();
        ScanIngredientCatalog.Match match = new ScanIngredientCatalog.Match();
        match.rawName = "AQUA"; match.status = "KNOWN"; match.ingredientId = 10L;
        report.matches.add(match); report.known = 1;
        store.recordIngredientCatalog("user-a", d.getScanId(), report);
        ScanDraftStore.SavedDraft restored = store.read("user-a", d.getScanId());
        assertEquals(Long.valueOf(10), restored.ingredientCatalog.matches.get(0).ingredientId);
        assertEquals(initial.draft.getBackOcrText(), restored.draft.getBackOcrText());
        assertEquals(initial.state, restored.state);
        assertThrows(java.io.IOException.class, () -> store.recordIngredientCatalog("user-b", d.getScanId(), report));
    }
    @Test public void mongoSubmissionSurvivesReloadAndLeavesNoPendingDraft() throws Exception {
        ScanSubmissionDraft d = draft();
        ScanDraftStore store = new ScanDraftStore(temporary.newFolder().toPath());
        ScanDraftStore.SavedDraft initial = store.save("user-a", d);
        Path storedFront = Path.of(initial.draft.getFrontPhotoPath());
        Path storedBack = Path.of(initial.draft.getBackPhotoPath());
        ScanSessionResponse response = new ScanSessionResponse();
        response.id = "mongo-id-1";
        response.scanId = d.getScanId();
        response.status = "PENDING_REVIEW";

        ScanDraftStore.SavedDraft submitted = store.recordSubmission("user-a", d.getScanId(), response);
        ScanDraftStore.SavedDraft restored = store.read("user-a", d.getScanId());

        assertEquals("SUBMITTED", submitted.state);
        assertEquals("SUBMITTED", restored.state);
        assertEquals("mongo-id-1", restored.submission.id);
        assertEquals("PENDING_REVIEW", restored.submission.status);
        assertNull(restored.frontUpload);
        assertNull(restored.backUpload);
        assertEquals("", restored.draft.getBackOcrText());
        assertFalse(Files.exists(storedFront));
        assertFalse(Files.exists(storedBack));
        assertTrue(store.pending("user-a").isEmpty());
        assertThrows(java.io.IOException.class,
                () -> store.recordSubmission("user-b", d.getScanId(), response));
    }

    @Test public void submittedReceiptResumesAfterAppRestartWithoutSensitiveDraft() throws Exception {
        ScanSubmissionDraft d = draft();
        Path root = temporary.newFolder().toPath();
        ScanDraftStore firstProcess = new ScanDraftStore(root);
        firstProcess.save("user-a", d);
        ScanSessionResponse response = new ScanSessionResponse();
        response.id = "mongo-id-restart"; response.scanId = d.getScanId(); response.status = "PENDING_REVIEW";
        firstProcess.recordSubmission("user-a", d.getScanId(), response);

        ScanDraftStore afterRestart = new ScanDraftStore(root);
        ScanDraftStore.SavedDraft receipt = afterRestart.read("user-a", d.getScanId());
        assertEquals("SUBMITTED", receipt.state);
        assertEquals("mongo-id-restart", receipt.submission.id);
        assertEquals("", receipt.draft.getFrontOcrText());
        assertEquals("", receipt.draft.getBackOcrText());
    }

    @Test public void pendingDraftResumesAfterAppRestart() throws Exception {
        ScanSubmissionDraft d = draft();
        Path root = temporary.newFolder().toPath();
        ScanDraftStore firstProcess = new ScanDraftStore(root);
        ScanDraftStore.SavedDraft saved = firstProcess.save("user-a", d);

        ScanDraftStore afterRestart = new ScanDraftStore(root);
        ScanDraftStore.SavedDraft resumed = afterRestart.read("user-a", d.getScanId());
        assertEquals("WAITING_UPLOAD", resumed.state);
        assertEquals(d.getBackOcrText(), resumed.draft.getBackOcrText());
        assertTrue(Files.isRegularFile(Path.of(saved.draft.getFrontPhotoPath())));
        assertTrue(Files.isRegularFile(Path.of(resumed.draft.getBackPhotoPath())));
    }

    @Test public void retryDoesNotOverwriteSavedPhotos() throws Exception {
        ScanSubmissionDraft d = draft();
        ScanDraftStore store = new ScanDraftStore(temporary.newFolder().toPath());
        store.save("user-a", d);
        Files.write(Path.of(d.getFrontPhotoPath()), new byte[]{9});
        ScanDraftStore.SavedDraft saved = store.save("user-a", d);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(Path.of(saved.draft.getFrontPhotoPath())));
        assertEquals(1, store.pending("user-a").size());
    }
    @Test public void missingPhotoNeverPublishesReadyDraft() throws Exception {
        ScanSubmissionDraft d = draft();
        Files.delete(Path.of(d.getBackPhotoPath()));
        ScanDraftStore store = new ScanDraftStore(temporary.newFolder().toPath());
        assertThrows(java.io.IOException.class, () -> store.save("user-a", d));
        assertTrue(store.pending("user-a").isEmpty());
    }
    @Test public void anotherAccountCannotSaveOrReadDraft() throws Exception {
        ScanSubmissionDraft d = draft();
        ScanDraftStore store = new ScanDraftStore(temporary.newFolder().toPath());
        store.save("user-a", d);
        assertThrows(java.io.IOException.class, () -> store.save("user-b", d));
        assertThrows(java.io.IOException.class, () -> store.read("user-b", d.getScanId()));
        assertThrows(java.io.IOException.class, () -> store.read("user-a", "../../other"));
    }
    private static ScanSessionRequest.Image photo(String scanId, String side) {
        ScanSessionRequest.Image image = new ScanSessionRequest.Image();
        image.publicId = "scans/" + scanId + "/" + side;
        image.width = 100; image.height = 100; image.bytes = 300L; image.format = "jpg";
        return image;
    }
}
