package com.venussystem.venusmobile.view;

import androidx.appcompat.app.AppCompatActivity;
import com.venussystem.venusmobile.R;

public class ScanCameraFrontActivity extends ScanCameraBaseActivity {
    @Override protected int layoutResId() { return R.layout.activity_scan_camera_front; }
    @Override protected Class<? extends AppCompatActivity> successActivity() { return ScanSuccessFrontActivity.class; }
    @Override protected String outputFileName() { return "scan_front.jpg"; }
}
