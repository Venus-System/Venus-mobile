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

/**
 * Termina sozinho os envios de produto que ficaram pela metade (sem internet,
 * API dormindo, a pessoa saiu da tela do scan). O rascunho de cada scan ja esta
 * guardado no aparelho com as fotos e o que o OCR leu; aqui so se repete o que a
 * tela do scan faz - assinaturas, fotos, consulta dos ingredientes e cadastro -
 * pulando o que ja foi confirmado.
 *
 * E seguro repetir: o servidor devolve o scan que ja existe quando o mesmo
 * scanId chega de novo, e as fotos ja enviadas nao sobem outra vez.
 *
 * Roda numa thread de fundo (cada passo bloqueia). Em 403/401 desiste: ali a
 * pessoa precisa agir na tela do scan ("Conectar minha conta"), nunca
 * automaticamente.
 */
public class ReenvioDeScan {

    private static final String TAG = "VENUS_SCAN_RESEND";

    /**
     * Um rascunho alterado ha pouco pode estar sendo enviado agora pela tela do
     * scan; so mexe nos que ficaram parados.
     */
    @VisibleForTesting
    static final long IDADE_MINIMA_MS = 2 * 60_000L;

    /** Os passos do envio, cada um bloqueante. A versao real fica em {@link EtapasDoScan}. */
    public interface Etapas {
        List<ScanDraftStore.SavedDraft> pendentes(String uid) throws IOException;

        long ultimaAlteracaoMs(String uid, String scanId) throws IOException;

        ScanUploadSignaturesResponse assinaturas(String uid, String scanId) throws Exception;

        void enviarFotos(String uid, String scanId, ScanUploadSignaturesResponse assinaturas) throws Exception;

        void consultarIngredientes(String uid, String scanId) throws Exception;

        void cadastrar(String uid, String scanId) throws Exception;
    }

    public static final class Resultado {
        /** O nome (como o OCR leu) de cada produto que subiu nesta rodada. */
        public final List<String> enviados;
        /** Sobrou algo que vale tentar de novo (rede, servidor) ou que ainda esta sendo enviado. */
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

    /**
     * @throws IOException se nem foi possivel listar os rascunhos - quem chama tenta de novo.
     */
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

    /** Vale tentar de novo mais tarde (sem rede, servidor ocupado)? Auth e erro de dado, nao. */
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
