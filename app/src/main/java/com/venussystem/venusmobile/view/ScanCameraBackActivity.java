package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;

/** Câmera do verso: foto usada posteriormente pelo fluxo de análise. */
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
        return "Vire a embalagem e fotografe o verso, principalmente a área de ingredientes ou composição.";
    }

    @Override
    protected String readyInstruction() {
        return "Verso do produto: enquadre o rótulo inteiro. Aproxime para deixar os ingredientes legíveis; afaste se cortar o texto.";
    }

    @Override
    protected String focusingInstruction() {
        return "Ajustando o foco nos ingredientes e no rótulo...";
    }

    @Override
    protected String focusedInstruction() {
        return "Foco ajustado. Verifique se os ingredientes estão legíveis e se o rótulo não foi cortado.";
    }

    @Override
    protected String capturedInstruction() {
        return "Foto registrada. Lendo ingredientes e informações do verso...";
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
            draft.putInto(intent);
        }

        startActivity(intent);
        finish();
    }
}
