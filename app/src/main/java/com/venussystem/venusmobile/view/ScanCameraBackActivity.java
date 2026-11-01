package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;

public class ScanCameraBackActivity extends ScanCameraBaseActivity {

    static final String EXTRA_BACK_RETAKE_MESSAGE = "scan_back_retake_message";

    private ScanSubmissionDraft draft;
    private String retakeMessage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        retakeMessage = getIntent().getStringExtra(EXTRA_BACK_RETAKE_MESSAGE);
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
        return hasRetakeMessage()
                ? retakeMessage
                : "Fotografe o verso, principalmente os ingredientes.";
    }

    @Override
    protected String readyInstruction() {
        if (hasRetakeMessage()) {
            String message = retakeMessage;
            retakeMessage = null;
            return message;
        }
        return "Enquadre o verso. Deixe os ingredientes legíveis.";
    }

    private boolean hasRetakeMessage() {
        return retakeMessage != null && !retakeMessage.trim().isEmpty();
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
