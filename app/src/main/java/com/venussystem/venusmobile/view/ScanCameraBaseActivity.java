package com.venussystem.venusmobile.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.venussystem.venusmobile.R;

import java.io.File;
import java.util.concurrent.ExecutionException;

public abstract class ScanCameraBaseActivity
        extends AppCompatActivity {

    protected static final String EXTRA_OUTPUT_KEY =
            "scan_output_path";

    protected PreviewView previewView;
    protected ImageCapture imageCapture;

    private ImageButton captureButton;

    private ProcessCameraProvider cameraProvider;

    private boolean cameraInicializando = false;

    private boolean capturando = false;

    private final ActivityResultLauncher<String>
            cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {

                        if (granted) {

                            startCamera();

                        } else {

                            Toast.makeText(
                                    this,
                                    "Permissão da câmera é necessária para o scan.",
                                    Toast.LENGTH_LONG
                            ).show();

                            if (captureButton != null) {
                                captureButton.setEnabled(false);
                            }
                        }
                    }
            );

    protected abstract int layoutResId();

    protected abstract Class<? extends AppCompatActivity>
    successActivity();

    protected abstract String outputFileName();

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        enableImmersiveCameraWindow();

        setContentView(
                layoutResId()
        );

        previewView =
                findViewById(
                        R.id.previewView
                );

        captureButton =
                findViewById(
                        R.id.btnScanCapture
                );

        if (captureButton != null) {
            captureButton.setEnabled(false);

            captureButton.setOnClickListener(
                    v -> takePhoto()
            );
        }

        View btnBack =
                findViewById(
                        R.id.btnScanBack
                );

        if (btnBack != null) {

            btnBack.setOnClickListener(
                    v -> finish()
            );
        }

        ensureCameraPermissionAndStart();
    }

    private void ensureCameraPermissionAndStart() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            startCamera();

        } else {

            cameraPermissionLauncher.launch(
                    Manifest.permission.CAMERA
            );
        }
    }

     protected void startCamera() {

        if (cameraInicializando) {
            return;
        }

        if (isFinishing()
                || isDestroyed()) {
            return;
        }

        if (previewView == null) {
            return;
        }

        cameraInicializando = true;

        if (captureButton != null) {
            captureButton.setEnabled(false);
        }

        final int targetRotation =
                getTargetRotation();

        final ListenableFuture<ProcessCameraProvider>
                future =
                ProcessCameraProvider.getInstance(
                        this
                );

        future.addListener(
                () -> {

                    try {

                        cameraProvider =
                                future.get();

                        CameraSelector selector =
                                CameraSelector.DEFAULT_BACK_CAMERA;

                        boolean cameraDisponivel;

                        try {

                            cameraDisponivel =
                                    cameraProvider.hasCamera(
                                            selector
                                    );

                        } catch (
                                CameraInfoUnavailableException e
                        ) {

                            cameraDisponivel =
                                    false;

                            android.util.Log.e(
                                    "VENUS_CAMERA",
                                    "Não foi possível consultar a câmera.",
                                    e
                            );

                            Toast.makeText(
                                    this,
                                    "Não foi possível verificar a câmera deste aparelho.",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        if (!cameraDisponivel) {

                            android.util.Log.e(
                                    "VENUS_CAMERA",
                                    "Câmera traseira não disponível."
                            );

                            Toast.makeText(
                                    this,
                                    "A câmera traseira não está disponível neste aparelho.",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        Preview preview =
                                new Preview.Builder()
                                        .setTargetResolution(
                                                new Size(
                                                        2160,
                                                        3840
                                                )
                                        )
                                        .setTargetRotation(
                                                targetRotation
                                        )
                                        .build();

                        imageCapture =
                                new ImageCapture.Builder()
                                        .setTargetResolution(
                                                new Size(
                                                        2160,
                                                        3840
                                                )
                                        )
                                        .setTargetRotation(
                                                targetRotation
                                        )
                                        .setCaptureMode(
                                                ImageCapture
                                                        .CAPTURE_MODE_MAXIMIZE_QUALITY
                                        )
                                        .setJpegQuality(
                                                95
                                        )
                                        .build();

                        preview.setSurfaceProvider(
                                previewView
                                        .getSurfaceProvider()
                        );

                        cameraProvider.unbindAll();

                        cameraProvider.bindToLifecycle(
                                this,
                                selector,
                                preview,
                                imageCapture
                        );

                        if (captureButton != null) {
                            captureButton.setEnabled(true);
                        }

                        android.util.Log.d(
                                "VENUS_CAMERA",
                                "Câmera inicializada com sucesso."
                        );

                    } catch (ExecutionException e) {

                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Erro ao obter ProcessCameraProvider.",
                                e
                        );

                        Toast.makeText(
                                this,
                                "Não foi possível iniciar a câmera.",
                                Toast.LENGTH_LONG
                        ).show();

                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();

                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Inicialização da câmera interrompida.",
                                e
                        );

                        Toast.makeText(
                                this,
                                "Inicialização da câmera interrompida.",
                                Toast.LENGTH_LONG
                        ).show();

                    } catch (IllegalArgumentException e) {

                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Configuração da câmera incompatível.",
                                e
                        );

                        Toast.makeText(
                                this,
                                "A configuração da câmera não é compatível com este aparelho.",
                                Toast.LENGTH_LONG
                        ).show();

                    } finally {

                        cameraInicializando = false;

                    }

                },
                ContextCompat.getMainExecutor(
                        this
                )
        );
    }

    protected void takePhoto() {

        if (capturando) {
            return;
        }

        if (imageCapture == null) {

            Toast.makeText(
                    this,
                    "Câmera ainda não está pronta.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        File outputDir =
                new File(
                        getCacheDir(),
                        "scan"
                );

        if (!outputDir.exists()
                && !outputDir.mkdirs()) {

            Toast.makeText(
                    this,
                    "Não foi possível preparar o arquivo da foto.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        File photoFile =
                new File(
                        outputDir,
                        outputFileName()
                );

        if (photoFile.exists()
                && !photoFile.delete()) {

            Toast.makeText(
                    this,
                    "Não foi possível preparar a nova foto.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        capturando = true;

        if (captureButton != null) {
            captureButton.setEnabled(false);
        }

        imageCapture.setTargetRotation(
                getTargetRotation()
        );

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(
                        photoFile
                ).build();

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(
                        this
                ),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            ImageCapture.OutputFileResults
                                    outputFileResults
                    ) {

                        capturando = false;

                        if (!photoFile.exists()
                                || photoFile.length() <= 0) {

                            if (captureButton != null) {
                                captureButton.setEnabled(
                                        true
                                );
                            }

                            Toast.makeText(
                                    ScanCameraBaseActivity.this,
                                    "A foto não foi gravada corretamente.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        android.util.Log.d(
                                "VENUS_CAMERA",
                                "Foto salva: "
                                        + photoFile
                                        .getAbsolutePath()
                        );

                        goToSuccess(
                                photoFile
                                        .getAbsolutePath()
                        );
                    }

                    @Override
                    public void onError(
                            ImageCaptureException exception
                    ) {

                        capturando = false;

                        if (captureButton != null) {
                            captureButton.setEnabled(
                                    true
                            );
                        }

                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Erro ao capturar foto.",
                                exception
                        );

                        Toast.makeText(
                                ScanCameraBaseActivity.this,
                                "Não foi possível capturar a foto.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private int getTargetRotation() {

        if (previewView != null
                && previewView.getDisplay() != null) {

            return previewView
                    .getDisplay()
                    .getRotation();
        }

        return Surface.ROTATION_0;
    }

    protected void goToSuccess(
            String path
    ) {

        Intent intent =
                new Intent(
                        this,
                        successActivity()
                );

        intent.putExtra(
                EXTRA_OUTPUT_KEY,
                path
        );

        startActivity(
                intent
        );

        finish();
    }

    public void onScanBackClicked(
            View ignored
    ) {

        finish();
    }

    @Override
    protected void onDestroy() {

        capturando = false;

        if (cameraProvider != null) {

            try {

                cameraProvider.unbindAll();

            } catch (Exception e) {

                android.util.Log.w(
                        "VENUS_CAMERA",
                        "Erro ao liberar CameraX.",
                        e
                );
            }
        }

        super.onDestroy();
    }

    private void enableImmersiveCameraWindow() {

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
    }
}