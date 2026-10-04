package com.venussystem.venusmobile.view.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.IOException;
import com.venussystem.venusmobile.model.ScanPhotoQuality;

/**
 * Small deterministic quality check executed locally. It never rejects OCR data.
 * blurScore is normalized to [0,1], where higher means more blur; brightness is [0,1].
 */
public final class ScanPhotoQualityAnalyzer {
    private static final int MAX_SIDE = 640;
    private ScanPhotoQualityAnalyzer() { }

    public static ScanPhotoQuality aggregate(String frontPath, String backPath) throws IOException {
        ScanPhotoQuality front = analyze(frontPath);
        ScanPhotoQuality back = analyze(backPath);
        return new ScanPhotoQuality(
                Math.max(front.getBlurScore(), back.getBlurScore()),
                (front.getBrightness() + back.getBrightness()) / 2d,
                front.isBackgroundOk() && back.isBackgroundOk(),
                "COMPLETED");
    }

    public static ScanPhotoQuality analyze(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) throw new IOException("Caminho da foto ausente.");
        File file = new File(path);
        if (!file.isFile() || file.length() == 0) throw new IOException("Arquivo da foto indisponível.");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Foto inválida.");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IOException("Não foi possível decodificar a foto.");
        try { return measure(bitmap); }
        finally { bitmap.recycle(); }
    }

    static ScanPhotoQuality measure(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        if (width < 16 || height < 16) throw new IllegalArgumentException("Foto pequena demais.");
        // The decoded image is already capped at 640 px. Sampling every 4 px
        // skipped the thin label text and made every photo look uniformly blurry.
        int step = 1;
        double sum = 0, sum2 = 0, lapSum = 0, lap2 = 0;
        long count = 0;
        double borderSum = 0, border2 = 0, centerSum = 0, center2 = 0;
        long borderCount = 0, centerCount = 0;
        for (int y = 1; y < height - 1; y += step) {
            for (int x = 1; x < width - 1; x += step) {
                double c = gray(bitmap.getPixel(x, y));
                double l = gray(bitmap.getPixel(x - 1, y));
                double r = gray(bitmap.getPixel(x + 1, y));
                double u = gray(bitmap.getPixel(x, y - 1));
                double d = gray(bitmap.getPixel(x, y + 1));
                double lap = l + r + u + d - 4d * c;
                sum += c; sum2 += c * c; lapSum += lap; lap2 += lap * lap; count++;
                boolean border = x < width * .12 || x >= width * .88 || y < height * .12 || y >= height * .88;
                if (border) { borderSum += c; border2 += c * c; borderCount++; }
                else { centerSum += c; center2 += c * c; centerCount++; }
            }
        }
        double mean = sum / count;
        double variance = Math.max(0, sum2 / count - mean * mean);
        double lapVariance = Math.max(0, lap2 / count - Math.pow(lapSum / count, 2));
        // A high Laplacian variance means edges are preserved and therefore less blur.
        // Pixels are normalized to [0,1], so the old 1800-scale (appropriate
        // for 0..255 pixels) collapsed every score to almost exactly 1.
        double blur = 1d / (1d + lapVariance / .0025d);
        double borderVariance = borderCount == 0 ? 1 : Math.max(0,
                border2 / borderCount - Math.pow(borderSum / borderCount, 2));
        double centerMean = centerCount == 0 ? mean : centerSum / centerCount;
        boolean backgroundOk = borderVariance < .055 && Math.abs(borderSum / Math.max(1, borderCount)
                - centerMean) < .45 && variance > .002;
        return new ScanPhotoQuality(blur, mean, backgroundOk, "COMPLETED");
    }

    private static int sampleSize(int width, int height) {
        int largest = Math.max(width, height), sample = 1;
        while (largest / sample > MAX_SIDE) sample *= 2;
        return sample;
    }
    private static double gray(int color) {
        return (0.299 * ((color >> 16) & 255) + 0.587 * ((color >> 8) & 255)
                + 0.114 * (color & 255)) / 255d;
    }
}
