package com.venussystem.venusmobile.repository;

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.repository.api.ScanApiException;
import com.venussystem.venusmobile.repository.api.ScanUploadException;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

public class ReenvioDeScan {

    private static final String TAG = "VENUS_SCAN_RESEND";

    @VisibleForTesting
    static final long IDADE_MINIMA_MS = 2 * 60_000L;

    public interface Etapas {
        List<ScanDraftStore.SavedDraft> pendentes(String uid) throws IOException;

        long ultimaAlteracaoMs(String uid, String scanId) throws IOException;

        ScanUploadSignaturesResponse assinaturas(String uid, String scanId) throws Exception;

        void enviarFotos(String uid, String scanId, ScanUploadSignaturesResponse assinaturas) throws Exception;

        void consultarIngredientes(String uid, String scanId) throws Exception;

        void cadastrar(String uid, String scanId) throws Exception;
    }

    public static final class Resultado {
        public final List<String> enviados;
        public final boolean tentarDepois;

        Resultado(List<String> enviados, boolean tentarDepois) {
            this.enviados = enviados;
            this.tentarDepois = tentarDepois;
        }
    }

    private final Etapas etapas;
    private final BooleanSupplier cancelado;

    public ReenvioDeScan(Etapas etapas, BooleanSupplier cancelado) {
        this.etapas = etapas;
        this.cancelado = cancelado;
    }

    public Resultado tentar(String uid, long agoraMs) throws IOException {
        List<String> enviados = new ArrayList<>();
        boolean tentarDepois = false;

        for (ScanDraftStore.SavedDraft rascunho : etapas.pendentes(uid)) {
            if (cancelado.getAsBoolean()) {
                tentarDepois = true;
                break;
            }
            String scanId = rascunho.draft.getScanId();
            try {
                if (agoraMs - etapas.ultimaAlteracaoMs(uid, scanId) < IDADE_MINIMA_MS) {
                    tentarDepois = true;
                    continue;
                }
                if (rascunho.frontUpload == null || rascunho.backUpload == null) {
                    etapas.enviarFotos(uid, scanId, etapas.assinaturas(uid, scanId));
                }
                if (rascunho.ingredientCatalog == null) {
                    etapas.consultarIngredientes(uid, scanId);
                }
                etapas.cadastrar(uid, scanId);
                enviados.add(ScanSubmissionMapper.bestFrontCandidate(rascunho.draft.getProductCandidates()));
                Log.d(TAG, "RESENT scanId=" + scanId);
            } catch (Exception e) {
                boolean transitorio = transitorio(e);
                tentarDepois |= transitorio;
                Log.w(TAG, "FAILED scanId=" + scanId + " retry=" + transitorio
                        + " error=" + e.getClass().getSimpleName());
            }
        }
        return new Resultado(enviados, tentarDepois);
    }

    @VisibleForTesting
    static boolean transitorio(Throwable erro) {
        if (erro instanceof ScanApiException) {
            ScanApiException api = (ScanApiException) erro;
            switch (api.kind) {
                case NETWORK:
                case TIMEOUT:
                    return true;
                case HTTP:
                    return httpTransitorio(api.httpCode);
                default:
                    return false;
            }
        }
        if (erro instanceof ScanUploadException) {
            ScanUploadException upload = (ScanUploadException) erro;
            switch (upload.kind) {
                case NETWORK:
                case TIMEOUT:
                case EXPIRED_SIGNATURE:
                    return true;
                case HTTP:
                    return httpTransitorio(upload.httpCode);
                default:
                    return false;
            }
        }
        return erro instanceof TimeoutException || erro instanceof IOException;
    }

    private static boolean httpTransitorio(int codigo) {
        return codigo >= 500 || codigo == 408 || codigo == 429;
    }
}
