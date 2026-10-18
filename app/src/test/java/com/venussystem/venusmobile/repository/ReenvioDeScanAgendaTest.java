package com.venussystem.venusmobile.repository;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ReenvioDeScanAgendaTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
    }

    private List<WorkInfo> trabalhos() throws Exception {
        return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(ReenvioDeScanAgenda.NOME_DO_TRABALHO).get();
    }

    @Test
    public void agendar_criaUmTrabalhoQueEsperaInternet() throws Exception {
        ReenvioDeScanAgenda.agendar(context, 0L);

        List<WorkInfo> trabalhos = trabalhos();
        assertEquals(1, trabalhos.size());
        assertEquals(WorkInfo.State.ENQUEUED, trabalhos.get(0).getState());
        assertEquals(androidx.work.NetworkType.CONNECTED,
                trabalhos.get(0).getConstraints().getRequiredNetworkType());
    }

    @Test
    public void agendarDuasVezes_mantemUmTrabalhoSo() throws Exception {
        ReenvioDeScanAgenda.agendar(context, 0L);
        ReenvioDeScanAgenda.agendar(context, 0L);

        assertEquals(1, trabalhos().size());
    }

    @Test
    public void semContaLogada_otrabalhoTerminaSemFazerNada() {
        ReenvioDeScanWorker trabalhador = TestListenableWorkerBuilder
                .from(context, ReenvioDeScanWorker.class).build();

        assertEquals(ListenableWorker.Result.success(), trabalhador.doWork());
    }

    @Test
    public void avisoDeEnvio_umProdutoDizOnome_variosDizemQuantos() {
        org.robolectric.Shadows.shadowOf((android.app.Application) context)
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        com.venussystem.venusmobile.view.Notificador.criarCanais(context);
        android.app.NotificationManager gerente =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        ReenvioDeScanWorker.avisarEnvio(context, java.util.Arrays.asList("Hidratante"));
        android.app.Notification um = org.robolectric.Shadows.shadowOf(gerente).getAllNotifications().get(0);
        assertEquals("Produto enviado", um.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString());
        assertEquals("Hidratante foi enviado para análise.",
                um.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString());

        ReenvioDeScanWorker.avisarEnvio(context, java.util.Arrays.asList("Hidratante", "Tônico", "Sabonete"));
        // Mesmo id: o segundo aviso troca o primeiro.
        assertEquals(1, org.robolectric.Shadows.shadowOf(gerente).getAllNotifications().size());
        android.app.Notification varios = org.robolectric.Shadows.shadowOf(gerente).getAllNotifications().get(0);
        assertEquals("3 produtos foram enviados para análise.",
                varios.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString());
    }

    @Test
    public void avisoDeEnvio_semNomeLido_dizSeuProduto() {
        org.robolectric.Shadows.shadowOf((android.app.Application) context)
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        com.venussystem.venusmobile.view.Notificador.criarCanais(context);
        android.app.NotificationManager gerente =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        ReenvioDeScanWorker.avisarEnvio(context, java.util.Collections.singletonList(null));

        android.app.Notification aviso = org.robolectric.Shadows.shadowOf(gerente).getAllNotifications().get(0);
        assertEquals("Seu produto foi enviado para análise.",
                aviso.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString());
    }
}
