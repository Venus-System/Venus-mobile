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

/**
 * Mostra a foto real da frente e representa o ponto de decisão do primeiro scan.
 *
 * O OCR/READ será conectado posteriormente ao ScanProductResolver.
 * Se não houver match, o fluxo segue para Tutorial 2 (primeiro onboarding)
 * ou diretamente para a captura do verso (demais utilizações).
 */
public class ScanSuccessFrontActivity extends AppCompatActivity {

    private String photoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();
        setContentView(R.layout.activity_scan_success_front);

        ImageView photo = findViewById(R.id.imgCapturedPhoto);
        photoPath = getIntent().getStringExtra(
                ScanCameraBaseActivity.EXTRA_OUTPUT_KEY
        );

        if (photoPath != null) {
            File file = new File(photoPath);
            if (file.exists()) {
                photo.setImageURI(Uri.fromFile(file));
            }
        }

        findViewById(R.id.btnScanNext).setOnClickListener(v -> decidirDestino());
    }

    private void decidirDestino() {
        if (photoPath == null || !new File(photoPath).exists()) {
            seguirSemMatch();
            return;
        }

        findViewById(R.id.btnScanNext).setEnabled(false);

        ScanProductResolver.resolver(
                new File(photoPath),
                productId -> {
                    if (productId != null && productId > 0) {
                        Intent intent = new Intent(
                                this,
                                DetalheProdutoActivity.class
                        );
                        intent.putExtra(
                                DetalheProdutoActivity.EXTRA_ID,
                                productId
                        );

                        // Se o produto foi identificado no primeiro scan,
                        // os tutoriais não precisam mais ser exibidos.
                        ScanTutorialState.markCompleted(this);

                        startActivity(intent);
                        finish();
                    } else {
                        seguirSemMatch();
                    }
                }
        );
    }

    private void seguirSemMatch() {
        Class<?> destino = ScanTutorialState.isCompleted(this)
                ? ScanCameraBackActivity.class
                : ScanTutorial2Activity.class;

        startActivity(new Intent(this, destino));
        finish();
    }

    public void onScanBackClicked(View ignored) {
        finish();
    }

    private void fullscreen() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }
}
