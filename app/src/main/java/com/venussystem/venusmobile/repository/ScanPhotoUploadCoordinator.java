package com.venussystem.venusmobile.repository;

import com.venussystem.venusmobile.repository.api.ScanUploadException;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadedPhoto;
import java.io.File;
import java.io.IOException;

/** Worker only. Uploads photos; never submits product data. Production calls are serialized. */
public final class ScanPhotoUploadCoordinator {
    public interface Signer { ScanUploadSignaturesResponse get() throws IOException; }
    public interface Guard { void check() throws IOException; }
    public interface Upload { ScanUploadedPhoto send(ScanUploadSignaturesResponse s, String id, String side, File file) throws IOException; }
    public interface Progress { void update(String event, String side); }
    private final ScanDraftStore store;
    private final Signer signer;
    private final Guard guard;
    private final Upload uploader;
    public ScanPhotoUploadCoordinator(ScanDraftStore store, Signer signer, Guard guard, Upload uploader) {
        this.store = store; this.signer = signer; this.guard = guard; this.uploader = uploader;
    }
    public ScanDraftStore.SavedDraft run(String uid, String id, ScanUploadSignaturesResponse initial, Progress progress)
            throws IOException {
        guard.check();
        ScanDraftStore.SavedDraft saved = store.read(uid, id);
        ScanUploadSignaturesResponse signatures = initial;
        for (String side : new String[]{"front", "back"}) {
            guard.check();
            ScanUploadedPhoto receipt = "front".equals(side) ? saved.frontUpload : saved.backUpload;
            if (receipt != null) {
                receipt.validate(id, side);
                if (signatures != null) checkDestination(receipt, signatures);
                progress.update("SKIPPED", side);
                continue;
            }
            if (signatures == null) signatures = signer.get();
            if (saved.frontUpload != null) checkDestination(saved.frontUpload, signatures);
            if (saved.backUpload != null) checkDestination(saved.backUpload, signatures);
            String path = "front".equals(side) ? saved.draft.getFrontPhotoPath() : saved.draft.getBackPhotoPath();
            progress.update("START", side);
            guard.check();
            try { receipt = uploader.send(signatures, id, side, new File(path)); }
            catch (ScanUploadException e) {
                if (e.kind != ScanUploadException.Kind.EXPIRED_SIGNATURE) throw e;
                guard.check();
                signatures = signer.get(); // One refresh only, never an unbounded retry.
                if (saved.frontUpload != null) checkDestination(saved.frontUpload, signatures);
                if (saved.backUpload != null) checkDestination(saved.backUpload, signatures);
                guard.check();
                receipt = uploader.send(signatures, id, side, new File(path));
            }
            // Even if logout happened during upload, record the receipt under the original owner.
            // This cannot upload anything else; the following guard prevents continuing as another user.
            saved = store.recordUpload(uid, id, side, receipt);
            guard.check();
            progress.update("UPLOADED", side);
        }
        return saved;
    }
    private static void checkDestination(ScanUploadedPhoto receipt, ScanUploadSignaturesResponse s) throws IOException {
        if (s == null || !receipt.cloudName.equals(s.cloudName) || !receipt.deliveryType.equals(s.type))
            throw new ScanUploadException(ScanUploadException.Kind.CONFIGURATION, 0, "O destino das fotos mudou. Verifique a configuração.");
    }
}
