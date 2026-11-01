package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;

public class ScanCameraBackActivity extends ScanCameraBaseActivity {

    private ScanSubmissionDraft draft;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        draft = ScanSubmissionDraft.fromIntent(getIntent());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int layoutResId() {
        return R.layout.activity_scan_camera_back;
    }

    @Override
    protected Class<? extends AppCompatActivity> successActivity() {
        return ScanSuccessBackActivity.class;
    }

    @Override
    protected String outputFileName() {
        return "scan_back.jpg";
    }

    @Override
    protected String initialInstruction() {
        return "Fotografe o verso, principalmente os ingredientes.";
    }

    @Override
    protected String readyInstruction() {
        return "Enquadre o verso. Deixe os ingredientes legíveis.";
    }

    @Override
    protected String focusingInstruction() {
        return "Ajustando o foco nos ingredientes...";
    }

    @Override
    protected String focusedInstruction() {
        return "Foco ajustado. Confira os ingredientes e o rótulo.";
    }

    @Override
    protected String capturedInstruction() {
        return "Foto salva. Lendo os ingredientes...";
    }

    @Override
    protected boolean allowQualityReviewAfterRetake() {
        // The front draft is preserved, but the back must be readable before
        // the OCR/submission pipeline can continue.
        return false;
    }

    @Override
    protected void goToSuccess(String path) {
        Intent intent = new Intent(
                this,
                ScanSuccessBackActivity.class
        );

        intent.putExtra(
                EXTRA_OUTPUT_KEY,
                path
        );

        if (draft != null) {
            draft.setBackPhotoPath(path);
            draft.setQualityReviewOverride(
                    draft.isQualityReviewOverride()
                            || getIntent().getBooleanExtra(EXTRA_QUALITY_REVIEW_OVERRIDE, false));
            draft.putInto(intent);
        }

        startActivity(intent);
        finish();
    }
}
