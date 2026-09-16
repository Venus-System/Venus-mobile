package com.venussystem.venusmobile.view;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;

/** Terceira orientação, apresentada somente no primeiro fluxo sem match. */
public class ScanTutorial3Activity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();

        // O passo 3 pertence somente ao primeiro onboarding.
        // Depois de concluído, ele nunca deve voltar a ser exibido.
        if (ScanTutorialState.isCompleted(this)) {
            startActivity(new android.content.Intent(this, PrincipalActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return;
        }

        setContentView(R.layout.activity_scan_tutorial_3);
        findViewById(R.id.btnScanNext).setOnClickListener(v ->
                ScanFlowFinisher.finalizar(this));
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
