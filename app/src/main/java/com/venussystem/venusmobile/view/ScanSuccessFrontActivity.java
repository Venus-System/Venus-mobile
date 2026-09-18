package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanProductMatch;
import com.venussystem.venusmobile.model.ScanStatus;
import com.venussystem.venusmobile.viewmodel.ScanViewModel;

import java.io.File;

public class ScanSuccessFrontActivity extends AppCompatActivity {

    private static final String TAG_OCR = "VENUS_OCR";
    private static final String TAG_SCAN = "VENUS_SCAN";

    private String photoPath;
    private ScanViewModel scanViewModel;

    private boolean produtoAberto = false;

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        fullscreen();
        setContentView(
                R.layout.activity_scan_success_front
        );

        ImageView photo =
                findViewById(
                        R.id.imgCapturedPhoto
                );

        photoPath =
                getIntent().getStringExtra(
                        ScanCameraBaseActivity.EXTRA_OUTPUT_KEY
                );

        if (photoPath != null) {

            File file =
                    new File(photoPath);

            if (file.exists()) {
                photo.setImageURI(
                        Uri.fromFile(file)
                );
            }
        }

        scanViewModel =
                new ViewModelProvider(this)
                        .get(ScanViewModel.class);

        observarViewModel();

        findViewById(
                R.id.btnScanNext
        ).setOnClickListener(
                v -> decidirDestino()
        );

        iniciarLeitura();
    }

    private void observarViewModel() {

        scanViewModel
                .getOcrResult()
                .observe(
                        this,
                        this::registrarOcr
                );

        scanViewModel
                .getStatus()
                .observe(
                        this,
                        this::atualizarEstadoInterface
                );

        scanViewModel
                .getProductMatch()
                .observe(
                        this,
                        this::processarMatch
                );

        scanViewModel
                .getErro()
                .observe(
                        this,
                        exception -> {

                            if (exception == null) {
                                return;
                            }

                            Log.e(
                                    TAG_SCAN,
                                    "ERRO NO SCAN",
                                    exception
                            );
                        }
                );
    }

    private void registrarOcr(
            ScanOcrResult result
    ) {

        if (result == null) {
            return;
        }

        Log.d(
                TAG_OCR,
                "OCR RECEBIDO PELA ACTIVITY"
        );

        Log.d(
                TAG_OCR,
                result.getFullText()
        );
    }

    private void atualizarEstadoInterface(
            ScanStatus status
    ) {

        if (status == null) {
            return;
        }

        View botao =
                findViewById(
                        R.id.btnScanNext
                );

        boolean podeContinuar =
                status == ScanStatus.PRODUTO_NAO_ENCONTRADO
                        || status == ScanStatus.ERRO;

        botao.setEnabled(
                podeContinuar
        );
    }

    private void iniciarLeitura() {

        if (photoPath == null
                || photoPath.trim().isEmpty()) {

            Log.e(
                    TAG_OCR,
                    "photoPath nulo ou vazio"
            );

            findViewById(
                    R.id.btnScanNext
            ).setEnabled(true);

            return;
        }

        File photoFile =
                new File(photoPath);

        if (!photoFile.exists()
                || !photoFile.isFile()
                || photoFile.length() <= 0) {

            Log.e(
                    TAG_OCR,
                    "Foto inválida: "
                            + photoPath
            );

            findViewById(
                    R.id.btnScanNext
            ).setEnabled(true);

            return;
        }

        Log.d(
                TAG_OCR,
                "INICIANDO PROCESSAMENTO DA FOTO"
        );

        scanViewModel.processarFrente(
                Uri.fromFile(photoFile)
        );
    }

    private void processarMatch(
            ScanProductMatch match
    ) {

        if (match == null) {
            return;
        }

        if (match.hasError()) {

            Log.e(
                    TAG_SCAN,
                    "Erro no matching: "
                            + match.getErrorMessage()
            );

            return;
        }

        if (!match.isFound()
                || match.getProduto() == null) {

            Log.d(
                    TAG_SCAN,
                    "NENHUM PRODUTO ENCONTRADO"
            );

            return;
        }

        abrirProduto(
                match.getProduto(),
                match
        );
    }

    private void decidirDestino() {

        ScanStatus status =
                scanViewModel
                        .getStatus()
                        .getValue();

        if (status == ScanStatus.PROCESSANDO_OCR
                || status == ScanStatus.IDENTIFICANDO_PRODUTO) {
            return;
        }

        ScanProductMatch match =
                scanViewModel
                        .getProductMatch()
                        .getValue();

        if (match != null
                && match.isFound()
                && match.getProduto() != null) {

            abrirProduto(
                    match.getProduto(),
                    match
            );

            return;
        }

        seguirSemMatch();
    }

    private void abrirProduto(
            @NonNull Produto produto,
            @NonNull ScanProductMatch match
    ) {

        if (produtoAberto) {
            return;
        }

        Long produtoId =
                produto.getId();

        if (produtoId == null
                || produtoId <= 0) {

            Log.e(
                    TAG_SCAN,
                    "Produto encontrado sem ID válido: "
                            + produto.getName()
            );

            return;
        }

        produtoAberto = true;

        Log.d(
                TAG_SCAN,
                "=============================="
        );

        Log.d(
                TAG_SCAN,
                "PRODUTO ENCONTRADO"
        );

        Log.d(
                TAG_SCAN,
                "ID: "
                        + produtoId
        );

        Log.d(
                TAG_SCAN,
                "NOME: "
                        + produto.getName()
        );

        Log.d(
                TAG_SCAN,
                "MARCA: "
                        + produto.getBrandName()
        );

        Log.d(
                TAG_SCAN,
                "SCORE: "
                        + match.getScore()
        );

        Intent intent =
                new Intent(
                        this,
                        DetalheProdutoActivity.class
                );

        intent.putExtra(
                DetalheProdutoActivity.EXTRA_ID,
                produtoId.longValue()
        );

        ScanTutorialState.markCompleted(
                this
        );

        startActivity(intent);
        finish();
    }

    private void seguirSemMatch() {

        Class<?> destino =
                ScanTutorialState.isCompleted(this)
                        ? ScanCameraBackActivity.class
                        : ScanTutorial2Activity.class;

        startActivity(
                new Intent(
                        this,
                        destino
                )
        );

        finish();
    }

    public void onScanBackClicked(
            View ignored
    ) {
        finish();
    }

    private void fullscreen() {

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
