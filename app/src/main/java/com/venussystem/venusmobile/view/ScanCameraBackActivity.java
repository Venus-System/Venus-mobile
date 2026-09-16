package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import com.venussystem.venusmobile.R;

public class ScanCameraBackActivity extends ScanCameraBaseActivity {
    @Override protected int layoutResId() { return R.layout.activity_scan_camera_back; }
    @Override protected Class<? extends AppCompatActivity> successActivity() { return ScanSuccessBackActivity.class; }
    @Override protected String outputFileName() { return "scan_back.jpg"; }
}
