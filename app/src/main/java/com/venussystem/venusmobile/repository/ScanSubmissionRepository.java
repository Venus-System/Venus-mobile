package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.repository.api.AuthenticatedScanClient;
import com.venussystem.venusmobile.repository.api.CloudinaryUploadClient;
import com.venussystem.venusmobile.repository.api.ScanUploadException;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.FirebaseScanAuthSession;
import com.venussystem.venusmobile.repository.api.ScanApiException;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionRequest;
import com.venussystem.venusmobile.repository.api.dto.ScanSessionResponse;
import android.os.Build;
import android.provider.Settings;
import com.venussystem.venusmobile.view.util.ScanPhotoQualityAnalyzer;
import com.venussystem.venusmobile.domain.scan.ScanPhotoQualityPolicy;
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

    public interface UploadCallback {
        void progress(String side);
        void uploaded(ScanDraftStore.SavedDraft saved);
        void failed(Exception error);
    }

    public interface SubmissionCallback {
        void submitted(ScanDraftStore.SavedDraft saved);
        void failed(Exception exception);
    }

    /** Sends the durable, uploaded draft to MongoDB exactly once from the app's perspective. */
    public void submitToMongo(String uid, String scanId, Context context, SubmissionCallback callback) {
        IO.execute(() -> {
            String stage = "AUTH";
            try {
                FirebaseScanAuthSession auth = new FirebaseScanAuthSession();
                auth.requireSameUser(uid);
                stage = "READ_DRAFT";
                ScanDraftStore.SavedDraft saved = store.read(uid, scanId);
                if (saved.submission != null) {
                    main.post(() -> callback.submitted(saved));
                    return;
                }
                stage = "VALIDATE_LOCAL_STATE";
                if (saved.frontUpload == null || saved.backUpload == null)
                    throw new IllegalStateException("As duas fotos precisam estar confirmadas antes do cadastro.");
                if (saved.ingredientCatalog == null)
                    throw new IllegalStateException("A consulta dos ingredientes precisa ser concluída antes do cadastro.");
                saved.frontUpload.validate(scanId, "front");
                saved.backUpload.validate(scanId, "back");
                stage = "BUILD_REQUEST";
                String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                if (deviceId == null || deviceId.trim().isEmpty()) deviceId = "android-" + Build.FINGERPRINT;
                String version;
                try { version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
                catch (Exception ignored) { version = "unknown"; }
                ScanSessionRequest.Device device = new ScanSessionRequest.Device(deviceId, "Android", version,
                        Build.MANUFACTURER + " " + Build.MODEL);
                ScanSessionRequest request = ScanSubmissionMapper.prepare(saved.draft, uid, device, null);
                request.images.front = saved.frontUpload.image;
                request.images.back = saved.backUpload.image;
                stage = "POST_SCAN_SESSION";
                ScanSessionResponse response = new AuthenticatedScanClient(ClienteApi.scans(), auth).submit(request);
                stage = "ACKNOWLEDGE_RESPONSE";
                auth.requireSameUser(uid);
                ScanDraftStore.SavedDraft result = store.recordSubmission(uid, scanId, response);
                main.post(() -> callback.submitted(result));
            } catch (Exception e) {
                int code = e instanceof ScanApiException ? ((ScanApiException) e).httpCode : 0;
                Log.w("VENUS_SCAN_SUBMISSION", "FAILED scanId=" + scanId + " kind="
                        + (e instanceof ScanApiException ? ((ScanApiException) e).kind : "LOCAL")
                        + " stage=" + stage + " http=" + code
                        + " error=" + e.getClass().getSimpleName()
                        + " message=" + safeDiagnosticMessage(e.getMessage()));
                main.post(() -> callback.failed(e));
            }
        });
    }

    private static String safeDiagnosticMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "none";
        String compact = message.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 160);
    }

    /** Read-only catalog preflight. Does not submit to Mongo or mark the product approved. */
    public void checkIngredients(String uid, String scanId, Callback callback) {
        IO.execute(() -> {
            try {
                FirebaseScanAuthSession auth = new FirebaseScanAuthSession();
                auth.requireSameUser(uid);
                ScanDraftStore.SavedDraft saved = store.read(uid, scanId);
                java.util.List<String> names = new java.util.ArrayList<>();
                saved.draft.getBackData().getIngredients().forEach(i -> names.add(i.getRawName()));
                ScanIngredientCatalog.Report report = new ScanIngredientCatalog(ClienteApi.ingredients(),
                        () -> auth.requireSameUser(uid)).lookup(names);
                auth.requireSameUser(uid);
                ScanDraftStore.SavedDraft result = store.recordIngredientCatalog(uid, scanId, report);
                main.post(() -> {
                    try { auth.requireSameUser(uid); }
                    catch (Exception e) { callback.failed(e); return; }
                    callback.saved(result);
                });
            } catch (Exception e) {
                Log.w("VENUS_SCAN_INGREDIENTS", "FAILED scanId=" + scanId
                        + " kind=" + e.getClass().getSimpleName()
                        + " http=" + (e instanceof ScanApiException ? ((ScanApiException) e).httpCode : 0));
                main.post(() -> callback.failed(e));
            }
        });
    }

    /** Uploads both photos and leaves the durable draft ready for the catalog/POST stages. */
    public void uploadPhotos(String uid, String scanId, ScanUploadSignaturesResponse signatures, UploadCallback callback) {
        IO.execute(() -> {
            try {
                FirebaseScanAuthSession auth = new FirebaseScanAuthSession();
                AuthenticatedScanClient api = new AuthenticatedScanClient(ClienteApi.scans(), auth);
                CloudinaryUploadClient cloud = new CloudinaryUploadClient();
                ScanDraftStore.SavedDraft saved = new ScanPhotoUploadCoordinator(store,
                        () -> api.signatures(uid, scanId), () -> auth.requireSameUser(uid), cloud::upload)
                        .run(uid, scanId, signatures, (event, side) -> {
                            if ("START".equals(event)) main.post(() -> callback.progress(side));
                        });
                main.post(() -> {
                    try { auth.requireSameUser(uid); }
                    catch (Exception e) { callback.failed(e); return; }
                    callback.uploaded(saved);
                });
            } catch (Exception e) {
                String reason = e instanceof ScanUploadException ? ((ScanUploadException) e).kind.name()
                        : e instanceof ScanApiException ? ((ScanApiException) e).kind.name() : "LOCAL";
                int code = e instanceof ScanUploadException ? ((ScanUploadException) e).httpCode
                        : e instanceof ScanApiException ? ((ScanApiException) e).httpCode : 0;
                Log.w("VENUS_SCAN_UPLOAD", "FAILED scanId=" + scanId + " reason=" + reason + " http=" + code);
                main.post(() -> callback.failed(e));
            }
        });
    }

    /** Only a GET. Signatures stay in memory and are never written into the draft. */
    public void requestUploadSignatures(String uid, String scanId, SignaturesCallback callback) {
        requestUploadSignatures(uid, scanId, false, callback);
    }

    /** Explicit account-connection action. Never called automatically because of a 403. */
    public void connectAccountAndRequestSignatures(String uid, String scanId, SignaturesCallback callback) {
        requestUploadSignatures(uid, scanId, true, callback);
    }

    private void requestUploadSignatures(String uid, String scanId, boolean connectAccount,
                                         SignaturesCallback callback) {
        IO.execute(() -> {
            try {
                ScanDraftStore.SavedDraft saved = store.read(uid, scanId);
                FirebaseScanAuthSession auth = new FirebaseScanAuthSession();
                auth.requireSameUser(saved.firebaseUid);
                AuthenticatedScanClient client = new AuthenticatedScanClient(ClienteApi.scans(), auth);
                ScanUploadSignaturesResponse signatures;
                if (connectAccount) {
                    SessaoFirebase profile = new SessaoFirebase();
                    String name = profile.nome();
                    String email = profile.email();
                    auth.requireSameUser(saved.firebaseUid);
                    signatures = client.connectAccountAndGetSignatures(saved.firebaseUid, name, email, scanId);
                } else {
                    signatures = client.signatures(saved.firebaseUid, saved.draft.getScanId());
                }
                main.post(() -> {
                    try {
                        // Discard the response if the account changed while the GET was running.
                        auth.requireSameUser(saved.firebaseUid);
                    } catch (Exception e) {
                        logSignatureFailure(scanId, e);
                        callback.failed(e);
                        return;
                    }
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

    /** Computes quality and saves the durable local snapshot before any network operation. */
    public void prepareLocally(String uid, ScanSubmissionDraft draft, Callback callback) {
        IO.execute(() -> {
            try {
                draft.setPhotoQuality(ScanPhotoQualityAnalyzer.aggregate(
                        draft.getFrontPhotoPath(), draft.getBackPhotoPath()));
                com.venussystem.venusmobile.model.ScanPhotoQuality quality = draft.getPhotoQuality();
                if (ScanPhotoQualityPolicy.shouldBlock(quality)) {
                    throw new IllegalArgumentException("A qualidade das fotos não permite uma leitura segura ("
                            + ScanPhotoQualityPolicy.blockReason(quality) + "). Tire novas fotos do rótulo.");
                }
                ScanDraftStore.SavedDraft saved = store.save(uid, draft);
                main.post(() -> callback.saved(saved));
            } catch (Exception e) {
                main.post(() -> callback.failed(e));
            }
        });
    }
}
