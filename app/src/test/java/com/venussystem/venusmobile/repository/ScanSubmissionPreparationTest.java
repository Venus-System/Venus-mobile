package com.venussystem.venusmobile.repository;

import static org.junit.Assert.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.venussystem.venusmobile.model.*;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
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
        d.setBackOcrText("INGREDIENTES: AQUA, AUIOATE");
        d.setBackOcrLines(Arrays.asList(d.getBackOcrText()));
        d.setBackData(new ScanBackData(d.getBackOcrText(), d.getBackOcrLines(), true,
                "AQUA, AUIOATE", Arrays.asList(new ScanBackIngredientCandidate(1, "AQUA", "AQUA", "UNRESOLVED"),
                new ScanBackIngredientCandidate(2, "AUIOATE", "AUIOATE", "UNRESOLVED")),
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
        assertEquals("AUIOATE", r.ingredients.get(1).rawName);
        assertEquals(2, json.getAsJsonArray("ingredients").get(0).getAsJsonObject().size());
        String body = json.toString();
        assertFalse(body.contains("PhotoPath"));
        assertFalse(body.contains("UNRESOLVED"));
        assertFalse(body.contains("formulaSignature"));
        assertNull(r.ocr.front.extracted.category);
        assertEquals("150 ML", r.ocr.back.extracted.netContent);
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
