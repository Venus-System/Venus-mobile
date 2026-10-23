package com.venussystem.venusmobile.repository;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.venussystem.venusmobile.domain.aviso.AvisoDeAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.model.ProdutoEmAnalise.Situacao;
import com.venussystem.venusmobile.view.DetalheProdutoActivity;
import com.venussystem.venusmobile.view.Notificador;
import com.venussystem.venusmobile.view.PrincipalActivity;
import com.venussystem.venusmobile.view.ProdutosEmAnaliseActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class DecisoesAgendaTest {

    private Application context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
    }

    private List<WorkInfo> trabalhos(String nome) throws Exception {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWork(nome).get();
    }

    private int emFila(String nome) throws Exception {
        int total = 0;
        for (WorkInfo info : trabalhos(nome)) {
            if (info.getState() == WorkInfo.State.ENQUEUED) {
                total++;
            }
        }
        return total;
    }

    // ---- Agendamento ----

    @Test
    public void verificarAgora_criaUmTrabalhoQueEsperaInternet() throws Exception {
        DecisoesAgenda.verificarAgora(context);

        List<WorkInfo> trabalhos = trabalhos(DecisoesAgenda.CONFERENCIA_AGORA);
        assertEquals(1, trabalhos.size());
        assertEquals(WorkInfo.State.ENQUEUED, trabalhos.get(0).getState());
        assertEquals(NetworkType.CONNECTED, trabalhos.get(0).getConstraints().getRequiredNetworkType());
    }

    @Test
    public void verificarAgoraDeNovo_oMaisNovoSubstituiOAnterior() throws Exception {
        DecisoesAgenda.verificarAgora(context);
        DecisoesAgenda.verificarAgora(context);

        assertEquals(1, emFila(DecisoesAgenda.CONFERENCIA_AGORA));
    }

    @Test
    public void ligarPeriodica_deNovoMantemUmaSo() throws Exception {
        DecisoesAgenda.ligarPeriodica(context);
        DecisoesAgenda.ligarPeriodica(context);

        assertEquals(1, trabalhos(DecisoesAgenda.CONFERENCIA_PERIODICA).size());
        assertEquals(1, emFila(DecisoesAgenda.CONFERENCIA_PERIODICA));
    }

    @Test
    public void desligarPeriodica_cancelaAConferencia() throws Exception {
        DecisoesAgenda.ligarPeriodica(context);

        DecisoesAgenda.desligarPeriodica(context);

        assertEquals(0, emFila(DecisoesAgenda.CONFERENCIA_PERIODICA));
    }

    @Test
    public void cancelar_tiraAsDuasConferencias() throws Exception {
        DecisoesAgenda.verificarAgora(context);
        DecisoesAgenda.ligarPeriodica(context);

        DecisoesAgenda.cancelar(context);

        assertEquals(0, emFila(DecisoesAgenda.CONFERENCIA_AGORA));
        assertEquals(0, emFila(DecisoesAgenda.CONFERENCIA_PERIODICA));
    }

    @Test
    public void semContaLogada_oTrabalhoTerminaEDesligaAPeriodica() throws Exception {
        DecisoesAgenda.ligarPeriodica(context);
        DecisoesWorker trabalhador = TestListenableWorkerBuilder.from(context, DecisoesWorker.class).build();

        assertEquals(ListenableWorker.Result.success(), trabalhador.doWork());

        assertEquals(0, emFila(DecisoesAgenda.CONFERENCIA_PERIODICA));
    }

    // ---- O aviso ----

    private List<Notification> avisos() {
        NotificationManager gerente = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return shadowOf(gerente).getAllNotifications();
    }

    private static AvisoDeAnalise aviso(String id, Situacao situacao, Long produtoId) {
        return AvisoDeAnalise.de(new ProdutoEmAnalise(id, situacao, "Creme", "Cerave", null, null, null, null,
                "Foto ruim", produtoId));
    }

    @Test
    public void aprovadoNoCatalogo_otoqueAbreOProdutoPorCimaDaPrincipal() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Notificador.criarCanais(context);

        DecisoesWorker.avisar(context, aviso("scan-1", Situacao.APROVADO, 57L));

        assertEquals(1, avisos().size());
        Notification notificacao = avisos().get(0);
        assertEquals("Produto aprovado", notificacao.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Cerave Creme já está no catálogo da Venus.",
                notificacao.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals("analise_de_produtos", notificacao.getChannelId());
        Intent[] pilha = shadowOf(notificacao.contentIntent).getSavedIntents();
        assertEquals(PrincipalActivity.class.getName(), pilha[0].getComponent().getClassName());
        assertEquals(DetalheProdutoActivity.class.getName(), pilha[1].getComponent().getClassName());
        assertEquals(57L, pilha[1].getLongExtra(DetalheProdutoActivity.EXTRA_ID, -1L));
    }

    @Test
    public void recusado_otoqueAbreProdutosEmAnalise() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Notificador.criarCanais(context);

        DecisoesWorker.avisar(context, aviso("scan-1", Situacao.RECUSADO, null));

        Intent[] pilha = shadowOf(avisos().get(0).contentIntent).getSavedIntents();
        assertEquals(ProdutosEmAnaliseActivity.class.getName(), pilha[pilha.length - 1].getComponent().getClassName());
    }

    @Test
    public void doisProdutosDecididos_doisAvisos() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Notificador.criarCanais(context);

        DecisoesWorker.avisar(context, aviso("scan-1", Situacao.APROVADO, 57L));
        DecisoesWorker.avisar(context, aviso("scan-2", Situacao.RECUSADO, null));

        assertEquals(2, avisos().size());
    }

    @Test
    public void semPermissaoDeNotificacao_naoPublicaENaoQuebra() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Notificador.criarCanais(context);

        DecisoesWorker.avisar(context, aviso("scan-1", Situacao.APROVADO, 57L));

        assertEquals(0, avisos().size());
    }
}
