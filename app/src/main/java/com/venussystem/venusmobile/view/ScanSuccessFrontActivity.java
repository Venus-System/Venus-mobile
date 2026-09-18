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
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanProductMatch;
import com.venussystem.venusmobile.view.util.ScanFrontExtractor;
import com.venussystem.venusmobile.viewmodel.ScanViewModel;

import java.io.File;

/**
 * Tela exibida depois da captura da frente do produto.
 *
 * Fluxo:
 *
 * Foto
 * -> OCR
 * -> extração de marca/produto
 * -> identificação no catálogo
 * -> se encontrar, abre DetalheProdutoActivity automaticamente
 * -> se não encontrar, segue para o próximo fluxo do Scan.
 */
public class ScanSuccessFrontActivity extends AppCompatActivity {

    private static final String TAG_OCR = "VENUS_OCR";
    private static final String TAG_SCAN = "VENUS_SCAN";

    private String photoPath;

    private ScanViewModel scanViewModel;

    /**
     * Impede que o mesmo resultado do LiveData abra
     * a tela de detalhe mais de uma vez.
     */
    private boolean produtoAberto = false;

    /**
     * Impede processamento duplicado do mesmo resultado.
     */
    private boolean matchProcessado = false;

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
                findViewById(R.id.imgCapturedPhoto);

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

    /**
     * Observa OCR, loading, erro e resultado do match.
     */
    private void observarViewModel() {

        scanViewModel
                .getOcrResult()
                .observe(
                        this,
                        this::processarOcr
                );

        scanViewModel
                .getCarregando()
                .observe(
                        this,
                        carregando -> {

                            if (carregando == null) {
                                return;
                            }

                            View botao =
                                    findViewById(
                                            R.id.btnScanNext
                                    );

                            botao.setEnabled(
                                    !carregando
                            );
                        }
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
                                    TAG_OCR,
                                    "Erro no OCR",
                                    exception
                            );

                            findViewById(
                                    R.id.btnScanNext
                            ).setEnabled(true);
                        }
                );

        scanViewModel
                .getProductMatch()
                .observe(
                        this,
                        this::processarMatch
                );
    }

    /**
     * Inicia leitura OCR da foto capturada.
     */
    private void iniciarLeitura() {

        if (photoPath == null) {

            Log.e(
                    TAG_OCR,
                    "photoPath nulo"
            );

            findViewById(
                    R.id.btnScanNext
            ).setEnabled(true);

            return;
        }

        File photoFile =
                new File(photoPath);

        if (!photoFile.exists()) {

            Log.e(
                    TAG_OCR,
                    "Foto não existe: "
                            + photoPath
            );

            findViewById(
                    R.id.btnScanNext
            ).setEnabled(true);

            return;
        }

        findViewById(
                R.id.btnScanNext
        ).setEnabled(false);

        Log.d(
                TAG_OCR,
                "Iniciando OCR..."
        );

        Log.d(
                TAG_OCR,
                "Imagem: "
                        + photoPath
        );

        scanViewModel.reconhecer(
                Uri.fromFile(
                        photoFile
                )
        );
    }

    /**
     * Recebe o resultado do OCR e passa para o extractor.
     */
    private void processarOcr(
            ScanOcrResult result
    ) {

        if (result == null) {
            return;
        }

        Log.d(
                TAG_OCR,
                "=============================="
        );

        Log.d(
                TAG_OCR,
                "OCR CONCLUÍDO"
        );

        Log.d(
                TAG_OCR,
                "=============================="
        );

        Log.d(
                TAG_OCR,
                "TEXTO BRUTO:"
        );

        Log.d(
                TAG_OCR,
                result.getFullText()
        );

        Log.d(
                TAG_OCR,
                "LINHAS NORMALIZADAS:"
        );

        if (result.getLines() != null) {

            for (String linha :
                    result.getLines()) {

                Log.d(
                        TAG_OCR,
                        linha
                );
            }
        }

        ScanFrontData frontData =
                ScanFrontExtractor.extract(
                        result.getLines()
                );

        Log.d(
                TAG_OCR,
                "------------------------------"
        );

        Log.d(
                TAG_OCR,
                "CANDIDATOS DE MARCA: "
                        + frontData.getBrandCandidates()
        );

        Log.d(
                TAG_OCR,
                "CANDIDATOS DE PRODUTO: "
                        + frontData.getProductCandidates()
        );

        Log.d(
                TAG_OCR,
                "APRESENTAÇÕES: "
                        + frontData.getPresentationCandidates()
        );

        Log.d(
                TAG_OCR,
                "CAPACIDADE: "
                        + frontData.getCapacity()
        );

        Log.d(
                TAG_OCR,
                "CONCENTRAÇÃO: "
                        + frontData.getConcentration()
        );

        Log.d(
                TAG_OCR,
                "------------------------------"
        );

        /*
         * IMPORTANTE:
         *
         * Aqui começa o matching real.
         * O ViewModel/repository deve devolver o Produto
         * REAL do catálogo.
         */
        scanViewModel.identificarProduto(
                frontData
        );
    }

    /**
     * Processa resultado do matching.
     *
     * Quando o produto é encontrado, a tela de detalhe
     * é aberta automaticamente.
     */
    private void processarMatch(
            ScanProductMatch match
    ) {

        if (match == null) {
            return;
        }

        /*
         * Evita processar o mesmo resultado duas vezes.
         */
        if (matchProcessado) {
            return;
        }

        /*
         * Se houve erro no matcher, não abre produto.
         * O usuário continua podendo seguir pelo botão.
         */
        if (match.hasError()) {

            Log.e(
                    TAG_SCAN,
                    "Erro no matching: "
                            + match.getErrorMessage()
            );

            findViewById(
                    R.id.btnScanNext
            ).setEnabled(true);

            return;
        }

        /*
         * Nenhum produto encontrado.
         */
        if (!match.isFound()
                || match.getProduto() == null) {

            Log.d(
                    TAG_SCAN,
                    "NENHUM PRODUTO ENCONTRADO"
            );

            findViewById(
                    R.id.btnScanNext
            ).setEnabled(true);

            /*
             * Não marcamos matchProcessado aqui,
             * porque o botão ainda poderá continuar
             * o fluxo do Scan.
             */
            return;
        }

        Produto produto =
                match.getProduto();

        /*
         * O ID REAL do catálogo é obrigatório.
         */
        Long produtoId =
                produto.getId();

        if (produtoId == null
                || produtoId <= 0) {

            Log.e(
                    TAG_SCAN,
                    "Produto encontrado sem ID válido"
            );

            findViewById(
                    R.id.btnScanNext
            ).setEnabled(true);

            return;
        }

        matchProcessado = true;

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

        Log.d(
                TAG_SCAN,
                "OCR MARCA: "
                        + match.getMatchedBrand()
        );

        Log.d(
                TAG_SCAN,
                "OCR PRODUTO: "
                        + match.getMatchedProduct()
        );

        /*
         * ABRE AUTOMATICAMENTE.
         *
         * Não espera o usuário apertar o botão.
         */
        abrirProduto(produto);
    }

    /**
     * Usado pelo botão quando o usuário precisa continuar
     * depois de um caso sem match.
     */
    private void decidirDestino() {

        ScanProductMatch match =
                scanViewModel
                        .getProductMatch()
                        .getValue();

        if (match != null
                && match.isFound()
                && match.getProduto() != null) {

            abrirProduto(
                    match.getProduto()
            );

            return;
        }

        seguirSemMatch();
    }

    /**
     * Abre a DetalheProdutoActivity usando SOMENTE o ID
     * real do Produto encontrado no catálogo.
     */
    private void abrirProduto(
            @NonNull Produto produto
    ) {

        if (produtoAberto) {
            return;
        }

        Long produtoId =
                produto.getId();

        /*
         * Nunca abrir detalhe sem ID válido.
         */
        if (produtoId == null
                || produtoId <= 0) {

            Log.e(
                    TAG_SCAN,
                    "Tentativa de abrir produto sem ID: "
                            + produto.getName()
            );

            produtoAberto = false;

            seguirSemMatch();

            return;
        }

        produtoAberto = true;

        Log.d(
                TAG_SCAN,
                "ABRINDO DETALHE DO PRODUTO"
        );

        Log.d(
                TAG_SCAN,
                "ID ENVIADO: "
                        + produtoId
        );

        Log.d(
                TAG_SCAN,
                "NOME ENVIADO: "
                        + produto.getName()
        );

        Intent intent =
                new Intent(
                        ScanSuccessFrontActivity.this,
                        DetalheProdutoActivity.class
                );

        /*
         * Passamos exatamente o ID retornado
         * pelo catálogo/matcher.
         */
        intent.putExtra(
                DetalheProdutoActivity.EXTRA_ID,
                produtoId.longValue()
        );

        /*
         * Marca onboarding como concluído
         * somente porque houve identificação.
         */
        ScanTutorialState.markCompleted(
                this
        );

        startActivity(intent);

        /*
         * Não permite voltar para a tela intermediária
         * de sucesso da foto.
         */
        finish();
    }

    /**
     * Fluxo quando nenhum produto foi encontrado.
     */
    private void seguirSemMatch() {

        Class<?> destino =
                ScanTutorialState.isCompleted(
                        this
                )
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

    /**
     * Mantém a tela em fullscreen.
     */
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