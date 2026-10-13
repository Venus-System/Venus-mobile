package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;

/**
 * Passo 1: explica que a primeira foto serve para procurar o produto no banco.
 */
public class ScanTutorial1Activity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();

        if (ScanTutorialState.isTutorial1Seen(this)) {
            abrirCameraFront();
            return;
        }

        // A tela foi efetivamente exibida ao usuário: registra como vista.
        ScanTutorialState.markTutorial1Seen(this);

        setContentView(R.layout.activity_scan_tutorial_1);

        findViewById(R.id.btnScanNext).setOnClickListener(
                v -> abrirCameraFront()
        );

        findViewById(R.id.btnScanBack).setOnClickListener(
                v -> finish()
        );
    }

    private void abrirCameraFront() {
        startActivity(new Intent(this, ScanCameraFrontActivity.class));
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
