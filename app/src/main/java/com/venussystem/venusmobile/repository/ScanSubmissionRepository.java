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
    private final ScanIngredientCatalogCache ingredientCatalogCache;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ScanSubmissionRepository(Context context) {
        java.nio.file.Path noBackup = context.getApplicationContext().getNoBackupFilesDir().toPath();
        store = new ScanDraftStore(noBackup.resolve("scan-submissions"));
        // The catalogue is public reference data, not user evidence. Keep one
        // atomic app-private snapshot so a scan with many ingredients does not
        // issue one network request per token. It is refreshed by
        // ScanIngredientCatalog after its bounded TTL and is safe to discard.
        ingredientCatalogCache = new ScanIngredientCatalogCache(
                noBackup.resolve("scan-catalog").resolve("ingredients.json"));
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

    /** Envios desta conta que ficaram pela metade (ver ReenvioDeScan). */
    public java.util.List<ScanDraftStore.SavedDraft> pendentes(String uid) throws java.io.IOException {
        return store.pending(uid);
    }

    public long ultimaAlteracaoMs(String uid, String scanId) throws java.io.IOException {
        return store.lastModifiedMs(uid, scanId);
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
                    Log.d("VENUS_SCAN_SUBMISSION", "ALREADY_SUBMITTED scanId=" + scanId
                            + " id=" + saved.submission.id + " status=" + saved.submission.status);
                    main.post(() -> callback.submitted(saved));
                    return;
                }
                stage = "VALIDATE_LOCAL_STATE";
                if (saved.frontUpload == null || saved.backUpload == null)
                    throw new IllegalStateException("As duas fotos precisam estar confirmadas antes do cadastro.");
                saved.frontUpload.validate(scanId, "front");
                saved.backUpload.validate(scanId, "back");
                // A catalogue outage must not erase OCR evidence or prevent a
                // draft from reaching the existing PENDING_REVIEW workflow.
                // The backend will keep these raw names pending for administrative review.
                if (saved.ingredientCatalog == null) {
                    saved.ingredientCatalog = unresolvedCatalog(saved.draft);
                }
                try {
                    ScanIngredientCatalogReportValidator.validate(
                            saved.ingredientCatalog,
                            saved.draft.getBackData().getIngredients()
                    );
                } catch (IllegalArgumentException invalidReport) {
                    // A report from an older app/cache version must never
                    // promote an ID against a different OCR position.
                    Log.w("VENUS_SCAN_SUBMISSION", "CATALOG_REPORT_RESET scanId=" + scanId);
                    saved.ingredientCatalog = unresolvedCatalog(saved.draft);
                }
                stage = "BUILD_REQUEST";
                String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                if (deviceId == null || deviceId.trim().isEmpty()) deviceId = "android-" + Build.FINGERPRINT;
                String version;
                try { version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
                catch (Exception ignored) { version = "unknown"; }
                // Mongo's persisted contract uses lowercase platform tokens.
                ScanSessionRequest.Device device = new ScanSessionRequest.Device(deviceId, "android", version,
                        Build.MANUFACTURER + " " + Build.MODEL);
                ScanSessionRequest request = ScanSubmissionMapper.prepare(saved.draft, uid, device, null,
                        saved.ingredientCatalog);
                request.images.front = saved.frontUpload.image;
                request.images.back = saved.backUpload.image;
                Log.d("VENUS_SCAN_SUBMISSION", "REQUEST scanId=" + scanId);
                stage = "VALIDATE_REQUEST";
                ScanSubmissionValidator.validate(request, true);
                stage = "POST_SCAN_SESSION";
                ScanSessionResponse response = new AuthenticatedScanClient(ClienteApi.scans(), auth).submit(request);
                stage = "ACKNOWLEDGE_RESPONSE";
                auth.requireSameUser(uid);
                ScanDraftStore.SavedDraft result = store.recordSubmission(uid, scanId, response);
                Log.d("VENUS_SCAN_SUBMISSION", "MONGO_SUBMITTED scanId=" + scanId + " id=" + response.id
                        + " status=" + response.status);
                main.post(() -> callback.submitted(result));
            } catch (Exception e) {
                int code = e instanceof ScanApiException ? ((ScanApiException) e).httpCode : 0;
                Log.w("VENUS_SCAN_SUBMISSION", "FAILED scanId=" + scanId + " kind="
                        + (e instanceof ScanApiException ? ((ScanApiException) e).kind : "LOCAL")
                        + " stage=" + stage + " http=" + code
                        + " error=" + e.getClass().getSimpleName()
                        + " reason=" + safeLocalReason(e));
                main.post(() -> callback.failed(e));
            }
        });
    }

    /**
     * Local validation messages are fixed contract errors and contain no OCR,
     * image path, token or user identifier. Network/API errors keep only their
     * typed kind so backend diagnostics never enter production logs.
     */
    private static String safeLocalReason(Exception error) {
        if (error instanceof ScanApiException) {
            return ((ScanApiException) error).kind.name();
        }
        if (!(error instanceof IllegalArgumentException)
                && !(error instanceof IllegalStateException)) {
            return "UNAVAILABLE";
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "UNAVAILABLE";
        return message.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static ScanIngredientCatalog.Report unresolvedCatalog(ScanSubmissionDraft draft) {
        ScanIngredientCatalog.Report report = new ScanIngredientCatalog.Report();
        report.checkedAt = java.time.OffsetDateTime.now().toString();
        if (draft == null || draft.getBackData() == null) return report;
        int position = 1;
        for (com.venussystem.venusmobile.model.ScanBackIngredientCandidate candidate
                : draft.getBackData().getIngredients()) {
            ScanIngredientCatalog.Match match = new ScanIngredientCatalog.Match();
            match.position = candidate.getPosition() > 0 ? candidate.getPosition() : position;
            match.rawName = candidate.getRawName();
            match.normalizedName = candidate.getNormalizedName();
            match.status = ScanIngredientCatalog.STATUS_NOT_FOUND;
            match.resolutionStatus = ScanIngredientCatalog.RESOLUTION_UNRESOLVED;
            match.resolutionMethod = "CATALOG_UNAVAILABLE";
            match.needsReview = true;
            report.matches.add(match);
            ScanIngredientCatalog.Issue issue = new ScanIngredientCatalog.Issue();
            issue.position = match.position;
            issue.rawName = match.rawName;
            issue.normalizedName = match.normalizedName;
            issue.type = "SOURCE_CONFLICT";
            issue.reason = "CATALOG_UNAVAILABLE";
            report.issues.add(issue);
            report.notFound++;
            report.unresolved++;
            report.needsReview = true;
            position++;
        }
        report.expectedCount = report.matches.size();
        report.validationStatus = report.expectedCount == 0
                ? "INVALID" : ScanIngredientCatalog.VALIDATION_NEEDS_REVIEW;
        return report;
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
                Log.d("VENUS_SCAN_INGREDIENTS", "REQUEST scanId=" + scanId + " candidates=" + names.size());
                ScanIngredientCatalog.Report report = new ScanIngredientCatalog(ClienteApi.ingredients(),
                        () -> auth.requireSameUser(uid), ingredientCatalogCache).lookup(names);
                auth.requireSameUser(uid);
                ScanDraftStore.SavedDraft result = store.recordIngredientCatalog(uid, scanId, report);
                main.post(() -> {
                    try { auth.requireSameUser(uid); }
                    catch (Exception e) { callback.failed(e); return; }
                    Log.d("VENUS_SCAN_INGREDIENTS", "READY scanId=" + scanId + " known=" + report.known
                            + " alias=" + report.alias + " ambiguous=" + report.ambiguous
                            + " notFound=" + report.notFound + " errors=" + report.errors + " mongo=false");
                    callback.saved(result);
                });
            } catch (Exception e) {
                if (isDeferrableCatalogFailure(e)) {
                    try {
                        FirebaseScanAuthSession auth = new FirebaseScanAuthSession();
                        auth.requireSameUser(uid);
                        ScanDraftStore.SavedDraft saved = store.read(uid, scanId);
                        ScanIngredientCatalog.Report deferred = unresolvedCatalog(saved.draft);
                        ScanDraftStore.SavedDraft result = store.recordIngredientCatalog(uid, scanId, deferred);
                        Log.w("VENUS_SCAN_INGREDIENTS", "DEFERRED scanId=" + scanId + " reason=CATALOG_UNAVAILABLE");
                        main.post(() -> callback.saved(result));
                        return;
                    } catch (Exception ignored) {
                        // Fall through to the normal error callback if the
                        // draft/account cannot be verified locally.
                    }
                }
                Log.w("VENUS_SCAN_INGREDIENTS", "FAILED scanId=" + scanId
                        + " kind=" + e.getClass().getSimpleName()
                        + " http=" + (e instanceof ScanApiException ? ((ScanApiException) e).httpCode : 0));
                main.post(() -> callback.failed(e));
            }
        });
    }

    private static boolean isDeferrableCatalogFailure(Exception error) {
        if (error instanceof ScanApiException) {
            ScanApiException apiError = (ScanApiException) error;
            return apiError.kind == ScanApiException.Kind.NETWORK
                    || apiError.kind == ScanApiException.Kind.TIMEOUT
                    || (apiError.kind == ScanApiException.Kind.HTTP && apiError.httpCode >= 500);
        }
        return error instanceof java.io.IOException;
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
                            Log.d("VENUS_SCAN_UPLOAD", event + " scanId=" + scanId + " side=" + side);
                            if ("START".equals(event)) main.post(() -> callback.progress(side));
                        });
                main.post(() -> {
                    try { auth.requireSameUser(uid); }
                    catch (Exception e) { callback.failed(e); return; }
                    Log.d("VENUS_SCAN_UPLOAD", "PHOTOS_READY scanId=" + scanId + " front=true back=true");
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
                Log.d("VENUS_SCAN_SIGNATURES", "REQUEST scanId=" + saved.draft.getScanId());
                AuthenticatedScanClient client = new AuthenticatedScanClient(ClienteApi.scans(), auth);
                ScanUploadSignaturesResponse signatures;
                if (connectAccount) {
                    SessaoFirebase profile = new SessaoFirebase();
                    String name = profile.nome();
                    String email = profile.email();
                    auth.requireSameUser(saved.firebaseUid);
                    Log.d("VENUS_SCAN_SIGNATURES", "ACCOUNT_PROVISIONING scanId=" + scanId);
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

    /** Computes quality and saves the durable local snapshot before any network operation. */
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
                if (ScanPhotoQualityPolicy.shouldBlock(quality)) {
                    throw new IllegalArgumentException("A qualidade das fotos não permite uma leitura segura ("
                            + ScanPhotoQualityPolicy.blockReason(quality) + "). Tire novas fotos do rótulo.");
                }
                if (ScanPhotoQualityPolicy.backgroundIsAdvisory(quality)) {
                    Log.d("VENUS_SCAN_QUALITY", "BACKGROUND_ADVISORY=true");
                }
                ScanDraftStore.SavedDraft saved = store.save(uid, draft);
                main.post(() -> callback.saved(saved));
            } catch (Exception e) {
                main.post(() -> callback.failed(e));
            }
        });
    }
}
