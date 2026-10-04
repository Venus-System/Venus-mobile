package com.venussystem.venusmobile.view;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;

/**
 * Passo 3: explica que os dados do novo produto serão preparados para análise.
 */
public class ScanTutorial3Activity extends AppCompatActivity {

    private static final String TAG = "VENUS_SCAN";
    private boolean finalizando = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();

        final ScanSubmissionDraft draft =
                ScanSubmissionDraft.fromIntent(getIntent());

        if (draft == null) {
            Log.e(TAG, "Tutorial 3 recebeu draft nulo. Encerrando o scan.");
            fecharFluxoParaPrincipal();
            return;
        }

        if (ScanTutorialState.isTutorial3Seen(this)) {
            ScanFlowFinisher.finalizar(this, draft);
            return;
        }

        setContentView(R.layout.activity_scan_tutorial_3);

        // Marca como visto somente depois que a tela foi realmente montada.
        ScanTutorialState.markTutorial3Seen(this);

        findViewById(R.id.btnScanNext).setOnClickListener(
                v -> finalizar(draft)
        );

        findViewById(R.id.btnScanBack).setOnClickListener(
                v -> finish()
        );
    }

    private void fecharFluxoParaPrincipal() {
        try {
            android.content.Intent intent =
                    new android.content.Intent(this, PrincipalActivity.class);
            intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG, "Falha ao retornar para PrincipalActivity.", exception);
        } finally {
            finish();
        }
    }

    private void finalizar(ScanSubmissionDraft draft) {
        if (finalizando) {
            return;
        }

        finalizando = true;
        ScanFlowFinisher.finalizar(this, draft);
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
