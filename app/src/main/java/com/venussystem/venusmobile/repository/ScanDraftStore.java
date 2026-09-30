package com.venussystem.venusmobile.repository;

import com.google.gson.Gson;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
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
                saved.draft.setFrontPhotoPath(directory.resolve("front.photo").toString());
                saved.draft.setBackPhotoPath(directory.resolve("back.photo").toString());
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
                    if ("WAITING_UPLOAD".equals(saved.state)) result.add(saved);
                }
            }
        }
        return result;
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
