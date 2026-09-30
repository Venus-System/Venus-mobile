package com.venussystem.venusmobile.view;

import android.app.Activity;
import android.app.AlertDialog;
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

/**
 * Persiste a coleta e solicita assinaturas. Ainda não envia fotos ou cadastra no MongoDB.
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
                        draft.setStatus(ScanSubmissionDraft.STATUS_WAITING_UPLOAD);
                        solicitarAssinaturas(activity, repository, saved);
                    }
                    @Override public void failed(Exception exception) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        new android.app.AlertDialog.Builder(activity)
                                .setTitle("Não foi possível salvar o rascunho")
                                .setMessage("Nada foi enviado. Verifique a conta e o espaço disponível no aparelho.")
                                .setPositiveButton("Tentar novamente", (dialog, which) -> finalizar(activity, draft))
                                .setNegativeButton("Voltar", (dialog, which) -> activity.finish())
                                .show();
                    }
                });
    }

    private static void solicitarAssinaturas(Activity activity, ScanSubmissionRepository repository,
                                             ScanDraftStore.SavedDraft saved) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle("Solicitando autorização para as fotos")
                .setMessage("Fotos e dados salvos no aparelho. Aguardando a API; isso pode levar alguns minutos.")
                .setCancelable(false)
                .setNegativeButton("Voltar ao início", (dialog, which) -> abrirPrincipal(activity))
                .create();
        showWhileAlive(activity, progress);
        repository.requestUploadSignatures(saved.firebaseUid, saved.draft.getScanId(),
                new ScanSubmissionRepository.SignaturesCallback() {
                    @Override public void ready(ScanUploadSignaturesResponse signatures) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        progress.dismiss();
                        ScanTutorialState.markCompleted(activity);
                        // Do not persist expiring signatures or claim the photos were uploaded.
                        Toast.makeText(activity,
                                "Assinaturas de frente e verso recebidas. Fotos e dados ainda não foram enviados.",
                                Toast.LENGTH_LONG).show();
                        abrirPrincipal(activity);
                    }
                    @Override public void failed(Exception exception) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        progress.dismiss();
                        String detail = exception instanceof ScanApiException ? exception.getMessage()
                                : "Não foi possível recuperar o rascunho ou autenticar a solicitação.";
                        AlertDialog failure = new AlertDialog.Builder(activity)
                                .setTitle("Não foi possível obter as assinaturas")
                                .setMessage(detail + "\n\nFotos e dados continuam salvos neste aparelho. Nenhum produto foi enviado.")
                                .setCancelable(false)
                                .setPositiveButton("Tentar novamente", (dialog, which) ->
                                        solicitarAssinaturas(activity, repository, saved))
                                .setNegativeButton("Voltar ao início", (dialog, which) -> abrirPrincipal(activity))
                                .create();
                        showWhileAlive(activity, failure);
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
