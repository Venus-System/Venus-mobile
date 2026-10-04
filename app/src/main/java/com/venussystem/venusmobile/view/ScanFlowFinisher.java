package com.venussystem.venusmobile.view;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.venussystem.venusmobile.model.ScanSubmissionDraft;
import com.venussystem.venusmobile.model.Usuario;
import com.venussystem.venusmobile.repository.AutenticacaoRepository;
import com.venussystem.venusmobile.repository.ScanDraftStore;
import com.venussystem.venusmobile.repository.ScanSubmissionRepository;
import com.venussystem.venusmobile.repository.api.ScanApiException;
import com.venussystem.venusmobile.repository.api.dto.ScanUploadSignaturesResponse;
import com.venussystem.venusmobile.domain.scan.ScanPhotoQualityPolicy;
import com.venussystem.venusmobile.view.dialog.ScanStatusDialog;

/**
 * Persiste a coleta, envia as fotos e cadastra o scan para análise administrativa.
 */
public final class ScanFlowFinisher {

    private ScanFlowFinisher() {
    }

    /** Compatibilidade com fluxos antigos. */
    public static void finalizar(@NonNull Activity activity) {
        ScanTutorialState.markCompleted(activity);

        Toast.makeText(
                activity,
                "Fluxo concluído.",
                Toast.LENGTH_LONG
        ).show();

        abrirPrincipal(activity);
    }

    /**
     * Finaliza a coleta do novo produto.
     * Salva fotos/rascunho antes de solicitar as assinaturas à API.
     */
    public static void finalizar(
            @NonNull Activity activity,
            @NonNull ScanSubmissionDraft draft
    ) {
        android.util.Log.d("VENUS_SCAN_PIPELINE", "FINISH_REQUEST scanId=" + draft.getScanId()
                + " hasBack=" + (draft.getBackData() != null));
        Usuario owner = new AutenticacaoRepository().usuarioLogado();
        if (owner == null) {
            Toast.makeText(activity, "Entre na conta que iniciou o scan. Nada foi enviado.", Toast.LENGTH_LONG).show();
            return;
        }
        ScanSubmissionRepository repository = new ScanSubmissionRepository(activity);
        repository.prepareLocally(owner.getUid(), draft,
                new ScanSubmissionRepository.Callback() {
                    @Override public void saved(ScanDraftStore.SavedDraft saved) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        android.util.Log.d("VENUS_SCAN_PIPELINE", "DRAFT_SAVED scanId="
                                + saved.draft.getScanId());
                        if (ScanPhotoQualityPolicy.backgroundIsAdvisory(saved.draft.getPhotoQuality())) {
                            Toast.makeText(activity,
                                    "A iluminação ou o fundo não estão ideais; continuaremos e faremos uma revisão adicional.",
                                    Toast.LENGTH_LONG).show();
                        }
                        draft.setStatus(ScanSubmissionDraft.STATUS_WAITING_UPLOAD);
                        solicitarAssinaturas(activity, repository, saved);
                    }
                    @Override public void failed(Exception exception) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        boolean qualityBlocked = exception instanceof IllegalArgumentException
                                && exception.getMessage() != null
                                && exception.getMessage().startsWith("A qualidade das fotos");
                        new android.app.AlertDialog.Builder(activity)
                                .setTitle("Não foi possível salvar o rascunho")
                                .setMessage(qualityBlocked
                                        ? "A foto está muito desfocada ou com iluminação extrema. Tire novas fotos do rótulo e tente novamente. Nada foi enviado."
                                        : "Nada foi enviado. Verifique a conta e o espaço disponível no aparelho.")
                                .setPositiveButton("Tentar novamente", (dialog, which) -> finalizar(activity, draft))
                                .setNegativeButton("Voltar", (dialog, which) -> activity.finish())
                                .show();
                    }
                });
    }

    private static void solicitarAssinaturas(Activity activity, ScanSubmissionRepository repository,
                                             ScanDraftStore.SavedDraft saved) {
        solicitarAssinaturas(activity, repository, saved, false, false);
    }

    private static void solicitarAssinaturas(Activity activity, ScanSubmissionRepository repository,
            ScanDraftStore.SavedDraft saved, boolean connectAccount, boolean accountAttempted) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog progress = ScanStatusDialog.create(activity,
                connectAccount ? "Conectando sua conta" : "Preparando seu scan",
                "Aguarde enquanto verificamos sua conta.",
                true, null, null, () -> abrirPrincipal(activity));
        showWhileAlive(activity, progress);
        ScanSubmissionRepository.SignaturesCallback callback = new ScanSubmissionRepository.SignaturesCallback() {
                    @Override public void ready(ScanUploadSignaturesResponse signatures) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        android.util.Log.d("VENUS_SCAN_PIPELINE", "SIGNATURES_READY scanId="
                                + saved.draft.getScanId());
                        ScanStatusDialog.updateMessage(progress, "Preparando o envio das fotos...");
                        repository.uploadPhotos(saved.firebaseUid, saved.draft.getScanId(), signatures,
                                new ScanSubmissionRepository.UploadCallback() {
                                    @Override public void progress(String side) {
                                        if (activity.isFinishing() || activity.isDestroyed()) return;
                                        ScanStatusDialog.updateMessage(progress, "front".equals(side)
                                                ? "Enviando a foto da frente..." : "Enviando a foto do verso...");
                                    }
                                    @Override public void uploaded(ScanDraftStore.SavedDraft result) {
                                        if (activity.isFinishing() || activity.isDestroyed()) return;
                                        android.util.Log.d("VENUS_SCAN_PIPELINE", "PHOTOS_UPLOADED_BEGIN_CATALOG scanId="
                                                + result.draft.getScanId());
                                        progress.dismiss();
                                        consultarIngredientes(activity, repository, result);
                                    }
                                    @Override public void failed(Exception error) {
                                        if (activity.isFinishing() || activity.isDestroyed()) return;
                                        progress.dismiss();
                                        AlertDialog failure = ScanStatusDialog.create(activity,
                                                "O envio não foi concluído", "Tente novamente. As fotos já confirmadas não serão reenviadas.",
                                                false, "Tentar novamente", () -> solicitarAssinaturas(activity, repository, saved,
                                                        false, accountAttempted || connectAccount),
                                                () -> abrirPrincipal(activity));
                                        showWhileAlive(activity, failure);
                                    }
                                });
                    }
                    @Override public void failed(Exception exception) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        progress.dismiss();
                        ScanApiException failureInfo = exception instanceof ScanApiException
                                ? (ScanApiException) exception : null;
                        boolean forbidden = failureInfo != null && failureInfo.kind == ScanApiException.Kind.FORBIDDEN;
                        boolean alreadyAttempted = accountAttempted || connectAccount;
                        boolean offerConnection = forbidden && !alreadyAttempted;
                        String title = "Não conseguimos continuar";
                        String message = "Tente novamente em instantes.";
                        if (forbidden) {
                            title = offerConnection ? "Vamos conectar sua conta?" : "Sua conta precisa de atenção";
                            message = offerConnection
                                    ? "Use seu nome e e-mail para concluir o cadastro Venus e continuar."
                                    : "Peça ao responsável pelo cadastro para verificar seu acesso.";
                        } else if (failureInfo != null && failureInfo.kind == ScanApiException.Kind.AUTH) {
                            title = "Entre novamente na sua conta";
                            message = "Volte ao início e entre com a mesma conta deste scan.";
                        } else if (failureInfo != null && (failureInfo.kind == ScanApiException.Kind.NETWORK
                                || failureInfo.kind == ScanApiException.Kind.TIMEOUT)) {
                            title = "A conexão não respondeu";
                            message = "Verifique sua internet e tente novamente.";
                        }
                        boolean authError = failureInfo != null && failureInfo.kind == ScanApiException.Kind.AUTH;
                        AlertDialog failure = ScanStatusDialog.create(activity, title, message, false,
                                offerConnection ? "Conectar minha conta" : "Verificar novamente",
                                authError ? null : () -> solicitarAssinaturas(activity, repository, saved,
                                        offerConnection, alreadyAttempted || offerConnection),
                                () -> abrirPrincipal(activity));
                        showWhileAlive(activity, failure);
                    }
                };
        if (connectAccount) repository.connectAccountAndRequestSignatures(saved.firebaseUid, saved.draft.getScanId(), callback);
        else repository.requestUploadSignatures(saved.firebaseUid, saved.draft.getScanId(), callback);
    }

    private static void consultarIngredientes(Activity activity, ScanSubmissionRepository repository,
                                               ScanDraftStore.SavedDraft saved) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        android.util.Log.d("VENUS_SCAN_PIPELINE", "CATALOG_REQUEST scanId=" + saved.draft.getScanId());
        AlertDialog progress = ScanStatusDialog.create(activity, "Consultando ingredientes",
                "Verificando os nomes no catálogo Venus.", true, null, null, () -> abrirPrincipal(activity));
        showWhileAlive(activity, progress);
        repository.checkIngredients(saved.firebaseUid, saved.draft.getScanId(), new ScanSubmissionRepository.Callback() {
            @Override public void saved(ScanDraftStore.SavedDraft result) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                android.util.Log.d("VENUS_SCAN_PIPELINE", "CATALOG_SAVED scanId=" + result.draft.getScanId());
                progress.dismiss();
                enviarParaMongo(activity, repository, result, false);
            }
            @Override public void failed(Exception error) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                android.util.Log.w("VENUS_SCAN_PIPELINE", "CATALOG_FAILED scanId=" + saved.draft.getScanId()
                        + " type=" + error.getClass().getSimpleName()
                        + " kind=" + (error instanceof ScanApiException
                        ? ((ScanApiException) error).kind : "LOCAL")
                        + " http=" + (error instanceof ScanApiException
                        ? ((ScanApiException) error).httpCode : 0));
                progress.dismiss();
                String message = error instanceof ScanApiException && ((ScanApiException) error).kind == ScanApiException.Kind.AUTH
                        ? "Entre com a conta que iniciou o scan. Suas fotos estão salvas."
                        : "Confira a conexão e tente novamente. Suas fotos estão salvas.";
                if (error instanceof ScanApiException && ((ScanApiException) error).kind != ScanApiException.Kind.AUTH)
                    message = "O catálogo está indisponível. Tente novamente mais tarde. Suas fotos estão salvas.";
                showWhileAlive(activity, ScanStatusDialog.create(activity, "Consulta não concluída", message,
                        false, "Tentar novamente", () -> consultarIngredientes(activity, repository, saved),
                        () -> abrirPrincipal(activity)));
            }
        });
    }

    private static void enviarParaMongo(Activity activity, ScanSubmissionRepository repository,
                                         ScanDraftStore.SavedDraft saved, boolean retry) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog progress = ScanStatusDialog.create(activity, "Enviando para análise",
                "Salvando o scan com as fotos e os dados do rótulo.", true, null, null,
                () -> abrirPrincipal(activity));
        showWhileAlive(activity, progress);
        repository.submitToMongo(saved.firebaseUid, saved.draft.getScanId(), activity.getApplicationContext(),
                new ScanSubmissionRepository.SubmissionCallback() {
                    @Override public void submitted(ScanDraftStore.SavedDraft result) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        progress.dismiss();
                        ScanTutorialState.markCompleted(activity);
                        showWhileAlive(activity, ScanStatusDialog.create(activity, "Scan enviado",
                                "O produto foi enviado para análise administrativa.", false,
                                null, null, () -> abrirPrincipal(activity)));
                    }
                    @Override public void failed(Exception error) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        progress.dismiss();
                        ScanApiException apiError = error instanceof ScanApiException ? (ScanApiException) error : null;
                        String message = apiError != null && apiError.kind == ScanApiException.Kind.FORBIDDEN
                                ? "Sua conta não está habilitada para enviar este scan."
                                : apiError != null && (apiError.kind == ScanApiException.Kind.NETWORK
                                || apiError.kind == ScanApiException.Kind.TIMEOUT)
                                ? "A conexão não respondeu. Suas fotos continuam salvas."
                                : "Não foi possível confirmar o envio. Suas fotos continuam salvas.";
                        showWhileAlive(activity, ScanStatusDialog.create(activity, "Envio não concluído", message,
                                false, "Tentar novamente", () -> enviarParaMongo(activity, repository, saved, true),
                                () -> abrirPrincipal(activity)));
                    }
                });
    }

    private static void showWhileAlive(Activity activity, AlertDialog dialog) {
        // Both scan screens are LifecycleOwners. Release windows on rotation/exit.
        if (activity instanceof LifecycleOwner) {
            LifecycleOwner owner = (LifecycleOwner) activity;
            DefaultLifecycleObserver observer = new DefaultLifecycleObserver() {
                @Override public void onDestroy(@NonNull LifecycleOwner source) { dialog.dismiss(); }
            };
            owner.getLifecycle().addObserver(observer);
            dialog.setOnDismissListener(ignored -> owner.getLifecycle().removeObserver(observer));
        }
        dialog.show();
    }

    private static void abrirPrincipal(@NonNull Activity activity) {
        Intent intent = new Intent(
                activity,
                PrincipalActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        activity.startActivity(intent);
        activity.finish();
    }
}
