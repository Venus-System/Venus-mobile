package com.venussystem.venusmobile.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.vision.text.Text;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.repository.ScanOcrRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel responsável pelo OCR do fluxo de Scan.
 *
 * Neste momento ele trabalha SOMENTE com a foto da FRENTE
 * do produto.
 *
 * Fluxo atual:
 *
 * Camera Front
 *      ↓
 * foto
 *      ↓
 * ScanSuccessFrontActivity
 *      ↓
 * ScanViewModel
 *      ↓
 * ScanOcrRepository
 *      ↓
 * ML Kit
 *      ↓
 * texto + linhas
 *
 * Futuramente:
 *
 * FRONT:
 *      marca
 *      nome do produto
 *      capacidade/volume
 *
 * BACK:
 *      ingredientes
 *      composição
 *      advertências
 *      demais informações do rótulo
 *
 * O tratamento específico do BACK será implementado posteriormente.
 */
public class ScanViewModel extends AndroidViewModel {

    private final ScanOcrRepository ocrRepository;

    private final MutableLiveData<ScanOcrResult> ocrResult =
            new MutableLiveData<>();

    private final MutableLiveData<Boolean> carregando =
            new MutableLiveData<>(false);

    private final MutableLiveData<Exception> erro =
            new MutableLiveData<>();

    public ScanViewModel(
            @NonNull Application application
    ) {
        super(application);

        ocrRepository =
                new ScanOcrRepository();
    }

    /**
     * Resultado estruturado do OCR.
     */
    public LiveData<ScanOcrResult> getOcrResult() {
        return ocrResult;
    }

    /**
     * Informa se o OCR está sendo processado.
     */
    public LiveData<Boolean> getCarregando() {
        return carregando;
    }

    /**
     * Último erro ocorrido durante o OCR.
     */
    public LiveData<Exception> getErro() {
        return erro;
    }

    /**
     * Executa o OCR da imagem recebida.
     *
     * Neste momento essa função é utilizada pelo FRONT.
     *
     * @param imageUri URI da foto real capturada pela CameraX
     */
    public void reconhecer(
            @NonNull Uri imageUri
    ) {

        carregando.setValue(true);
        erro.setValue(null);

        ocrRepository.recognize(
                getApplication(),
                imageUri,
                new ScanOcrRepository.Callback() {

                    @Override
                    public void onSuccess(
                            @NonNull Text result
                    ) {

                        List<String> lines =
                                extrairLinhas(result);

                        ScanOcrResult resultado =
                                new ScanOcrResult(
                                        result.getText(),
                                        lines
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
     * Extrai as linhas preservando a estrutura encontrada
     * pelo ML Kit.
     *
     * O texto completo continua disponível através de
     * Text.getText(), mas também guardamos cada linha
     * separadamente para as próximas etapas do reconhecimento.
     */
    @NonNull
    private List<String> extrairLinhas(
            @NonNull Text result
    ) {

        List<String> lines =
                new ArrayList<>();

        for (
                Text.TextBlock block
                : result.getTextBlocks()
        ) {

            if (block == null) {
                continue;
            }

            for (
                    Text.Line line
                    : block.getLines()
            ) {

                if (line == null) {
                    continue;
                }

                String lineText =
                        line.getText();

                if (
                        lineText != null
                                && !lineText
                                .trim()
                                .isEmpty()
                ) {

                    lines.add(
                            lineText.trim()
                    );
                }
            }
        }

        return lines;
    }

    /**
     * Libera o recognizer do ML Kit quando o ViewModel
     * deixa de existir.
     */
    @Override
    protected void onCleared() {

        super.onCleared();

        ocrRepository.close();
    }
}