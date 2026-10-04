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
                        setBadge("Lendo a foto e buscando o produto no nosso banco...");
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
                            "ERRO NO SCAN",
                            exception
                    );

                    Log.e(
                            TAG_SCAN,
                            "Erro durante o processamento da frente. Encerrando o scan."
                    );
                    fecharFluxoParaPrincipal(
                            "Não foi possível concluir a leitura da foto. Tente novamente com o rótulo mais nítido e bem enquadrado."
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
            Log.e(TAG_OCR, "photoPath nulo ou vazio");
            Log.e(TAG_OCR, "Foto frontal não recebida. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não conseguimos ler esta foto. Tire outra foto da frente do produto, aproximando ou afastando a embalagem até o rótulo ficar bem visível."
            );
            return;
        }

        File photoFile = new File(photoPath);

        if (!photoFile.exists()
                || !photoFile.isFile()
                || photoFile.length() <= 0) {
            Log.e(TAG_OCR, "Foto frontal inválida ou indisponível. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "A foto não pôde ser utilizada porque o arquivo da captura ficou inválido. Tire outra foto da frente do produto."
            );
            return;
        }

        Log.d(TAG_OCR, "INICIANDO PROCESSAMENTO DA FOTO FRONT");
        setBadge("Lendo a foto e buscando o produto no nosso banco...");

        scanViewModel.reconhecer(Uri.fromFile(photoFile));
    }

    private void processarOcr(ScanOcrResult result) {
        if (result == null || navegando) {
            return;
        }

        ultimoOcr = result;

        Log.d(TAG_OCR, "OCR_FRONT_CONCLUIDO chars="
                + (result.getFullText() == null ? 0 : result.getFullText().length())
                + " lines=" + result.getLines().size());

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

        Log.d(TAG_OCR, "FRONT_CANDIDATES brands=" + ultimoFrontData.getBrandCandidates().size()
                + " products=" + ultimoFrontData.getProductCandidates().size()
                + " presentations=" + ultimoFrontData.getPresentationCandidates().size());

        setBadge("Buscando o produto no nosso banco...");

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
            setBadge("Não foi possível consultar o catálogo. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não foi possível consultar o nosso banco de produtos agora. O scan foi encerrado; tente novamente."
            );
            return;
        }

        if (match.isFound() && match.getProduto() != null) {
            Produto produto = match.getProduto();

            if (produto.getId() == null || produto.getId() <= 0) {
                Log.e(TAG_SCAN, "Produto encontrado sem ID válido");
                Log.e(TAG_SCAN, "Produto encontrado sem ID válido. Encerrando o scan.");
                fecharFluxoParaPrincipal(
                        "Encontramos um produto, mas não foi possível abrir os dados dele. Tente o scan novamente."
                );
                return;
            }

            abrirProduto(produto, match);
            return;
        }

        Log.d(TAG_SCAN, "PRODUTO NÃO ENCONTRADO NO CATÁLOGO");
        setBadge("Produto não encontrado. Verificando se é um cosmético...");

        if (ultimoOcr == null || ultimoFrontData == null) {
            Log.e(TAG_SCAN, "Resultado do OCR/extração ainda não disponível");
            Log.e(TAG_SCAN, "Dados do OCR/extração ausentes. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não conseguimos entender a foto frontal. Tire outra foto mostrando claramente o rótulo do produto."
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
                    "Não foi possível analisar esta foto frontal. Tire outra foto com o produto inteiro e o rótulo legível."
            );
            return;
        }

        Log.d(
                TAG_SCAN,
                "CLASSIFICAÇÃO COSMÉTICO: " + ultimaClassificacao.getStatus()
        );
        Log.d(
                TAG_SCAN,
                "CONFIANÇA HEURÍSTICA: " + ultimaClassificacao.getConfidenceScore()
        );
        Log.d(TAG_SCAN, "EVIDENCIAS_COUNT=" + ultimaClassificacao.getEvidence().size());

        boolean catalogoNaoEncontrou = !match.isFound();
        boolean liberar = ScanCosmeticClassifier.shouldOpenNewCosmeticFlow(
                match.isFound(),
                ultimaClassificacao
        );

        Log.d(
                TAG_SCAN,
                "DECISAO NOVO PRODUTO: catalogoNaoEncontrou="
                        + catalogoNaoEncontrou
                        + " confirmadoCosmetico="
                        + ultimaClassificacao.isConfirmedCosmetic()
                        + " liberar="
                        + liberar
        );

        if (liberar) {
            abrirFluxoProdutoNaoCadastrado();
            return;
        }

        if (ultimaClassificacao.isNonCosmetic()) {
            Log.d(TAG_SCAN, "Produto não cosmético. Encerrando o scan e retornando à PrincipalActivity.");
            fecharFluxoParaPrincipal(
                    "Foto recusada: não identificamos um produto cosmético nesta imagem. Fotografe a frente de um produto cosmético e tente novamente."
            );
        } else {
            Log.d(TAG_SCAN, "Produto não confirmado como cosmético. Encerrando o scan e retornando à PrincipalActivity.");
            fecharFluxoParaPrincipal(
                    "Foto recusada: não conseguimos confirmar que esta embalagem é um cosmético. Deixe o nome e o tipo do produto visíveis e tire outra foto."
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
            Log.e(TAG_SCAN, "Dados insuficientes para criar o draft do novo produto");
            Log.e(TAG_SCAN, "Dados insuficientes para criar o draft do novo produto. Encerrando o scan.");
            fecharFluxoParaPrincipal(
                    "Não foi possível preparar os dados desta foto para continuar o cadastro. O scan foi encerrado."
            );
            return;
        }

        navegando = true;
        setBadge("Produto não cadastrado. Preparando o próximo passo...");

        com.venussystem.venusmobile.model.Usuario owner =
                new com.venussystem.venusmobile.repository.AutenticacaoRepository().usuarioLogado();
        if (owner == null) {
            fecharFluxoParaPrincipal("Entre na sua conta antes de enviar um novo produto.");
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
                    "Não foi possível abrir a etapa de novo produto. Tente fazer o scan novamente."
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
                    "O produto foi encontrado, mas não foi possível abrir os dados dele. Tente novamente."
            );
            return;
        }

        navegando = true;

        Log.d(TAG_SCAN, "PRODUTO_ENCONTRADO idPresent=true score=" + match.getScore());

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
                    "Não foi possível abrir os detalhes do produto. Tente o scan novamente."
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
