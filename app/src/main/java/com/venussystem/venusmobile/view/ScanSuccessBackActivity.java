package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.view.util.ScanBackExtractor;
import com.venussystem.venusmobile.viewmodel.ScanViewModel;

import java.io.File;

/**
 * Pós-captura do verso.
 *
 * O processamento existente é preservado:
 * OCR -> ScanBackExtractor -> atualização do draft.
 *
 * Após o processamento, a próxima etapa é aberta automaticamente.
 * Não exige clique no botão da tela de sucesso.
 */
public class ScanSuccessBackActivity extends AppCompatActivity {

    private static final String TAG = "VENUS_BACK";
    private static final long NEXT_STEP_DELAY_MS = 250L;

    private String photoPath;
    private ScanSubmissionDraft draft;
    private ScanViewModel scanViewModel;

    private boolean ocrProcessado = false;
    private boolean navegando = false;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private TextView successBadge;

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();
        setContentView(R.layout.activity_scan_success_back);

        ImageView photo = findViewById(R.id.imgCapturedPhoto);
        successBadge = findViewById(R.id.txtSuccessBadge);

        // A transição deve ser automática. O botão antigo da tela de sucesso
        // não participa mais da navegação.
        View nextButton = findViewById(R.id.btnScanNext);
        if (nextButton != null) {
            nextButton.setVisibility(View.GONE);
            nextButton.setEnabled(false);
        }

        photoPath = getIntent().getStringExtra(
                ScanCameraBaseActivity.EXTRA_OUTPUT_KEY
        );

        draft = ScanSubmissionDraft.fromIntent(getIntent());

        if (photoPath != null && photo != null) {
            File file = new File(photoPath);
            if (file.exists() && file.length() > 0) {
                photo.setImageURI(Uri.fromFile(file));
            }
        }

        View backButton = findViewById(R.id.btnScanBack);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        setBadge("Lendo o verso...");

        // Não existe continuidade válida sem o draft criado pela frente.
        if (draft == null) {
            Log.e(
                    TAG,
                    "DRAFT AUSENTE NO SUCCESS BACK. Encerrando o scan."
            );
            fecharFluxoParaPrincipal();
            return;
        }

        scanViewModel = new ViewModelProvider(this)
                .get(ScanViewModel.class);

        observarViewModel();
        iniciarOcrBack();
    }

    private void observarViewModel() {
        scanViewModel.getOcrResult().observe(
                this,
                this::processarOcrBack
        );

        scanViewModel.getCarregando().observe(
                this,
                carregando -> {
                    if (carregando != null && carregando && !navegando) {
                        setBadge("Lendo os ingredientes...");
                    }
                }
        );

        scanViewModel.getErro().observe(
                this,
                exception -> {
                    if (exception == null || navegando || ocrProcessado) {
                        return;
                    }

                    Log.e(TAG, "ERRO NO OCR BACK. Encerrando o scan.", exception);
                    ocrProcessado = true;
                    fecharFluxoParaPrincipal();
                }
        );
    }

    private void iniciarOcrBack() {
        if (photoPath == null || photoPath.trim().isEmpty()) {
            Log.e(TAG, "PHOTO PATH BACK AUSENTE. Encerrando o scan.");
            fecharFluxoParaPrincipal();
            return;
        }

        File file = new File(photoPath);

        if (!file.exists() || !file.isFile() || file.length() <= 0) {
            Log.e(TAG, "FOTO BACK INVALIDA");
            fecharFluxoParaPrincipal();
            return;
        }

        scanViewModel.reconhecer(Uri.fromFile(file));
    }

    private void processarOcrBack(ScanOcrResult result) {
        if (result == null || ocrProcessado || navegando) {
            return;
        }

        String texto = result.getFullText();

        if (texto == null || texto.trim().isEmpty()) {
            Log.e(TAG, "OCR BACK VAZIO. Encerrando o scan.");
            ocrProcessado = true;
            fecharFluxoParaPrincipal();
            return;
        }

        ocrProcessado = true;

        Log.d(TAG, "OCR_BACK_CONCLUIDO chars=" + texto.length()
                + " lines=" + result.getLines().size());

        setBadge("Organizando os ingredientes...");

        final ScanBackData backData;

        try {
            backData = ScanBackExtractor.extract(result);
        } catch (Exception exception) {
            Log.e(TAG, "ERRO AO PROCESSAR O VERSO.", exception);
            fecharFluxoParaPrincipal();
            return;
        }

        if (backData == null) {
            Log.e(TAG, "ScanBackExtractor retornou null.");
            fecharFluxoParaPrincipal();
            return;
        }

        Log.d(
                TAG,
                "SEÇÃO DE INGREDIENTES: "
                        + backData.isIngredientSectionFound()
        );
        Log.d(
                TAG,
                "INGREDIENTES ENCONTRADOS: "
                        + backData.getIngredients().size()
        );

        Log.d(TAG, "BACK_FIELDS_EXTRACTED manufacturer=" + (!backData.getManufacturer().isEmpty())
                + " batch=" + (!backData.getBatch().isEmpty())
                + " registration=" + (!backData.getRegistrationNumber().isEmpty())
                + " content=" + (!backData.getNetContent().isEmpty())
                + " barcode=" + (!backData.getBarcode().isEmpty()));

        draft.setBackPhotoPath(photoPath);
        draft.setBackOcrText(texto);
        draft.setBackOcrLines(result.getLines());
        draft.setBackData(backData);
        draft.setStatus(ScanSubmissionDraft.STATUS_BACK_PROCESSED);

        setBadge("Verso pronto. Continuando...");

        // IMPORTANTE: não esperar clique do usuário.
        mainHandler.postDelayed(
                this::abrirProximaEtapaAutomaticamente,
                NEXT_STEP_DELAY_MS
        );
    }

    private void abrirProximaEtapaAutomaticamente() {
        if (navegando || isFinishing() || isDestroyed()) {
            return;
        }

        if (draft == null) {
            fecharFluxoParaPrincipal();
            return;
        }

        navegando = true;

        try {
            if (!ScanTutorialState.isTutorial3Seen(this)) {
                Intent intent = new Intent(
                        this,
                        ScanTutorial3Activity.class
                );

                draft.putInto(intent);
                startActivity(intent);
                finish();
                return;
            }

            // Passo 3 já foi visto anteriormente: não mostra novamente.
            // Finaliza automaticamente.
            ScanFlowFinisher.finalizar(this, draft);

        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "ERRO AO ABRIR A PRÓXIMA ETAPA DO VERSO.",
                    exception
            );
            navegando = false;
            fecharFluxoParaPrincipal();
        }
    }

    private void fecharFluxoParaPrincipal() {
        if (navegando) {
            return;
        }

        navegando = true;

        try {
            Intent intent = new Intent(
                    this,
                    PrincipalActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(intent);
            finish();
        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Não foi possível retornar para a PrincipalActivity.",
                    exception
            );
            finish();
        }
    }

    public void onScanBackClicked(View ignored) {
        finish();
    }

    private void setBadge(String texto) {
        if (successBadge != null && texto != null) {
            successBadge.setText(texto);
        }
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
