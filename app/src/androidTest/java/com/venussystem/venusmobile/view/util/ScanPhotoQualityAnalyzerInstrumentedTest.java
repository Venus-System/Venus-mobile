package com.venussystem.venusmobile.view.util;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import org.junit.Test;

/** Runs on Android because Bitmap is an Android framework type. */
public class ScanPhotoQualityAnalyzerInstrumentedTest {
    @Test public void metricsAreBoundedForUniformAndEdgeImages() {
        Bitmap uniform = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 80; y++) for (int x = 0; x < 80; x++) uniform.setPixel(x, y, 0xffeeeeee);
        assertBounded(ScanPhotoQualityAnalyzer.measure(uniform));
        Bitmap edge = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 80; y++) for (int x = 0; x < 80; x++)
            edge.setPixel(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xffffffff : 0xff111111);
        assertBounded(ScanPhotoQualityAnalyzer.measure(edge));
        assertTrue(ScanPhotoQualityAnalyzer.measure(uniform).getBlurScore()
                > ScanPhotoQualityAnalyzer.measure(edge).getBlurScore());
        assertTrue(ScanPhotoQualityAnalyzer.measure(edge).getBlurScore() < .99);
        uniform.recycle(); edge.recycle();
    }
    @Test public void qualityDoesNotClaimAUniformBlankImageIsValidBackground() {
        Bitmap blank = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        assertFalse(ScanPhotoQualityAnalyzer.measure(blank).isBackgroundOk());
        blank.recycle();
    }
    private static void assertBounded(com.venussystem.venusmobile.model.ScanPhotoQuality q) {
        assertTrue(q.getBlurScore() >= 0 && q.getBlurScore() <= 1);
        assertTrue(q.getBrightness() >= 0 && q.getBrightness() <= 1);
        assertNotNull(q.getStatus());
    }
}
