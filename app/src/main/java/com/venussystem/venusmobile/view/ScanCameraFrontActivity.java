package com.venussystem.venusmobile.view;

import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;

/** Câmera da frente: foto usada para procurar o produto no catálogo. */
public class ScanCameraFrontActivity extends ScanCameraBaseActivity {

    @Override
    protected int layoutResId() {
        return R.layout.activity_scan_camera_front;
    }

    @Override
    protected Class<? extends AppCompatActivity> successActivity() {
        return ScanSuccessFrontActivity.class;
    }

    @Override
    protected String outputFileName() {
        return "scan_front.jpg";
    }

    @Override
    protected String initialInstruction() {
        return "Fotografe a frente do produto.";
    }

    @Override
    protected String readyInstruction() {
        return "Centralize o rótulo. Aproxime para ler; afaste se cortar.";
    }

    @Override
    protected String capturedInstruction() {
        return "Foto salva. Buscando o produto...";
    }
}
