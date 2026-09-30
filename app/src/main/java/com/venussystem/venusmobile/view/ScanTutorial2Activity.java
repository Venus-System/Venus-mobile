package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;

/**
 * Passo 2: explica que a foto do verso deve priorizar ingredientes/composição.
 */
public class ScanTutorial2Activity extends AppCompatActivity {

    private static final String TAG = "VENUS_SCAN";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();

        final ScanSubmissionDraft draft =
                ScanSubmissionDraft.fromIntent(getIntent());

        if (draft == null) {
            Log.e(TAG, "Tutorial 2 recebeu draft nulo. Encerrando o scan.");
            fecharFluxoParaPrincipal();
            return;
        }

        if (ScanTutorialState.isTutorial2Seen(this)) {
            abrirCameraBack(draft);
            return;
        }

        setContentView(R.layout.activity_scan_tutorial_2);
        ScanTutorialState.markTutorial2Seen(this);

        findViewById(R.id.btnScanNext).setOnClickListener(
                v -> abrirCameraBack(draft)
        );

        findViewById(R.id.btnScanBack).setOnClickListener(
                v -> finish()
        );
    }

    private void abrirCameraBack(ScanSubmissionDraft draft) {
        Intent intent = new Intent(this, ScanCameraBackActivity.class);
        draft.putInto(intent);

        try {
            startActivity(intent);
            finish();
        } catch (Exception exception) {
            Log.e(TAG, "Falha ao abrir câmera traseira. Encerrando o scan.", exception);
            fecharFluxoParaPrincipal();
        }
    }

    private void fecharFluxoParaPrincipal() {
        try {
            Intent intent = new Intent(this, PrincipalActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Falha ao retornar para PrincipalActivity.", exception);
        } finally {
            finish();
        }
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
