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

/**
 * Agenda a conferencia das decisoes da equipe sobre os produtos enviados
 * ({@link DecisoesWorker}).
 *
 * Duas coisas: uma conferencia agora (ao abrir o app e quando um produto e
 * enviado) e, enquanto ainda ha produto esperando decisao, uma conferencia
 * periodica. O proprio trabalho liga e desliga a periodica conforme houver ou
 * nao o que esperar, para nao gastar bateria e rede a toa.
 *
 * O Android nao deixa a periodica rodar em menos de 15 minutos, e com o celular
 * parado pode atrasar mais.
 */
public final class DecisoesAgenda {

    static final String CONFERENCIA_AGORA = "decisoes_agora";
    static final String CONFERENCIA_PERIODICA = "decisoes_periodica";
    static final long INTERVALO_MINUTOS = 15L;

    private DecisoesAgenda() {
    }

    private static Constraints comInternet() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /**
     * Confere uma vez, assim que houver internet. O pedido mais novo vale: se um
     * produto acabou de ser enviado enquanto uma conferencia ja corria, ela pode
     * ter lido a lista antes dele, entao a nova a substitui.
     */
    public static void verificarAgora(Context context) {
        OneTimeWorkRequest pedido = new OneTimeWorkRequest.Builder(DecisoesWorker.class)
                .setConstraints(comInternet())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(CONFERENCIA_AGORA, ExistingWorkPolicy.REPLACE, pedido);
    }

    /** Liga a conferencia periodica, se ja nao estiver ligada. */
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

    /** No logout: nada de conferir em nome de uma conta que saiu. */
    public static void cancelar(Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.cancelUniqueWork(CONFERENCIA_AGORA);
        manager.cancelUniqueWork(CONFERENCIA_PERIODICA);
    }
}
