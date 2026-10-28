package com.venussystem.venusmobile.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.domain.scan.ScanPhotoQualityPolicy;
import com.venussystem.venusmobile.model.ScanPhotoQuality;
import com.venussystem.venusmobile.view.util.ScanPhotoQualityAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class ScanCameraBaseActivity extends AppCompatActivity {

    protected static final String EXTRA_OUTPUT_KEY = "scan_output_path";

    protected PreviewView previewView;
    protected ImageCapture imageCapture;

    private ImageButton captureButton;
    private TextView instructionText;
    private Camera camera;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService captureQualityExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "venus-capture-quality");
                thread.setDaemon(true);
                return thread;
            });

    private boolean cameraInicializando = false;
    private boolean capturando = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startCamera();
                        } else {
                            setInstruction("Precisamos da câmera para escanear.");
                            Toast.makeText(
                                    this,
                                    "Permita a câmera para escanear.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    protected abstract int layoutResId();

    protected abstract Class<? extends AppCompatActivity> successActivity();

    protected abstract String outputFileName();

    protected abstract String initialInstruction();

    protected String readyInstruction() {
        return "Centralize o rótulo. Aproxime para ler; afaste se cortar.";
    }

    protected String focusingInstruction() {
        return "Ajustando o foco...";
    }

    protected String focusedInstruction() {
        return "Foco ajustado. Deixe o texto nítido e o rótulo inteiro.";
    }

    protected abstract String capturedInstruction();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enableImmersiveCameraWindow();
        setContentView(layoutResId());

        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.btnScanCapture);
        instructionText = findViewById(R.id.txtScanInstruction);

        setInstruction(initialInstruction());

        if (captureButton != null) {
            captureButton.setEnabled(false);
            captureButton.setOnClickListener(v -> takePhoto());
        }

        View backButton = findViewById(R.id.btnScanBack);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (previewView != null) {
            previewView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    focarNaPosicao(event.getX(), event.getY());
                }
                return true;
            });
        }

        ensureCameraPermissionAndStart();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        captureQualityExecutor.shutdownNow();

        if (camera != null) {
            try {
                camera.getCameraControl().cancelFocusAndMetering();
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }

    private void ensureCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    protected void startCamera() {
        if (cameraInicializando || isFinishing() || isDestroyed()) {
            return;
        }

        if (previewView == null) {
            return;
        }

        cameraInicializando = true;

        if (captureButton != null) {
            captureButton.setEnabled(false);
        }

        final int targetRotation = getTargetRotation();

        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(
                () -> {
                    try {
                        ProcessCameraProvider cameraProvider = future.get();

                        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

                        boolean cameraDisponivel;
                        try {
                            cameraDisponivel = cameraProvider.hasCamera(selector);
                        } catch (CameraInfoUnavailableException exception) {
                            cameraDisponivel = false;
                            android.util.Log.e(
                                    "VENUS_CAMERA",
                                    "Não foi possível consultar a câmera.",
                                    exception
                            );
                        }

                        if (!cameraDisponivel) {
                            setInstruction("Câmera traseira indisponível.");
                            Toast.makeText(
                                    this,
                                    "Câmera traseira indisponível.",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        Preview preview = new Preview.Builder()
                                .setTargetResolution(new Size(2160, 3840))
                                .setTargetRotation(targetRotation)
                                .build();

                        imageCapture = new ImageCapture.Builder()
                                .setTargetResolution(new Size(2160, 3840))
                                .setTargetRotation(targetRotation)
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .setJpegQuality(95)
                                .build();

                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        cameraProvider.unbindAll();

                        camera = cameraProvider.bindToLifecycle(
                                this,
                                selector,
                                preview,
                                imageCapture
                        );

                        instalarComportamentoPosCameraPronta();

                        android.util.Log.d(
                                "VENUS_CAMERA",
                                "Câmera inicializada com sucesso."
                        );

                    } catch (ExecutionException exception) {
                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Erro ao obter ProcessCameraProvider.",
                                exception
                        );
                        setInstruction("Não foi possível iniciar a câmera.");
                        Toast.makeText(
                                this,
                                "Não foi possível iniciar a câmera.",
                                Toast.LENGTH_LONG
                        ).show();

                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Inicialização da câmera interrompida.",
                                exception
                        );
                        setInstruction("A câmera foi interrompida.");
                        Toast.makeText(
                                this,
                                "Inicialização da câmera interrompida.",
                                Toast.LENGTH_LONG
                        ).show();

                    } catch (IllegalArgumentException exception) {
                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Configuração da câmera incompatível.",
                                exception
                        );
                        setInstruction("Câmera incompatível.");
                        Toast.makeText(
                                this,
                                "A câmera não é compatível.",
                                Toast.LENGTH_LONG
                        ).show();

                    } catch (RuntimeException exception) {
                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Falha inesperada ao configurar a câmera.",
                                exception
                        );
                        setInstruction("Não foi possível preparar a câmera.");
                        Toast.makeText(
                                this,
                                "Não foi possível preparar a câmera.",
                                Toast.LENGTH_LONG
                        ).show();

                    } finally {
                        cameraInicializando = false;
                    }
                },
                ContextCompat.getMainExecutor(this)
        );
    }

    private void instalarComportamentoPosCameraPronta() {
        setInstruction(readyInstruction());

        if (captureButton != null) {
            captureButton.setEnabled(true);
        }

        mainHandler.postDelayed(
                () -> {
                    if (!isFinishing() && !capturando) {
                        setInstruction(readyInstruction());
                    }
                },
                1800L
        );
    }

    private void focarNaPosicao(float x, float y) {
        if (camera == null || previewView == null || capturando) {
            return;
        }

        MeteringPoint point = previewView
                .getMeteringPointFactory()
                .createPoint(x, y);

        FocusMeteringAction action =
                new FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build();

        setInstruction(focusingInstruction());

        camera.getCameraControl()
                .startFocusAndMetering(action)
                .addListener(
                        () -> {
                            if (isFinishing() || isDestroyed() || capturando) {
                                return;
                            }

                            try {
                                setInstruction(focusedInstruction());
                            } catch (Exception ignored) {
                            }
                        },
                        ContextCompat.getMainExecutor(this)
                );
    }

    protected void takePhoto() {
        if (capturando) {
            return;
        }

        if (imageCapture == null) {
            Toast.makeText(
                    this,
                    "Preparando a câmera...",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        File outputDir = new File(getCacheDir(), "scan");

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Toast.makeText(
                    this,
                    "Não foi possível preparar a foto.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        File photoFile = new File(outputDir, outputFileName());

        if (photoFile.exists() && !photoFile.delete()) {
            Toast.makeText(
                    this,
                    "Não foi possível preparar a foto.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        capturando = true;

        if (captureButton != null) {
            captureButton.setEnabled(false);
        }

        setInstruction(capturedInstruction());

        imageCapture.setTargetRotation(getTargetRotation());

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        if (!photoFile.exists() || photoFile.length() <= 0) {
                            capturando = false;
                            if (captureButton != null) {
                                captureButton.setEnabled(true);
                            }

                            setInstruction(readyInstruction());
                            Toast.makeText(
                                    ScanCameraBaseActivity.this,
                                    "A foto não foi salva.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (captureQualityExecutor.isShutdown()) {
                            return;
                        }
                        try {
                            captureQualityExecutor.execute(
                                    () -> validateCapturedPhoto(photoFile)
                            );
                        } catch (RuntimeException ignored) {
                        }
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        capturando = false;

                        if (captureButton != null) {
                            captureButton.setEnabled(true);
                        }

                        setInstruction(readyInstruction());

                        android.util.Log.e(
                                "VENUS_CAMERA",
                                "Erro ao capturar a foto.",
                                exception
                        );

                        Toast.makeText(
                                ScanCameraBaseActivity.this,
                                "Não foi possível salvar a foto.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void validateCapturedPhoto(File photoFile) {
        ScanPhotoQuality quality = null;
        String failureMessage = null;
        try {
            quality = ScanPhotoQualityAnalyzer.analyze(photoFile.getAbsolutePath());
            if (ScanPhotoQualityPolicy.shouldBlock(quality)) {
                failureMessage = captureQualityMessage(
                        ScanPhotoQualityPolicy.blockReason(quality)
                );
            }
        } catch (IOException exception) {
            failureMessage = captureQualityMessage(exception.getMessage());
            android.util.Log.w("VENUS_CAMERA", "QUALITY_CHECK_FAILED");
        } catch (RuntimeException exception) {
            failureMessage = "Não foi possível verificar a foto. Tente novamente.";
            android.util.Log.w("VENUS_CAMERA", "QUALITY_CHECK_FAILED");
        }

        final ScanPhotoQuality measuredQuality = quality;
        final String message = failureMessage;
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            capturando = false;
            if (message != null) {
                if (photoFile.exists() && !photoFile.delete()) {
                    android.util.Log.w("VENUS_CAMERA", "QUALITY_REJECT_DELETE_FAILED");
                }
                if (captureButton != null) {
                    captureButton.setEnabled(true);
                }
                setInstruction(message);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                return;
            }

            if (captureButton != null) {
                captureButton.setEnabled(true);
            }
            if (measuredQuality != null
                    && ScanPhotoQualityPolicy.backgroundIsAdvisory(measuredQuality)) {
                setInstruction("Rótulo legível. Evite sombras e reflexos se puder.");
            }

            mainHandler.postDelayed(
                    () -> {
                        if (!isFinishing() && !isDestroyed()) {
                            goToSuccess(photoFile.getAbsolutePath());
                        }
                    },
                    180L
            );
        });
    }

    private String captureQualityMessage(String reason) {
        if ("BLUR_TOO_HIGH".equals(reason)) {
            return "Foto desfocada. Segure firme e toque para focar.";
        }
        if ("TOO_DARK".equals(reason)) {
            return "Foto escura. Use mais luz e evite sombras.";
        }
        if ("TOO_BRIGHT".equals(reason)) {
            return "Reflexo forte. Mude o ângulo e tente novamente.";
        }
        if ("RESOLUTION_TOO_LOW".equals(reason)) {
            return "A foto ficou pequena. Aproxime o rótulo e tente novamente.";
        }
        return "Não foi possível verificar a foto. Tente novamente.";
    }

    private void setInstruction(String message) {
        if (instructionText == null || message == null) {
            return;
        }

        instructionText.setText(message);
    }

    private int getTargetRotation() {
        if (previewView != null && previewView.getDisplay() != null) {
            return previewView.getDisplay().getRotation();
        }

        return Surface.ROTATION_0;
    }

    protected void goToSuccess(String path) {
        Intent intent = new Intent(this, successActivity());
        intent.putExtra(EXTRA_OUTPUT_KEY, path);

        ScanSubmissionDraft draft =
                ScanSubmissionDraft.fromIntent(getIntent());

        if (draft != null) {
            draft.putInto(intent);
        }

        android.util.Log.d(
                "VENUS_CAMERA",
                "Abrindo successActivity="
                        + successActivity().getSimpleName()
                        + " draftPresente="
                        + (draft != null)
        );

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
