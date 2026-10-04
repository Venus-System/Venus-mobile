package com.venussystem.venusmobile.view;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.model.ScanCosmeticClassification;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanProductMatch;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.view.util.ScanCosmeticClassifier;
import com.venussystem.venusmobile.view.util.ScanFrontExtractor;
import com.venussystem.venusmobile.viewmodel.ScanViewModel;

import java.io.File;

/**
 * Tela de transição/processamento após a foto frontal.
 *
 * Não altera o processamento existente:
 * - OCR continua no ScanViewModel;
 * - extração continua no ScanFrontExtractor;
 * - match continua no ScanProductMatchRepository via ViewModel;
 * - classificação cosmética continua no ScanCosmeticClassifier.
 *
 * Esta Activity somente garante que a navegação ocorra no resultado final.
 */
public class ScanSuccessFrontActivity extends AppCompatActivity {

    private static final String TAG_OCR = "VENUS_OCR";
    private static final String TAG_SCAN = "VENUS_SCAN";

    private String photoPath;
    private ScanViewModel scanViewModel;

    private ScanOcrResult ultimoOcr;
    private ScanFrontData ultimoFrontData;
    private ScanCosmeticClassification ultimaClassificacao;

    private boolean navegando = false;
    private boolean leituraIniciada = false;

    private TextView successBadge;
    private View retryButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();
        setContentView(R.layout.activity_scan_success_front);

        ImageView photo = findViewById(R.id.imgCapturedPhoto);
        successBadge = findViewById(R.id.txtSuccessBadge);
        retryButton = findViewById(R.id.btnScanNext);

        photoPath = getIntent().getStringExtra(
                ScanCameraBaseActivity.EXTRA_OUTPUT_KEY
        );

        if (photoPath != null) {
            File file = new File(photoPath);
            if (file.exists() && file.length() > 0) {
                photo.setImageURI(Uri.fromFile(file));
            }
        }

        if (retryButton != null) {
            retryButton.setEnabled(false);
            retryButton.setOnClickListener(v -> refazerFotoFrontal());
        }

        View backButton = findViewById(R.id.btnScanBack);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        setBadge("Analisando a foto...");

        scanViewModel = new ViewModelProvider(this)
                .get(ScanViewModel.class);

        observarViewModel();
        iniciarLeitura();
    }

    private void observarViewModel() {
        scanViewModel.getOcrResult().observe(
                this,
                this::processarOcr
        );

        scanViewModel.getCarregando().observe(
                this,
                carregando -> {
                    if (carregando == null || navegando) {
                        return;
                    }

                    if (carregando) {
                        setBadge("Lendo a foto e buscando o produto...");
                    }
                }
        );

        scanViewModel.getProductMatch().observe(
                this,
                this::processarMatch
        );

        scanViewModel.getErro().observe(
                this,
                exception -> {
                    if (exception == null || navegando) {
                        return;
                    }

                    Log.e(
                            TAG_SCAN,
                            "Erro durante o processamento da frente. Encerrando o scan."
                    );
                    fecharFluxoParaPrincipal(
                    "Não foi possível ler a foto. Tente outra mais nítida."
                    );
                }
        );
    }

    private void iniciarLeitura() {
        if (leituraIniciada) {
            return;
        }

        leituraIniciada = true;

        if (photoPath == null || photoPath.trim().isEmpty()) {
            Log.e(TAG_OCR, "Foto frontal não recebida. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não lemos a foto. Tire outra com o rótulo visível."
            );
            return;
        }

        File photoFile = new File(photoPath);

        if (!photoFile.exists()
                || !photoFile.isFile()
                || photoFile.length() <= 0) {
            Log.e(TAG_OCR, "Foto frontal inválida ou indisponível. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "A foto ficou inválida. Tire outra da frente."
            );
            return;
        }

        setBadge("Lendo a foto e buscando o produto...");

        scanViewModel.reconhecer(Uri.fromFile(photoFile));
    }

    private void processarOcr(ScanOcrResult result) {
        if (result == null || navegando) {
            return;
        }

        ultimoOcr = result;

        if (result.getFullText() == null
                || result.getFullText().trim().isEmpty()) {
            Log.e(TAG_SCAN, "OCR FRONT VAZIO. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não foi possível processar a foto. Tire uma nova foto."
            );
            return;
        }

        ultimoFrontData = ScanFrontExtractor.extract(
                result.getLines()
        );

        setBadge("Buscando o produto...");

        scanViewModel.identificarProduto(ultimoFrontData);
    }

    private void processarMatch(ScanProductMatch match) {
        if (match == null || navegando) {
            return;
        }

        if (match.hasError()) {
            Log.e(
                    TAG_SCAN,
                    "Erro no matching: " + match.getErrorMessage()
            );
            setBadge("Catálogo indisponível. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                        "Catálogo indisponível. Tente novamente."
            );
            return;
        }

        if (match.isFound() && match.getProduto() != null) {
            Produto produto = match.getProduto();

            if (produto.getId() == null || produto.getId() <= 0) {
                Log.e(TAG_SCAN, "Produto encontrado sem ID válido. Encerrando o scan.");
                fecharFluxoParaPrincipal(
                        "Produto encontrado, mas não foi possível abri-lo."
                );
                return;
            }

            abrirProduto(produto, match);
            return;
        }

        setBadge("Produto não encontrado. Verificando...");

        if (ultimoOcr == null || ultimoFrontData == null) {
            Log.e(TAG_SCAN, "Dados do OCR/extração ausentes. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não entendemos a foto. Mostre o rótulo e tente novamente."
            );
            return;
        }

        try {
            ultimaClassificacao = ScanCosmeticClassifier.classify(
                    ultimoOcr,
                    ultimoFrontData
            );
        } catch (Exception exception) {
            Log.e(TAG_SCAN, "Erro ao classificar a frente. Encerrando o scan.", exception);
            fecharFluxoParaPrincipal(
                    "Não foi possível analisar. Mostre o rótulo inteiro."
            );
            return;
        }

        boolean liberar = ScanCosmeticClassifier.shouldOpenNewCosmeticFlow(
                match.isFound(),
                ultimaClassificacao
        );

        if (liberar) {
            abrirFluxoProdutoNaoCadastrado();
            return;
        }

        if (ultimaClassificacao.isNonCosmetic()) {
            fecharFluxoParaPrincipal(
                    "Foto recusada. Fotografe um cosmético."
            );
        } else {
            fecharFluxoParaPrincipal(
                    "Foto recusada. Mostre o nome e o tipo do produto."
            );
        }
    }

    private void abrirFluxoProdutoNaoCadastrado() {
        if (navegando) {
            return;
        }

        if (photoPath == null
                || ultimoOcr == null
                || ultimoFrontData == null
                || ultimaClassificacao == null) {
            Log.e(TAG_SCAN, "Dados insuficientes para criar o draft do novo produto. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não foi possível preparar a foto. Scan encerrado."
            );
            return;
        }

        navegando = true;
        setBadge("Preparando o próximo passo...");

        com.venussystem.venusmobile.model.Usuario owner =
                new com.venussystem.venusmobile.repository.AutenticacaoRepository().usuarioLogado();
        if (owner == null) {
            fecharFluxoParaPrincipal("Entre na sua conta para enviar.");
            return;
        }
        ScanSubmissionDraft draft = ScanSubmissionDraft.fromFront(
                photoPath,
                ultimoOcr,
                ultimoFrontData,
                ultimaClassificacao
        );
        draft.bindToUser(owner.getUid());

        Intent intent = new Intent(
                this,
                ScanProductNotRegisteredActivity.class
        );

        draft.putInto(intent);

        try {
            startActivity(intent);
            finish();
        } catch (ActivityNotFoundException exception) {
            Log.e(
                    TAG_SCAN,
                    "Não foi possível abrir a tela de produto não cadastrado. Encerrando o scan.",
                    exception
            );
            fecharFluxoParaPrincipal(
                    "Não foi possível abrir a próxima etapa."
            );
        }
    }

    private void abrirProduto(
            @NonNull Produto produto,
            @NonNull ScanProductMatch match
    ) {
        if (navegando) {
            return;
        }

        Long produtoId = produto.getId();

        if (produtoId == null || produtoId <= 0) {
            Log.e(TAG_SCAN, "Produto encontrado sem ID válido ao abrir detalhes. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Produto encontrado, mas não foi possível abri-lo."
            );
            return;
        }

        navegando = true;

        Intent intent = new Intent(
                this,
                DetalheProdutoActivity.class
        );

        intent.putExtra(
                DetalheProdutoActivity.EXTRA_ID,
                produtoId.longValue()
        );

        try {
            startActivity(intent);
            finish();
        } catch (Exception exception) {
            Log.e(TAG_SCAN, "Falha ao abrir detalhes do produto.", exception);
            fecharFluxoParaPrincipal(
                    "Não foi possível abrir os detalhes."
            );
        }
    }

    /**
     * Encerra completamente o contexto do scan e retorna à tela principal.
     * É usado somente quando não há dados suficientes, a imagem é inválida
     * ou não foi possível confirmar um produto cosmético.
     */
    private void fecharFluxoParaPrincipal(@Nullable String mensagem) {
        if (navegando || isFinishing() || isDestroyed()) {
            return;
        }

        navegando = true;

        if (mensagem != null && !mensagem.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    mensagem,
                    Toast.LENGTH_LONG
            ).show();
        }

        try {
            Intent intent = new Intent(this, PrincipalActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(intent);
        } catch (Exception exception) {
            Log.e(TAG_SCAN, "Falha ao retornar para PrincipalActivity.", exception);
        } finally {
            finish();
        }
    }

    private void refazerFotoFrontal() {
        if (navegando) {
            return;
        }

        navegando = true;

        startActivity(
                new Intent(
                        this,
                        ScanCameraFrontActivity.class
                )
        );

        finish();
    }

    private void habilitarRetry() {
        if (retryButton != null) {
            retryButton.setEnabled(true);
        }
    }

    private void setBadge(String texto) {
        if (successBadge != null && texto != null) {
            successBadge.setText(texto);
        }
    }

    public void onScanBackClicked(View ignored) {
        finish();
    }

    private void fullscreen() {
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
