package com.venussystem.venusmobile.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.vision.text.Text;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanProductMatch;
import com.venussystem.venusmobile.repository.ScanOcrRepository;
import com.venussystem.venusmobile.repository.ScanProductMatchRepository;

/**
 * ViewModel responsável pelo fluxo da leitura da frente.
 *
 * OCR:
 * foto -> ML Kit -> ScanOcrResult
 *
 * Identificação:
 * ScanFrontData -> catálogo -> ScanProductMatch
 */
public class ScanViewModel extends AndroidViewModel {

    private final ScanOcrRepository ocrRepository;
    private final ScanProductMatchRepository productMatchRepository;

    private final MutableLiveData<ScanOcrResult> ocrResult =
            new MutableLiveData<>();

    private final MutableLiveData<Boolean> carregando =
            new MutableLiveData<>(false);

    private final MutableLiveData<Exception> erro =
            new MutableLiveData<>();

    private final MutableLiveData<ScanProductMatch> productMatch =
            new MutableLiveData<>();

    public ScanViewModel(
            @NonNull Application application
    ) {
        super(application);

        ocrRepository =
                new ScanOcrRepository();

        productMatchRepository =
                new ScanProductMatchRepository();
    }

    public LiveData<ScanOcrResult> getOcrResult() {
        return ocrResult;
    }

    public LiveData<Boolean> getCarregando() {
        return carregando;
    }

    public LiveData<Exception> getErro() {
        return erro;
    }

    public LiveData<ScanProductMatch> getProductMatch() {
        return productMatch;
    }

    /**
     * Executa o OCR da imagem.
     */
    public void reconhecer(
            @NonNull Uri imageUri
    ) {

        carregando.setValue(true);
        erro.setValue(null);
        ocrResult.setValue(null);
        productMatch.setValue(null);

        ocrRepository.recognize(
                getApplication(),
                imageUri,
                new ScanOcrRepository.Callback() {

                    @Override
                    public void onSuccess(
                            @NonNull Text result
                    ) {

                        ScanOcrResult resultado =
                                new ScanOcrResult(
                                        result.getText(),
                                        extrairLinhas(result)
                                );

                        ocrResult.postValue(
                                resultado
                        );

                        carregando.postValue(false);
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {

                        erro.postValue(
                                exception
                        );

                        carregando.postValue(false);
                    }
                }
        );
    }

    /**
     * Transforma o objeto Text do ML Kit em uma lista de linhas,
     * preservando a estrutura básica fornecida pelo OCR.
     */
    @NonNull
    private java.util.List<String> extrairLinhas(
            @NonNull Text result
    ) {

        java.util.List<String> linhas =
                new java.util.ArrayList<>();

        for (Text.TextBlock block : result.getTextBlocks()) {

            for (Text.Line line : block.getLines()) {

                String texto =
                        line.getText();

                if (texto != null
                        && !texto.trim().isEmpty()) {

                    linhas.add(
                            texto.trim()
                    );
                }
            }
        }

        return linhas;
    }

    /**
     * Tenta localizar o produto no catálogo real.
     */
    public void identificarProduto(
            @NonNull ScanFrontData frontData
    ) {

        android.util.Log.d(
                "VENUS_MATCH",
                "================================"
        );

        android.util.Log.d(
                "VENUS_MATCH",
                "INICIANDO IDENTIFICACAO"
        );

        android.util.Log.d(
                "VENUS_MATCH",
                "MARCAS: "
                        + frontData.getBrandCandidates()
        );

        android.util.Log.d(
                "VENUS_MATCH",
                "PRODUTOS: "
                        + frontData.getProductCandidates()
        );

        productMatch.setValue(null);

        productMatchRepository.identificar(
                frontData,
                new ScanProductMatchRepository.Callback() {

                    @Override
                    public void onSuccess(
                            @NonNull ScanProductMatch resultado
                    ) {

                        android.util.Log.d(
                                "VENUS_MATCH",
                                "CALLBACK RECEBIDO"
                        );

                        productMatch.postValue(
                                resultado
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {

                        android.util.Log.e(
                                "VENUS_MATCH",
                                "ERRO NO MATCHING",
                                exception
                        );

                        productMatch.postValue(
                                ScanProductMatch.comErro(
                                        exception.getMessage()
                                )
                        );
                    }
                }
        );
    }

    @Override
    protected void onCleared() {

        ocrRepository.close();
        productMatchRepository.close();

        super.onCleared();
    }
}