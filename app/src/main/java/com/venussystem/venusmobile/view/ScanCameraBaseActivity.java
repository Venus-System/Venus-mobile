package com.venussystem.venusmobile.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

/**
 * Base reutilizável das duas telas de câmera.
 * Cada lado do produto possui sua própria Activity, mas compartilha a mesma
 * configuração CameraX e a mesma regra de foto vertical 9:16.
 */
public abstract class ScanCameraBaseActivity extends AppCompatActivity {

    protected static final String EXTRA_OUTPUT_KEY = "scan_output_path";

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(
                            this,
                            "Permissão da câmera é necessária para o scan.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

    protected PreviewView previewView;
    protected ImageCapture imageCapture;

    protected abstract int layoutResId();
    protected abstract Class<? extends AppCompatActivity> successActivity();
    protected abstract String outputFileName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveCameraWindow();
        setContentView(layoutResId());

        previewView = findViewById(R.id.previewView);
        findViewById(R.id.btnScanCapture).setOnClickListener(v -> takePhoto());
        findViewById(R.id.btnScanBack).setOnClickListener(v -> finish());

        ensureCameraPermissionAndStart();
    }

    private void ensureCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    protected void startCamera() {
        final int targetRotation = getTargetRotation();

        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                // Mantemos a prévia e a foto em 9:16 vertical. CameraX faz o
                // fallback automático para a resolução disponível mais próxima
                // caso o aparelho não ofereça 2160x3840.
                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(2160, 3840))
                        .setTargetRotation(targetRotation)
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setTargetResolution(new Size(2160, 3840))
                        .setTargetRotation(targetRotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(100)
                        .build();

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(
                        this,
                        "Não foi possível iniciar a câmera.",
                        Toast.LENGTH_LONG
                ).show();
            } catch (IllegalArgumentException e) {
                Toast.makeText(
                        this,
                        "A configuração da câmera não é compatível com este aparelho.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    protected void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "Câmera ainda não está pronta.", Toast.LENGTH_SHORT).show();
            return;
        }

        File outputDir = new File(getCacheDir(), "scan");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Toast.makeText(
                    this,
                    "Não foi possível preparar o arquivo da foto.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        File photoFile = new File(outputDir, outputFileName());
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // Mantém a orientação de captura sincronizada com a tela portrait.
        imageCapture.setTargetRotation(getTargetRotation());
        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        goToSuccess(photoFile.getAbsolutePath());
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
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
        if (previewView.getDisplay() != null) {
            return previewView.getDisplay().getRotation();
        }
        return Surface.ROTATION_0;
    }

    protected void goToSuccess(String path) {
        Intent intent = new Intent(this, successActivity());
        intent.putExtra(EXTRA_OUTPUT_KEY, path);
        startActivity(intent);
        finish();
    }

    public void onScanBackClicked(View ignored) {
        finish();
    }

    private void enableImmersiveCameraWindow() {
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
