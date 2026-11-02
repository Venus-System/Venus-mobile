package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class DecisoesAgenda {

    static final String CONFERENCIA_AGORA = "decisoes_agora";
    static final String CONFERENCIA_PERIODICA = "decisoes_periodica";
    static final long INTERVALO_MINUTOS = 15L;

    private DecisoesAgenda() {
    }

    private static Constraints comInternet() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    public static void verificarAgora(Context context) {
        OneTimeWorkRequest pedido = new OneTimeWorkRequest.Builder(DecisoesWorker.class)
                .setConstraints(comInternet())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(CONFERENCIA_AGORA, ExistingWorkPolicy.REPLACE, pedido);
    }

    static void ligarPeriodica(Context context) {
        PeriodicWorkRequest pedido = new PeriodicWorkRequest.Builder(
                DecisoesWorker.class, INTERVALO_MINUTOS, TimeUnit.MINUTES)
                .setConstraints(comInternet())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(CONFERENCIA_PERIODICA, ExistingPeriodicWorkPolicy.KEEP, pedido);
    }

    static void desligarPeriodica(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(CONFERENCIA_PERIODICA);
    }

    public static void cancelar(Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.cancelUniqueWork(CONFERENCIA_AGORA);
        manager.cancelUniqueWork(CONFERENCIA_PERIODICA);
    }
}
