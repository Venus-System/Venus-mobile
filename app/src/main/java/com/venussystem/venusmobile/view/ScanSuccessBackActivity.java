package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;

import java.io.File;

public class ScanSuccessBackActivity
        extends AppCompatActivity {

    private String photoPath;
    private boolean finalizando = false;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        fullscreen();
        setContentView(
                R.layout.activity_scan_success_back
        );

        ImageView photo =
                findViewById(
                        R.id.imgCapturedPhoto
                );

        photoPath =
                getIntent().getStringExtra(
                        ScanCameraBaseActivity.EXTRA_OUTPUT_KEY
                );

        if (photoPath != null) {

            File file =
                    new File(photoPath);

            if (file.exists()
                    && file.length() > 0) {

                photo.setImageURI(
                        Uri.fromFile(file)
                );
            }
        }

        findViewById(
                R.id.btnScanNext
        ).setOnClickListener(
                v -> seguir()
        );
    }

    private void seguir() {

        if (finalizando) {
            return;
        }

        finalizando = true;

        findViewById(
                R.id.btnScanNext
        ).setEnabled(false);

        if (ScanTutorialState.isCompleted(this)) {

            ScanFlowFinisher.finalizar(this);
            return;
        }

        startActivity(
                new Intent(
                        this,
                        ScanTutorial3Activity.class
                )
        );

        finish();
    }

    public void onScanBackClicked(
            View ignored
    ) {
        finish();
    }

    private void fullscreen() {

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
    }
}
