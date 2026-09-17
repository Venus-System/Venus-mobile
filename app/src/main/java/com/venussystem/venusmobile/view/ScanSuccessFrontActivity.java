package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.view.util.ScanFrontExtractor;
import com.venussystem.venusmobile.viewmodel.ScanViewModel;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.util.List;

/**
 * Exibe a foto real capturada da FRENTE do produto.
 *
 * Nesta etapa, o objetivo do OCR do FRONT é identificar
 * somente informações relevantes da face frontal:
 *
 *      - marca
 *      - nome do produto
 *      - capacidade / volume
 *
 * Fluxo atual:
 *
 *      CameraX FRONT
 *          ↓
 *      foto real
 *          ↓
 *      ML Kit OCR
 *          ↓
 *      linhas reconhecidas
 *          ↓
 *      análise do FRONT
 *          ↓
 *      marca / produto / capacidade
 *
 * O OCR do BACK ainda NÃO é tratado aqui.
 *
 * Ingredientes, composição, precauções, fabricante,
 * lote e demais dados serão tratados posteriormente
 * no fluxo da câmera BACK.
 *
 * Também não fazemos ainda:
 *
 *      - chamada à API
 *      - matching com banco
 *      - fuzzy matching
 *      - identificação definitiva do produto
 */
public class ScanSuccessFrontActivity extends AppCompatActivity {

    private static final String TAG_OCR = "VENUS_OCR";

    private String photoPath;

    private ScanViewModel scanViewModel;

    private View btnNext;

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

        btnNext =
                findViewById(
                        R.id.btnScanNext
                );

        /*
         * Recupera a fotografia produzida pela CameraX.
         */
        photoPath =
                getIntent().getStringExtra(
                        ScanCameraBaseActivity.EXTRA_OUTPUT_KEY
                );

        carregarFoto(photo);

        /*
         * ViewModel responsável pelo OCR.
         */
        scanViewModel =
                new ViewModelProvider(this)
                        .get(ScanViewModel.class);

        /*
         * Observa os resultados antes de iniciar o OCR.
         */
        observarOcr();

        /*
         * O OCR é executado automaticamente
         * quando a foto válida chega nesta tela.
         */
        executarOcr();

        /*
         * Mantém o botão do fluxo atual.
         *
         * A identificação definitiva do produto ainda
         * não foi implementada nesta etapa.
         */
        btnNext.setOnClickListener(
                v -> seguirFluxo()
        );
    }

    /**
     * Exibe a fotografia REAL tirada pela CameraX.
     */
    private void carregarFoto(
            @Nullable ImageView photoView
    ) {

        if (photoView == null) {
            return;
        }

        if (photoPath == null
                || photoPath.trim().isEmpty()) {

            Log.w(
                    TAG_OCR,
                    "Caminho da foto não recebido."
            );

            return;
        }

        File file =
                new File(photoPath);

        if (!file.exists()) {

            Log.w(
                    TAG_OCR,
                    "Arquivo da foto não existe: "
                            + photoPath
            );

            return;
        }

        photoView.setImageURI(
                Uri.fromFile(file)
        );
    }

    /**
     * Inicia o OCR da fotografia frontal.
     */
    private void executarOcr() {

        if (!fotoValida()) {

            Log.w(
                    TAG_OCR,
                    "OCR não executado: foto inválida."
            );

            return;
        }

        Log.d(
                TAG_OCR,
                "Iniciando OCR FRONT..."
        );

        Log.d(
                TAG_OCR,
                "Imagem: " + photoPath
        );

        setBotaoHabilitado(false);

        scanViewModel.reconhecer(
                Uri.fromFile(
                        new File(photoPath)
                )
        );
    }

    /**
     * Observa o processamento realizado pelo ViewModel.
     */
    private void observarOcr() {

        scanViewModel
                .getOcrResult()
                .observe(
                        this,
                        result -> {

                            if (result == null) {
                                return;
                            }

                            registrarResultadoOcr(
                                    result
                            );

                            setBotaoHabilitado(
                                    true
                            );
                        }
                );

        scanViewModel
                .getCarregando()
                .observe(
                        this,
                        carregando -> {

                            if (carregando == null) {
                                return;
                            }

                            setBotaoHabilitado(
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
                                    "Erro durante o OCR FRONT.",
                                    exception
                            );

                            setBotaoHabilitado(
                                    true
                            );

                            Toast.makeText(
                                    this,
                                    "Não foi possível ler o texto da foto.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );
    }

    /**
     * Registra o OCR bruto e extrai informações
     * específicas da parte FRONT.
     *
     * Neste momento não fazemos consulta à API.
     */
    private void registrarResultadoOcr(
            @NonNull ScanOcrResult result
    ) {

        Log.d(
                TAG_OCR,
                "=============================="
        );

        Log.d(
                TAG_OCR,
                "OCR FRONT CONCLUÍDO"
        );

        Log.d(
                TAG_OCR,
                "=============================="
        );

        /*
         * Texto completo reconhecido pelo ML Kit.
         */
        Log.d(
                TAG_OCR,
                "TEXTO BRUTO:"
        );

        Log.d(
                TAG_OCR,
                result.getFullText()
        );

        /*
         * Linhas preservadas pelo ViewModel.
         */
        Log.d(
                TAG_OCR,
                "------------------------------"
        );

        Log.d(
                TAG_OCR,
                "LINHAS OCR:"
        );

        for (
                String linha
                : result.getLines()
        ) {

            Log.d(
                    TAG_OCR,
                    linha
            );
        }

        /*
         * Analisa somente o conteúdo que poderá
         * representar dados da frente.
         */
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
                "DADOS EXTRAÍDOS DO FRONT"
        );

        Log.d(
                TAG_OCR,
                "CANDIDATOS DE MARCA:"
        );

        for (
                String marca
                : frontData.getBrandCandidates()
        ) {

            Log.d(
                    TAG_OCR,
                    marca
            );
        }

        Log.d(
                TAG_OCR,
                "CANDIDATOS DE PRODUTO:"
        );

        for (
                String produto
                : frontData.getProductCandidates()
        ) {

            Log.d(
                    TAG_OCR,
                    produto
            );
        }

        Log.d(
                TAG_OCR,
                "APRESENTAÇÕES:"
        );

        for (
                String apresentacao
                : frontData.getPresentationCandidates()
        ) {

            Log.d(
                    TAG_OCR,
                    apresentacao
            );
        }

        Log.d(
                TAG_OCR,
                "CAPACIDADE: "
                        + frontData.getCapacity()
        );

        Log.d(
                TAG_OCR,
                "CONCENTRACAO: "
                        + frontData.getConcentration()
        );

        Log.d(
                TAG_OCR,
                "=============================="
        );
    }

    /**
     * Verifica se a fotografia existe.
     */
    private boolean fotoValida() {

        if (photoPath == null) {
            return false;
        }

        if (photoPath.trim().isEmpty()) {
            return false;
        }

        return new File(photoPath).exists();
    }

    /**
     * Mantém a navegação atual.
     *
     * IMPORTANTE:
     *
     * Ainda não usamos os dados extraídos do FRONT
     * para buscar o produto no banco.
     *
     * Isso será feito na próxima etapa.
     */
    private void seguirFluxo() {

        if (!fotoValida()) {

            seguirSemMatch();

            return;
        }

        /*
         * Por enquanto mantemos o resolver existente.
         *
         * Ele continua sendo o mock da identificação.
         */
        ScanProductResolver.resolver(
                new File(photoPath),
                productId -> {

                    if (productId != null
                            && productId > 0) {

                        abrirProduto(
                                productId
                        );

                    } else {

                        seguirSemMatch();
                    }
                }
        );
    }

    /**
     * Abre o produto quando o resolver retornar
     * um ID válido.
     */
    private void abrirProduto(
            long productId
    ) {

        ScanTutorialState.markCompleted(
                this
        );

        Intent intent =
                new Intent(
                        this,
                        DetalheProdutoActivity.class
                );

        intent.putExtra(
                DetalheProdutoActivity.EXTRA_ID,
                productId
        );

        startActivity(intent);

        finish();
    }

    /**
     * Produto ainda não encontrado.
     *
     * Primeiro fluxo:
     *      FRONT → Tutorial 2
     *
     * Fluxos posteriores:
     *      FRONT → BACK
     */
    private void seguirSemMatch() {

        Class<?> destino;

        if (
                ScanTutorialState.isCompleted(
                        this
                )
        ) {

            destino =
                    ScanCameraBackActivity.class;

        } else {

            destino =
                    ScanTutorial2Activity.class;
        }

        startActivity(
                new Intent(
                        this,
                        destino
                )
        );

        finish();
    }

    /**
     * Controla o estado visual do botão.
     */
    private void setBotaoHabilitado(
            boolean habilitado
    ) {

        if (btnNext == null) {
            return;
        }

        btnNext.setEnabled(
                habilitado
        );

        btnNext.setAlpha(
                habilitado
                        ? 1.0f
                        : 0.5f
        );
    }

    /**
     * Botão de voltar.
     */
    public void onScanBackClicked(
            View ignored
    ) {

        finish();
    }

    /**
     * Mantém a tela do Scan em fullscreen.
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