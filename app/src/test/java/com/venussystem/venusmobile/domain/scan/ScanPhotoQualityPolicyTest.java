package com.venussystem.venusmobile.domain.scan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.venussystem.venusmobile.model.ScanPhotoQuality;

import org.junit.Test;

public class ScanPhotoQualityPolicyTest {
    @Test
    public void blocksMissingQuality() {
        assertTrue(ScanPhotoQualityPolicy.shouldBlock(null));
        assertFalse(ScanPhotoQualityPolicy.backgroundIsAdvisory(null));
    }

    @Test
    public void blocksOnlyBlurAndBrightnessLimits() {
        assertTrue(ScanPhotoQualityPolicy.shouldBlock(
                new ScanPhotoQuality(.85d, .5d, true, "COMPLETED")));
        assertTrue(ScanPhotoQualityPolicy.shouldBlock(
                new ScanPhotoQuality(.2d, .079d, true, "COMPLETED")));
        assertTrue(ScanPhotoQualityPolicy.shouldBlock(
                new ScanPhotoQuality(.2d, .951d, true, "COMPLETED")));
        assertFalse(ScanPhotoQualityPolicy.shouldBlock(
                new ScanPhotoQuality(.2d, .5d, true, "COMPLETED")));
    }

    @Test
    public void backgroundIsAdvisoryWhenTheLabelMetricsAreReadable() {
        ScanPhotoQuality quality = new ScanPhotoQuality(.2d, .5d, false, "COMPLETED");
        assertFalse(ScanPhotoQualityPolicy.shouldBlock(quality));
        assertTrue(ScanPhotoQualityPolicy.backgroundIsAdvisory(quality));
    }

    @Test
    public void blockedQualityIsNotDowngradedToBackgroundAdvisory() {
        ScanPhotoQuality quality = new ScanPhotoQuality(.9d, .5d, false, "COMPLETED");
        assertTrue(ScanPhotoQualityPolicy.shouldBlock(quality));
        assertFalse(ScanPhotoQualityPolicy.backgroundIsAdvisory(quality));
    }
}
