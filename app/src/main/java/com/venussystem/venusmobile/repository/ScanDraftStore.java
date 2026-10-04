package com.venussystem.venusmobile.repository;

import com.google.gson.Gson;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadedPhoto;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionResponse;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** App-private durable snapshots. Root must be under Context.getNoBackupFilesDir(). */
public final class ScanDraftStore {
    private static final Gson GSON = new Gson();
    private final Path root;
    public ScanDraftStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public static final class SavedDraft {
        public int schemaVersion = 1;
        public String firebaseUid;
        public ScanSubmissionDraft draft;
        public String state = "WAITING_UPLOAD";
        public ScanUploadedPhoto frontUpload, backUpload;
        public ScanIngredientCatalog.Report ingredientCatalog;
        public ScanSessionResponse submission;
    }

    /** Immutable initial snapshot. Repeated saves never overwrite images under the same scanId. */
    public SavedDraft save(String uid, ScanSubmissionDraft draft) throws IOException {
        synchronized (ScanDraftStore.class) {
            if (draft == null || draft.getBackData() == null)
                throw new IOException("O verso ainda não foi processado.");
            if (uid == null || !uid.equals(draft.getOwnerUid()))
                throw new IOException("O rascunho pertence a outra conta. Entre com a conta original.");
            Path directory = directory(uid, draft.getScanId());
            Path target = directory.resolve("draft.json");
            if (Files.exists(target)) return read(uid, draft.getScanId());
            Files.createDirectories(directory);
            SavedDraft snapshot = new SavedDraft();
            snapshot.firebaseUid = uid;
            snapshot.draft = GSON.fromJson(GSON.toJson(draft), ScanSubmissionDraft.class);
            Path front = directory.resolve("front.photo");
            Path back = directory.resolve("back.photo");
            copyPhoto(draft.getFrontPhotoPath(), front);
            copyPhoto(draft.getBackPhotoPath(), back);
            snapshot.draft.setFrontPhotoPath(front.toString());
            snapshot.draft.setBackPhotoPath(back.toString());
            snapshot.draft.setStatus(ScanSubmissionDraft.STATUS_WAITING_UPLOAD);
            Path temporary = Files.createTempFile(directory, "draft-", ".tmp");
            try {
                Files.write(temporary, GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
                force(temporary);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary); // Only this write's temporary file.
            }
            return snapshot;
        }
    }

    public SavedDraft read(String uid, String scanId) throws IOException {
        synchronized (ScanDraftStore.class) {
            Path directory = directory(uid, scanId);
            try {
                SavedDraft saved = GSON.fromJson(new String(Files.readAllBytes(directory.resolve("draft.json")),
                        StandardCharsets.UTF_8), SavedDraft.class);
                if (saved == null || saved.schemaVersion != 1 || !uid.equals(saved.firebaseUid)
                        || saved.draft == null || !scanId.equals(saved.draft.getScanId()))
                    throw new IOException("Rascunho incompatível ou de outra conta.");
                // Do not trust serialized absolute paths; derive from the validated identity.
                if (saved.submission == null && !"SUBMITTED".equals(saved.state)) {
                    saved.draft.setFrontPhotoPath(directory.resolve("front.photo").toString());
                    saved.draft.setBackPhotoPath(directory.resolve("back.photo").toString());
                } else {
                    saved.draft.setFrontPhotoPath("");
                    saved.draft.setBackPhotoPath("");
                }
                return saved;
            } catch (RuntimeException e) {
                throw new IOException("Não foi possível ler o rascunho salvo.", e);
            }
        }
    }

    public List<SavedDraft> pending(String uid) throws IOException {
        List<SavedDraft> result = new ArrayList<>();
        Path account = account(uid);
        if (!Files.exists(account)) return result;
        try (Stream<Path> entries = Files.list(account)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (Files.isRegularFile(entry.resolve("draft.json"))) {
                    SavedDraft saved = read(uid, entry.getFileName().toString());
                    if ("WAITING_UPLOAD".equals(saved.state) || "PHOTOS_UPLOADED".equals(saved.state)) result.add(saved);
                }
            }
        }
        return result;
    }

    /**
     * Submissões já confirmadas pelo servidor. Elas funcionam como cache
     * imediato do histórico enquanto a listagem remota ainda não refletiu o
     * scan recém-enviado.
     */
    public List<SavedDraft> submitted(String uid) throws IOException {
        List<SavedDraft> result = new ArrayList<>();
        Path account = account(uid);
        if (!Files.exists(account)) return result;
        try (Stream<Path> entries = Files.list(account)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (!Files.isRegularFile(entry.resolve("draft.json"))) continue;
                try {
                    SavedDraft saved = read(uid, entry.getFileName().toString());
                    if (saved.submission != null || "SUBMITTED".equals(saved.state)) {
                        result.add(saved);
                    }
                } catch (IOException ignored) {
                    // Um rascunho corrompido não deve esconder os demais itens.
                }
            }
        }
        return result;
    }

    /** Atomically acknowledges just one side, preserving the immutable OCR/photo snapshot. */
    public SavedDraft recordUpload(String uid, String scanId, String side, ScanUploadedPhoto receipt) throws IOException {
        synchronized (ScanDraftStore.class) {
            if (receipt == null) throw new IOException("Confirmação ausente.");
            receipt.validate(scanId, side);
            SavedDraft saved = read(uid, scanId);
            ScanUploadedPhoto other = "front".equals(side) ? saved.backUpload : saved.frontUpload;
            if (other != null) other.validate(scanId, "front".equals(side) ? "back" : "front");
            if (other != null && (!other.cloudName.equals(receipt.cloudName) || !other.deliveryType.equals(receipt.deliveryType)))
                throw new IOException("As fotos precisam usar o mesmo destino.");
            ScanUploadedPhoto existing = "front".equals(side) ? saved.frontUpload : saved.backUpload;
            if (existing != null) { existing.validate(scanId, side); return saved; }
            if ("front".equals(side)) saved.frontUpload = receipt;
            else saved.backUpload = receipt;
            saved.state = saved.frontUpload != null && saved.backUpload != null ? "PHOTOS_UPLOADED" : "WAITING_UPLOAD";
            Path directory = directory(uid, scanId);
            Path temporary = Files.createTempFile(directory, "receipt-", ".tmp");
            try {
                Files.write(temporary, GSON.toJson(saved).getBytes(StandardCharsets.UTF_8));
                force(temporary);
                Files.move(temporary, directory.resolve("draft.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            return saved;
        }
    }

    /** Stores lookup evidence separately, without rewriting OCR or changing upload state. */
    public SavedDraft recordIngredientCatalog(String uid, String scanId, ScanIngredientCatalog.Report report) throws IOException {
        synchronized (ScanDraftStore.class) {
            if (report == null) throw new IOException("Consulta ausente.");
            SavedDraft saved = read(uid, scanId);
            saved.ingredientCatalog = report;
            Path directory = directory(uid, scanId);
            Path temporary = Files.createTempFile(directory, "catalog-", ".tmp");
            try {
                Files.write(temporary, GSON.toJson(saved).getBytes(StandardCharsets.UTF_8));
                force(temporary);
                Files.move(temporary, directory.resolve("draft.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            return saved;
        }
    }

    /** Acknowledges the idempotent MongoDB submission after the API confirms it. */
    public SavedDraft recordSubmission(String uid, String scanId, ScanSessionResponse response) throws IOException {
        synchronized (ScanDraftStore.class) {
            if (response == null || response.id == null || response.id.trim().isEmpty()
                    || response.status == null || response.status.trim().isEmpty()
                    || !scanId.equals(response.scanId)) throw new IOException("Resposta de cadastro inválida.");
            SavedDraft saved = read(uid, scanId);
            saved.submission = response;
            saved.state = "SUBMITTED";
            saved.ingredientCatalog = null;
            saved.frontUpload = null;
            saved.backUpload = null;
            saved.draft.redactSensitiveDataAfterSubmission();
            Path directory = directory(uid, scanId);
            Path temporary = Files.createTempFile(directory, "submission-", ".tmp");
            try {
                Files.write(temporary, GSON.toJson(saved).getBytes(StandardCharsets.UTF_8));
                force(temporary);
                Files.move(temporary, directory.resolve("draft.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            // A submitted scan no longer needs local photos. Delete only the
            // validated scan directory files; the receipt remains for retry
            // idempotency and resume-after-process-death.
            Files.deleteIfExists(directory.resolve("front.photo"));
            Files.deleteIfExists(directory.resolve("back.photo"));
            return saved;
        }
    }

    private Path directory(String uid, String scanId) throws IOException {
        try {
            if (!UUID.fromString(scanId).toString().equals(scanId)) throw new IllegalArgumentException();
            return account(uid).resolve(scanId);
        } catch (RuntimeException e) { throw new IOException("Identificador de scan inválido.", e); }
    }
    private Path account(String uid) throws IOException {
        if (uid == null || uid.trim().isEmpty() || uid.length() > 128)
            throw new IOException("É necessário entrar para salvar a submissão.");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return root.resolve(name.toString());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void copyPhoto(String source, Path target) throws IOException {
        if (source == null || source.isEmpty()) throw new IOException("Foto ausente no rascunho.");
        Path path = Paths.get(source);
        if (!Files.isRegularFile(path) || Files.size(path) == 0) throw new IOException("Arquivo de foto indisponível.");
        Path temporary = Files.createTempFile(target.getParent(), "photo-", ".tmp");
        try {
            Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
            force(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
    }
}
