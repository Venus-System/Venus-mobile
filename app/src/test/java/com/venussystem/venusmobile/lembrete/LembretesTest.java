package com.venussystem.venusmobile.lembrete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.venussystem.venusmobile.repository.LembreteQuestionarioRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.view.Notificador;
import com.venussystem.venusmobile.view.PesquisaInicialActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class LembretesTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final String UID = "uid-ana";

    private Application app;
    private AlarmManager alarmes;
    private NotificationManager gerente;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        alarmes = app.getSystemService(AlarmManager.class);
        gerente = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        Notificador.criarCanais(app);
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private static long instante(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 10, dia, hora, minuto).atZone(SAO_PAULO).toInstant().toEpochMilli();
    }

    private List<ShadowAlarmManager.ScheduledAlarm> alarmesArmados() {
        return shadowOf(alarmes).getScheduledAlarms();
    }

    private List<Notification> avisos() {
        return shadowOf(gerente).getAllNotifications();
    }

    private static Intent disparoDoQuestionario() {
        return new Intent(Lembretes.ACAO_QUESTIONARIO);
    }

    @Test
    public void desligado_naoArmaAlarme() {
        Lembretes.agendarQuestionario(app, UID, instante(17, 9, 0), SAO_PAULO);

        assertTrue(alarmesArmados().isEmpty());
    }

    @Test
    public void ligado_armaParaUmDiaDepoisAs10h() {
        new LembreteQuestionarioRepository(app, UID).ativar(instante(17, 23, 0));

        Lembretes.agendarQuestionario(app, UID, instante(17, 23, 0), SAO_PAULO);

        assertEquals(1, alarmesArmados().size());
        assertEquals(instante(18, 10, 0), alarmesArmados().get(0).triggerAtTime);
    }

    @Test
    public void agendarDeNovo_trocaOAlarmeEmVezDeDuplicar() {
        new LembreteQuestionarioRepository(app, UID).ativar(instante(17, 23, 0));

        Lembretes.agendarQuestionario(app, UID, instante(17, 23, 0), SAO_PAULO);
        Lembretes.agendarQuestionario(app, UID, instante(17, 23, 30), SAO_PAULO);

        assertEquals(1, alarmesArmados().size());
    }

    @Test
    public void horarioQueJaPassouComOCelularDesligado_saiLogoDepois() {
        new LembreteQuestionarioRepository(app, UID).ativar(instante(10, 23, 0));
        long agora = instante(17, 9, 0);

        Lembretes.agendarQuestionario(app, UID, agora, SAO_PAULO);

        assertEquals(agora + 30_000L, alarmesArmados().get(0).triggerAtTime);
    }

    @Test
    public void dispara_avisaAbrindoOQuestionarioEArmaOProximo() {
        LembreteQuestionarioRepository estado = new LembreteQuestionarioRepository(app, UID);
        estado.ativar(instante(17, 23, 0));
        long agora = instante(18, 10, 0);

        Lembretes.disparar(app, disparoDoQuestionario(), UID, agora, SAO_PAULO);

        assertEquals(1, avisos().size());
        Notification aviso = avisos().get(0);
        assertEquals("Falta pouco para o seu Match", aviso.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        Intent[] pilha = shadowOf(aviso.contentIntent).getSavedIntents();
        assertEquals(PesquisaInicialActivity.class.getName(),
                pilha[pilha.length - 1].getComponent().getClassName());
        assertEquals(1, estado.enviados());
        assertEquals(instante(21, 10, 0), alarmesArmados().get(0).triggerAtTime);
    }

    @Test
    public void depoisDoTerceiro_param() {
        LembreteQuestionarioRepository estado = new LembreteQuestionarioRepository(app, UID);
        estado.ativar(instante(10, 9, 0));
        estado.registrarEnvio(instante(11, 10, 0));
        estado.registrarEnvio(instante(14, 10, 0));

        Lembretes.disparar(app, disparoDoQuestionario(), UID, instante(21, 10, 0), SAO_PAULO);

        assertEquals(1, avisos().size());
        assertEquals(3, estado.enviados());
        assertTrue(alarmesArmados().isEmpty());
    }

    @Test
    public void pessoaJaRespondeu_naoAvisaEDesliga() {
        LembreteQuestionarioRepository estado = new LembreteQuestionarioRepository(app, UID);
        estado.ativar(instante(17, 23, 0));
        new PerfilRepository(app, UID).marcarQuestionarioRespondido();

        Lembretes.disparar(app, disparoDoQuestionario(), UID, instante(18, 10, 0), SAO_PAULO);

        assertTrue(avisos().isEmpty());
        assertFalse(estado.ativo());
        assertTrue(alarmesArmados().isEmpty());
    }

    @Test
    public void responderDepoisDeLigar_oAgendamentoSeguinteCancela() {
        new LembreteQuestionarioRepository(app, UID).ativar(instante(17, 23, 0));
        Lembretes.agendarQuestionario(app, UID, instante(17, 23, 0), SAO_PAULO);
        assertEquals(1, alarmesArmados().size());

        new PerfilRepository(app, UID).marcarQuestionarioRespondido();
        Lembretes.agendarQuestionario(app, UID, instante(17, 23, 30), SAO_PAULO);

        assertTrue(alarmesArmados().isEmpty());
        assertFalse(new LembreteQuestionarioRepository(app, UID).ativo());
    }

    @Test
    public void semPermissaoDeNotificacao_naoAvisaMasContinuaAgendando() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        new LembreteQuestionarioRepository(app, UID).ativar(instante(17, 23, 0));

        Lembretes.disparar(app, disparoDoQuestionario(), UID, instante(18, 10, 0), SAO_PAULO);

        assertTrue(avisos().isEmpty());
        assertEquals(1, alarmesArmados().size());
    }

    @Test
    public void semContaLogada_nadaDispara() {
        new LembreteQuestionarioRepository(app, UID).ativar(instante(17, 23, 0));

        Lembretes.disparar(app, disparoDoQuestionario(), null, instante(18, 10, 0), SAO_PAULO);

        assertTrue(avisos().isEmpty());
        assertTrue(alarmesArmados().isEmpty());
    }

    @Test
    public void reagendarTudo_armaOQuestionarioDaConta() {
        new LembreteQuestionarioRepository(app, UID).ativar(instante(17, 23, 0));

        Lembretes.reagendarTudo(app, UID, instante(17, 23, 30), SAO_PAULO);

        assertEquals(1, alarmesArmados().size());
    }

    @Test
    public void reagendarTudo_semConta_naoArmaNada() {
        new LembreteQuestionarioRepository(app, UID).ativar(instante(17, 23, 0));

        Lembretes.reagendarTudo(app, null, instante(17, 23, 30), SAO_PAULO);

        assertTrue(alarmesArmados().isEmpty());
    }
}
