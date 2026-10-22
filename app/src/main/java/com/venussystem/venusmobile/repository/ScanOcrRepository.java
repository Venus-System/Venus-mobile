package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.venussystem.venusmobile.model.ScanOcrAttempt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanOcrRepository {

    public interface Callback {
        void onSuccess(@NonNull Text result);
        void onError(@NonNull Exception exception);
    }

    public interface BestCallback {
        void onSuccess(@NonNull Text result, @NonNull List<ScanOcrAttempt> attempts);
        void onError(@NonNull Exception exception);
    }

    private static final int MAX_VARIANT_SIDE = 1600;

    private final TextRecognizer recognizer;
    private final ExecutorService passExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "venus-ocr-passes");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public ScanOcrRepository() {
        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
    }

    /** Legacy single-pass entry point kept for callers outside the scan flow. */
    public void recognize(
            @NonNull Context context,
            @NonNull Uri imageUri,
            @NonNull Callback callback
    ) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            Task<Text> task = recognizer.process(image);
            task.addOnSuccessListener(callback::onSuccess);
            task.addOnFailureListener(callback::onError);
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    /**
     * Runs a bounded set of local OCR passes and returns the strongest result.
     * The original image is attempted first and never replaced. Derived images
     * exist only in the app cache and are deleted after selection.
     */
    public void recognizeBest(
            @NonNull Context context,
            @NonNull Uri imageUri,
            @NonNull BestCallback callback
    ) {
        if (closed) {
            callback.onError(new IllegalStateException("OCR já encerrado."));
            return;
        }
        try {
            passExecutor.execute(() -> {
                List<OcrPass> passes = new ArrayList<>();
                try {
                    passes.add(new OcrPass("ORIGINAL", imageUri, null));
                    passes.addAll(buildDerivedPasses(context, imageUri));
                } catch (Exception ignored) {
                    // The original pass remains valid when a derived image cannot
                    // be generated on a constrained device.
                }
                runPass(context, passes, 0, new ArrayList<>(), callback);
            });
        } catch (RuntimeException ignored) {
            callback.onError(new IllegalStateException("OCR já encerrado."));
        }
    }

    private void runPass(
            @NonNull Context context,
            @NonNull List<OcrPass> passes,
            int index,
            @NonNull List<AttemptResult> results,
            @NonNull BestCallback callback
    ) {
        if (closed || index >= passes.size()) {
            finishPasses(passes, results, callback);
            return;
        }

        OcrPass pass = passes.get(index);
        try {
            InputImage image = InputImage.fromFilePath(context, pass.uri);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        results.add(new AttemptResult(
                                text,
                                attemptFor(pass.name, text)
                        ));
                        runPass(context, passes, index + 1, results, callback);
                    })
                    .addOnFailureListener(error -> {
                        results.add(new AttemptResult(
                                null,
                                new ScanOcrAttempt(
                                        pass.name, 0, 0, 0d, false, false,
                                        "OCR_PASS_FAILED"
                                )
                        ));
                        runPass(context, passes, index + 1, results, callback);
                    });
        } catch (Exception error) {
            results.add(new AttemptResult(
                    null,
                    new ScanOcrAttempt(
                            pass.name, 0, 0, 0d, false, false, "OCR_PASS_FAILED"
                    )
            ));
            runPass(context, passes, index + 1, results, callback);
        }
    }

    private void finishPasses(
            @NonNull List<OcrPass> passes,
            @NonNull List<AttemptResult> results,
            @NonNull BestCallback callback
    ) {
        cleanup(passes);
        if (closed) {
            return;
        }
        AttemptResult best = null;
        for (AttemptResult result : results) {
            if (result.text == null || !result.attempt.isSuccessful()) continue;
            // Keep the earlier pass on ties; ORIGINAL is intentionally first.
            if (best == null || result.attempt.getScore() > best.attempt.getScore()) {
                best = result;
            }
        }
        if (best == null) {
            callback.onError(new IOException("Nenhuma passagem de OCR produziu texto."));
            return;
        }

        List<ScanOcrAttempt> attempts = new ArrayList<>();
        for (AttemptResult result : results) {
            attempts.add(result.attempt.asSelected(result == best));
        }
        callback.onSuccess(best.text, Collections.unmodifiableList(attempts));
    }

    private ScanOcrAttempt attemptFor(String variant, Text text) {
        String value = text.getText() == null ? "" : text.getText().trim();
        int lines = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            lines += block.getLines().size();
        }
        return new ScanOcrAttempt(
                variant,
                value.length(),
                lines,
                score(value, lines),
                true,
                false,
                null
        );
    }

    /** Conservative score: text amount plus section/separator evidence. */
    private static double score(String text, int lines) {
        if (text == null || text.trim().isEmpty()) return 0d;
        String upper = text.toUpperCase(Locale.ROOT);
        int separators = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',' || c == ';' || c == ':') separators++;
        }
        double score = Math.min(1d, text.length() / 3500d) * .35d;
        score += Math.min(1d, lines / 36d) * .15d;
        score += Math.min(1d, separators / 14d) * .20d;
        if (upper.contains("INGREDIENT") || upper.contains("INGREDIENTES")) score += .20d;
        if (upper.contains("MODO DE USO") || upper.contains("ADVERT")) score -= .08d;
        return Math.max(0d, Math.min(1d, score));
    }

    private List<OcrPass> buildDerivedPasses(Context context, Uri sourceUri) throws IOException {
        Bitmap source = decodeSampled(context, sourceUri);
        if (source == null) return Collections.emptyList();
        File directory = new File(context.getCacheDir(), "scan/ocr-passes");
        if (!directory.exists() && !directory.mkdirs()) {
            source.recycle();
            return Collections.emptyList();
        }
        List<OcrPass> passes = new ArrayList<>();
        try {
            tryAddVariant(passes, directory, "CONTRAST", enhanceContrast(source));
            tryAddVariant(passes, directory, "CENTER_CROP", centerCrop(source));
            tryAddVariant(passes, directory, "ROTATED_180", rotate180(source));
        } finally {
            source.recycle();
        }
        return passes;
    }

    private static void tryAddVariant(List<OcrPass> passes, File directory,
                                      String name, Bitmap bitmap) {
        try {
            addVariant(passes, directory, name, bitmap);
        } catch (IOException | RuntimeException ignored) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static void addVariant(List<OcrPass> passes, File directory,
                                   String name, Bitmap bitmap) throws IOException {
        if (bitmap == null) return;
        File output = new File(directory, name.toLowerCase(Locale.ROOT) + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                throw new IOException("Não foi possível criar passagem de OCR.");
            }
        } finally {
            bitmap.recycle();
        }
        passes.add(new OcrPass(name, Uri.fromFile(output), output));
    }

    private static Bitmap enhanceContrast(Bitmap source) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                1.30f, 0, 0, 0, -32,
                0, 1.30f, 0, 0, -32,
                0, 0, 1.30f, 0, -32,
                0, 0, 0, 1, 0
        })));
        canvas.drawBitmap(source, 0, 0, paint);
        return result;
    }

    private static Bitmap centerCrop(Bitmap source) {
        int x = Math.max(0, Math.round(source.getWidth() * .08f));
        int y = Math.max(0, Math.round(source.getHeight() * .08f));
        int width = Math.max(1, source.getWidth() - (x * 2));
        int height = Math.max(1, source.getHeight() - (y * 2));
        return Bitmap.createBitmap(source, x, y, width, height);
    }

    private static Bitmap rotate180(Bitmap source) {
        Matrix matrix = new Matrix();
        matrix.postRotate(180f);
        return Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(), matrix, true
        );
    }

    private static Bitmap decodeSampled(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        try (InputStream input = openStream(context, uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_VARIANT_SIDE) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = openStream(context, uri)) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }

    private static InputStream openStream(Context context, Uri uri) throws IOException {
        InputStream stream = context.getContentResolver().openInputStream(uri);
        if (stream == null) throw new IOException("Imagem indisponível.");
        return stream;
    }

    private static void cleanup(List<OcrPass> passes) {
        for (OcrPass pass : passes) {
            if (pass.temporaryFile != null && pass.temporaryFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pass.temporaryFile.delete();
            }
        }
    }

    private static final class OcrPass {
        final String name;
        final Uri uri;
        final File temporaryFile;

        OcrPass(String name, Uri uri, File temporaryFile) {
            this.name = name;
            this.uri = uri;
            this.temporaryFile = temporaryFile;
        }
    }

    private static final class AttemptResult {
        final Text text;
        final ScanOcrAttempt attempt;

        AttemptResult(Text text, ScanOcrAttempt attempt) {
            this.text = text;
            this.attempt = attempt;
        }
    }

    public void close() {
        closed = true;
        passExecutor.shutdownNow();
        recognizer.close();
    }
}
