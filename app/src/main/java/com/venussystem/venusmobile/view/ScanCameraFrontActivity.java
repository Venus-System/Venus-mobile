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
        return "Fotografe a frente do produto. A foto será usada para procurar o produto no nosso banco.";
    }

    @Override
    protected String readyInstruction() {
        return "Frente do produto: centralize o rótulo. Aproxime se o texto estiver pequeno; afaste se cortar as bordas.";
    }

    @Override
    protected String capturedInstruction() {
        return "Foto registrada. Buscando o produto no nosso banco...";
    }
}
