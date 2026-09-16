package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;

/** Segunda orientação, usada apenas quando o primeiro rótulo não foi reconhecido. */
public class ScanTutorial2Activity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();

        // Depois que o onboarding foi concluído por este usuário,
        // nenhum tutorial volta a ser exibido. O segundo passo é o scan do verso.
        if (ScanTutorialState.isCompleted(this)) {
            startActivity(new Intent(this, ScanCameraBackActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_scan_tutorial_2);
        findViewById(R.id.btnScanNext).setOnClickListener(v -> {
            startActivity(new Intent(this, ScanCameraBackActivity.class));
            finish();
        });
    }

    public void onScanBackClicked(View ignored) { finish(); }

    private void fullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
