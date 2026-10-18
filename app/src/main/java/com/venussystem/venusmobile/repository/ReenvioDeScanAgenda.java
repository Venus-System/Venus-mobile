package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Agenda o {@link ReenvioDeScanWorker}: um trabalho so (se ja tem um agendado ou
 * rodando, fica o que esta), que espera ter internet.
 */
public final class ReenvioDeScanAgenda {

    static final String NOME_DO_TRABALHO = "reenvio_de_scan";

    private ReenvioDeScanAgenda() {
    }

    /** @param atrasoMs espera antes da primeira tentativa (a tela do scan pode ainda estar enviando). */
    public static void agendar(Context context, long atrasoMs) {
        OneTimeWorkRequest pedido = new OneTimeWorkRequest.Builder(ReenvioDeScanWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInitialDelay(atrasoMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(NOME_DO_TRABALHO, ExistingWorkPolicy.KEEP, pedido);
    }

    /**
     * Ao abrir o app: se esta conta tem envio pela metade, agenda. A conferencia
     * lista arquivos, entao sai da thread principal.
     */
    public static void agendarSePreciso(Context context) {
        Context app = context.getApplicationContext();
        SincronizacaoRepository.EXECUTOR.execute(() -> {
            String uid = DadosDaConta.uidAtual(app);
            if (uid == null) {
                return;
            }
            try {
                if (!new ScanSubmissionRepository(app).pendentes(uid).isEmpty()) {
                    agendar(app, 0L);
                }
            } catch (IOException ignorado) {
                // Sem conseguir ler os rascunhos agora, tenta na proxima abertura.
            }
        });
    }
}
