package com.venussystem.venusmobile.repository;

import android.content.Context;

import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Os passos do envio do scan, bloqueantes, em cima do {@link ScanSubmissionRepository}
 * (o mesmo que a tela do scan usa, com callbacks). Cada passo espera o callback;
 * o limite e maior que o tempo de leitura da rede (3 min), para o proprio erro de
 * rede chegar antes.
 */
final class EtapasDoScan implements ReenvioDeScan.Etapas {

    private static final long LIMITE_MINUTOS = 4L;

    private final Context context;
    private final ScanSubmissionRepository repositorio;

    EtapasDoScan(Context context) {
        this.context = context.getApplicationContext();
        this.repositorio = new ScanSubmissionRepository(this.context);
    }

    @Override
    public List<ScanDraftStore.SavedDraft> pendentes(String uid) throws IOException {
        return repositorio.pendentes(uid);
    }

    @Override
    public long ultimaAlteracaoMs(String uid, String scanId) throws IOException {
        return repositorio.ultimaAlteracaoMs(uid, scanId);
    }

    @Override
    public ScanUploadSignaturesResponse assinaturas(String uid, String scanId) throws Exception {
        CompletableFuture<ScanUploadSignaturesResponse> resposta = new CompletableFuture<>();
        repositorio.requestUploadSignatures(uid, scanId, new ScanSubmissionRepository.SignaturesCallback() {
            @Override
            public void ready(ScanUploadSignaturesResponse signatures) {
                resposta.complete(signatures);
            }

            @Override
            public void failed(Exception exception) {
                resposta.completeExceptionally(exception);
            }
        });
        return esperar(resposta);
    }

    @Override
    public void enviarFotos(String uid, String scanId, ScanUploadSignaturesResponse assinaturas) throws Exception {
        CompletableFuture<Void> resposta = new CompletableFuture<>();
        repositorio.uploadPhotos(uid, scanId, assinaturas, new ScanSubmissionRepository.UploadCallback() {
            @Override
            public void progress(String side) {
            }

            @Override
            public void uploaded(ScanDraftStore.SavedDraft saved) {
                resposta.complete(null);
            }

            @Override
            public void failed(Exception error) {
                resposta.completeExceptionally(error);
            }
        });
        esperar(resposta);
    }

    @Override
    public void consultarIngredientes(String uid, String scanId) throws Exception {
        CompletableFuture<Void> resposta = new CompletableFuture<>();
        repositorio.checkIngredients(uid, scanId, new ScanSubmissionRepository.Callback() {
            @Override
            public void saved(ScanDraftStore.SavedDraft draft) {
                resposta.complete(null);
            }

            @Override
            public void failed(Exception exception) {
                resposta.completeExceptionally(exception);
            }
        });
        esperar(resposta);
    }

    @Override
    public void cadastrar(String uid, String scanId) throws Exception {
        CompletableFuture<Void> resposta = new CompletableFuture<>();
        repositorio.submitToMongo(uid, scanId, context, new ScanSubmissionRepository.SubmissionCallback() {
            @Override
            public void submitted(ScanDraftStore.SavedDraft saved) {
                resposta.complete(null);
            }

            @Override
            public void failed(Exception exception) {
                resposta.completeExceptionally(exception);
            }
        });
        esperar(resposta);
    }

    /** Devolve o erro original (ScanApiException, ScanUploadException...), nao o embrulho do futuro. */
    private static <T> T esperar(CompletableFuture<T> futuro) throws Exception {
        try {
            return futuro.get(LIMITE_MINUTOS, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof Exception) {
                throw (Exception) causa;
            }
            throw e;
        }
    }
}
