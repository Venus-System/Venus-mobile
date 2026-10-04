package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;

/**
 * Informa que o produto não foi encontrado no catálogo e conduz o usuário
 * para a captura do verso.
 *
 * Esta tela não executa OCR, matching ou classificação.
 */
public class ScanProductNotRegisteredActivity extends AppCompatActivity {

    private static final String TAG = "VENUS_SCAN";

    private ScanSubmissionDraft draft;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();
        setContentView(R.layout.activity_scan_product_not_registered);

        draft = ScanSubmissionDraft.fromIntent(getIntent());

        if (draft == null) {
            Log.e(
                    TAG,
                    "Draft do produto não cadastrado não chegou à Activity. "
                            + "Encerrando o scan."
            );
            fecharFluxoParaPrincipal();
            return;
        }

        View nextButton = findViewById(R.id.btnScanNext);
        if (nextButton != null) {
            nextButton.setOnClickListener(v -> continuar());
        }

        View backButton = findViewById(R.id.btnScanBack);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    private void continuar() {
        if (draft == null) {
            fecharFluxoParaPrincipal();
            return;
        }

        Intent intent;

        if (ScanTutorialState.isTutorial2Seen(this)) {
            intent = new Intent(
                    this,
                    ScanCameraBackActivity.class
            );
        } else {
            intent = new Intent(
                    this,
                    ScanTutorial2Activity.class
            );
        }

        draft.putInto(intent);

        try {
            startActivity(intent);
            finish();
        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Falha ao abrir a próxima etapa do scan. Encerrando o scan.",
                    exception
            );
            fecharFluxoParaPrincipal();
        }
    }

    private void fecharFluxoParaPrincipal() {
        try {
            Intent intent = new Intent(
                    this,
                    PrincipalActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(intent);
        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Falha ao retornar para PrincipalActivity.",
                    exception
            );
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
