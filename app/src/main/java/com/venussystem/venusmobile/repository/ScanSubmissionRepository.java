package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.repository.api.AuthenticatedScanClient;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.FirebaseScanAuthSession;
import com.venussystem.venusmobile.repository.api.ScanApiException;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.view.util.ScanPhotoQualityAnalyzer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanSubmissionRepository {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final ScanDraftStore store;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ScanSubmissionRepository(Context context) {
        store = new ScanDraftStore(context.getApplicationContext().getNoBackupFilesDir().toPath()
                .resolve("scan-submissions"));
    }

    public interface Callback {
        void saved(ScanDraftStore.SavedDraft draft);
        void failed(Exception exception);
    }

    public interface SignaturesCallback {
        void ready(ScanUploadSignaturesResponse signatures);
        void failed(Exception exception);
    }

    /** Only a GET. Signatures stay in memory and are never written into the draft. */
    public void requestUploadSignatures(String uid, String scanId, SignaturesCallback callback) {
        IO.execute(() -> {
            try {
                ScanDraftStore.SavedDraft saved = store.read(uid, scanId);
                FirebaseScanAuthSession auth = new FirebaseScanAuthSession();
                auth.requireSameUser(saved.firebaseUid);
                Log.d("VENUS_SCAN_SIGNATURES", "REQUEST scanId=" + saved.draft.getScanId());
                ScanUploadSignaturesResponse signatures = new AuthenticatedScanClient(ClienteApi.scans(), auth)
                        .signatures(saved.firebaseUid, saved.draft.getScanId());
                main.post(() -> {
                    try {
                        // Discard the response if the account changed while the GET was running.
                        auth.requireSameUser(saved.firebaseUid);
                    } catch (Exception e) {
                        logSignatureFailure(scanId, e);
                        callback.failed(e);
                        return;
                    }
                    Log.d("VENUS_SCAN_SIGNATURES", "READY scanId=" + scanId + " front=true back=true");
                    callback.ready(signatures);
                });
            } catch (Exception e) {
                logSignatureFailure(scanId, e);
                main.post(() -> callback.failed(e));
            }
        });
    }

    private static void logSignatureFailure(String scanId, Exception error) {
        String reason = error instanceof ScanApiException ? ((ScanApiException) error).kind.name() : "LOCAL";
        int code = error instanceof ScanApiException ? ((ScanApiException) error).httpCode : 0;
        Log.w("VENUS_SCAN_SIGNATURES", "FAILED scanId=" + scanId + " reason=" + reason + " http=" + code);
    }

    /** Local preparation only. Never reports an upload or Mongo commit. */
    public void prepareLocally(String uid, ScanSubmissionDraft draft, Callback callback) {
        IO.execute(() -> {
            try {
                draft.setPhotoQuality(ScanPhotoQualityAnalyzer.aggregate(
                        draft.getFrontPhotoPath(), draft.getBackPhotoPath()));
                com.venussystem.venusmobile.model.ScanPhotoQuality quality = draft.getPhotoQuality();
                Log.d("VENUS_SCAN_QUALITY", "side=AGGREGATE blurScore=" + quality.getBlurScore()
                        + " brightness=" + quality.getBrightness()
                        + " backgroundOk=" + quality.isBackgroundOk()
                        + " status=" + quality.getStatus());
                ScanDraftStore.SavedDraft saved = store.save(uid, draft);
                main.post(() -> callback.saved(saved));
            } catch (Exception e) {
                main.post(() -> callback.failed(e));
            }
        });
    }
}
