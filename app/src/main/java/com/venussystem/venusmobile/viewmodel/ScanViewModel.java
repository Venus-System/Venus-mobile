package com.venussystem.venusmobile.viewmodel;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.vision.text.Text;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanProductMatch;
import com.venussystem.venusmobile.model.ScanStatus;
import com.venussystem.venusmobile.repository.ScanOcrRepository;
import com.venussystem.venusmobile.repository.ScanProductMatchRepository;
import com.venussystem.venusmobile.view.util.ScanFrontExtractor;

import java.util.ArrayList;
import java.util.List;

public class ScanViewModel extends AndroidViewModel {

    private static final String TAG_OCR = "VENUS_OCR";
    private static final String TAG_MATCH = "VENUS_MATCH";

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

    private final MutableLiveData<ScanStatus> status =
            new MutableLiveData<>(ScanStatus.IDLE);

    private boolean identificacaoEmAndamento = false;


    private String ultimaImagemProcessada;

    public ScanViewModel(
            @NonNull Application application
    ) {
        super(application);

        ocrRepository = new ScanOcrRepository();
        productMatchRepository = new ScanProductMatchRepository();
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

    public LiveData<ScanStatus> getStatus() {
        return status;
    }

    public void processarFrente(
            @NonNull Uri imageUri
    ) {

        String chaveImagem = imageUri.toString();
        ScanStatus estadoAtual = status.getValue();

        if (chaveImagem.equals(ultimaImagemProcessada)
                && estadoAtual != null
                && estadoAtual != ScanStatus.IDLE) {
            return;
        }

        if (identificacaoEmAndamento) {
            return;
        }

        ultimaImagemProcessada = chaveImagem;
        identificacaoEmAndamento = true;

        ocrResult.setValue(null);
        productMatch.setValue(null);
        erro.setValue(null);
        carregando.setValue(true);
        status.setValue(ScanStatus.PROCESSANDO_OCR);

        Log.d(
                TAG_OCR,
                "INICIANDO FLUXO COMPLETO DA FRENTE"
        );

        Log.d(
                TAG_OCR,
                "IMAGEM: " + imageUri
        );

        ocrRepository.recognize(
                getApplication(),
                imageUri,
                new ScanOcrRepository.Callback() {

                    @Override
                    public void onSuccess(
                            @NonNull Text result
                    ) {

                        processarResultadoOcr(result);
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {

                        Log.e(
                                TAG_OCR,
                                "ERRO NO OCR",
                                exception
                        );

                        erro.postValue(exception);
                        productMatch.postValue(
                                ScanProductMatch.comErro(
                                        exception.getMessage()
                                )
                        );
                        status.postValue(
                                ScanStatus.ERRO
                        );
                        carregando.postValue(false);
                        identificacaoEmAndamento = false;
                    }
                }
        );
    }

    private void processarResultadoOcr(
            @NonNull Text result
    ) {

        List<String> linhas =
                extrairLinhas(result);

        ScanOcrResult resultado =
                new ScanOcrResult(
                        result.getText(),
                        linhas
                );

        ocrResult.postValue(resultado);

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
                "TEXTO BRUTO:"
        );

        Log.d(
                TAG_OCR,
                result.getText()
        );

        Log.d(
                TAG_OCR,
                "LINHAS: " + linhas
        );

        if (resultado.isEmpty()) {

            Log.d(
                    TAG_MATCH,
                    "OCR SEM TEXTO"
            );

            productMatch.postValue(
                    ScanProductMatch.semMatch()
            );
            status.postValue(
                    ScanStatus.PRODUTO_NAO_ENCONTRADO
            );
            carregando.postValue(false);
            identificacaoEmAndamento = false;

            return;
        }

        try {

            ScanFrontData frontData =
                    ScanFrontExtractor.extract(
                            linhas
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

            status.postValue(
                    ScanStatus.IDENTIFICANDO_PRODUTO
            );

            Log.d(
                    TAG_MATCH,
                    "INICIANDO IDENTIFICACAO"
            );

            productMatchRepository.identificar(
                    frontData,
                    new ScanProductMatchRepository.Callback() {

                        @Override
                        public void onSuccess(
                                @NonNull ScanProductMatch resultadoMatch
                        ) {

                            Log.d(
                                    TAG_MATCH,
                                    "CALLBACK RECEBIDO"
                            );

                            productMatch.postValue(
                                    resultadoMatch
                            );

                            status.postValue(
                                    resultadoMatch.isFound()
                                            && resultadoMatch.getProduto() != null
                                            ? ScanStatus.PRODUTO_ENCONTRADO
                                            : ScanStatus.PRODUTO_NAO_ENCONTRADO
                            );

                            carregando.postValue(false);
                            identificacaoEmAndamento = false;
                        }

                        @Override
                        public void onError(
                                @NonNull Exception exception
                        ) {

                            Log.e(
                                    TAG_MATCH,
                                    "ERRO NO MATCHING",
                                    exception
                            );

                            erro.postValue(exception);
                            productMatch.postValue(
                                    ScanProductMatch.comErro(
                                            exception.getMessage()
                                    )
                            );
                            status.postValue(
                                    ScanStatus.ERRO
                            );
                            carregando.postValue(false);
                            identificacaoEmAndamento = false;
                        }
                    }
            );

        } catch (Exception exception) {

            Log.e(
                    TAG_OCR,
                    "ERRO AO EXTRAIR DADOS DA FRENTE",
                    exception
            );

            erro.postValue(exception);
            productMatch.postValue(
                    ScanProductMatch.comErro(
                            exception.getMessage()
                    )
            );
            status.postValue(
                    ScanStatus.ERRO
            );
            carregando.postValue(false);
            identificacaoEmAndamento = false;
        }
    }

    @NonNull
    private List<String> extrairLinhas(
            @NonNull Text result
    ) {

        List<String> linhas =
                new ArrayList<>();

        for (Text.TextBlock block :
                result.getTextBlocks()) {

            for (Text.Line line :
                    block.getLines()) {

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

    @Deprecated
    public void reconhecer(
            @NonNull Uri imageUri
    ) {
        processarFrente(imageUri);
    }

    @Deprecated
    public void identificarProduto(
            @NonNull ScanFrontData frontData
    ) {

        if (identificacaoEmAndamento) {
            return;
        }

        identificacaoEmAndamento = true;
        carregando.setValue(true);
        erro.setValue(null);
        productMatch.setValue(null);
        status.setValue(
                ScanStatus.IDENTIFICANDO_PRODUTO
        );

        productMatchRepository.identificar(
                frontData,
                new ScanProductMatchRepository.Callback() {

                    @Override
                    public void onSuccess(
                            @NonNull ScanProductMatch resultado
                    ) {

                        productMatch.postValue(resultado);
                        status.postValue(
                                resultado.isFound()
                                        && resultado.getProduto() != null
                                        ? ScanStatus.PRODUTO_ENCONTRADO
                                        : ScanStatus.PRODUTO_NAO_ENCONTRADO
                        );
                        carregando.postValue(false);
                        identificacaoEmAndamento = false;
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {

                        erro.postValue(exception);
                        productMatch.postValue(
                                ScanProductMatch.comErro(
                                        exception.getMessage()
                                )
                        );
                        status.postValue(
                                ScanStatus.ERRO
                        );
                        carregando.postValue(false);
                        identificacaoEmAndamento = false;
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
