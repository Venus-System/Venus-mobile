package com.venussystem.venusmobile.lembrete;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.DadosDaConta;
import com.venussystem.venusmobile.repository.LembreteQuestionarioRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.view.Notificador;
import com.venussystem.venusmobile.view.PesquisaInicialActivity;

import java.time.ZoneId;

/**
 * O lembrete que o proprio app dispara, sem servidor, para a pessoa terminar o
 * questionario (e liberar o "Match com voce"): ate 3 vezes, depois de "responder
 * depois" (ver {@link com.venussystem.venusmobile.domain.lembrete.AgendaQuestionario}).
 *
 * E um alarme do AlarmManager para o {@link LembretesReceiver}. O alarme "inexato
 * mas que acorda no modo de economia" (setAndAllowWhileIdle) nao pede permissao
 * especial; o horario pode atrasar alguns minutos com o celular parado. Alarmes
 * somem quando o celular reinicia ou o app e atualizado, entao o receptor agenda
 * de novo nesses casos, e o app agenda ao abrir.
 *
 * O lembrete e de uma conta: ao sair, o alarme dela sai junto (o estado fica
 * guardado para quando ela voltar).
 */
public final class Lembretes {

    static final String ACAO_QUESTIONARIO = "com.venussystem.venusmobile.lembrete.QUESTIONARIO";

    @VisibleForTesting
    static final int ID_NOTIFICACAO_QUESTIONARIO = 7001;

    // Lembrete que deveria ter saido enquanto o celular estava desligado: sai logo
    // depois de ligar, sem acordar a pessoa com um aviso atrasado no ato.
    private static final long ATRASO_SE_JA_PASSOU_MS = 30_000L;

    private Lembretes() {
    }

    // ---- O que as telas usam ----

    /** "Responder depois" com o lembrete aceito. */
    public static void ativarQuestionario(Context context) {
        String uid = DadosDaConta.uidAtual(context);
        if (uid == null) {
            return;
        }
        long agora = System.currentTimeMillis();
        new LembreteQuestionarioRepository(context, uid).ativar(agora);
        agendarQuestionario(context, uid, agora, ZoneId.systemDefault());
    }

    /** Ao abrir o app, depois de reiniciar o celular, de atualizar o app ou de mudar o fuso. */
    public static void reagendarTudo(Context context) {
        reagendarTudo(context, DadosDaConta.uidAtual(context), System.currentTimeMillis(),
                ZoneId.systemDefault());
    }

    /** No logout: tira o alarme da conta que sai. O estado dela continua guardado. */
    public static void cancelarDaContaAtual(Context context) {
        cancelar(context, intentQuestionario(context));
    }

    // ---- Agendamento (o uid e a hora entram como parametro para os testes) ----

    @VisibleForTesting
    static void reagendarTudo(Context context, @Nullable String uid, long agora, ZoneId zona) {
        if (uid == null) {
            return;
        }
        agendarQuestionario(context, uid, agora, zona);
    }

    @VisibleForTesting
    static void agendarQuestionario(Context context, String uid, long agora, ZoneId zona) {
        LembreteQuestionarioRepository estado = new LembreteQuestionarioRepository(context, uid);
        if (!estado.ativo()) {
            cancelar(context, intentQuestionario(context));
            return;
        }
        if (new PerfilRepository(context, uid).jaRespondeuQuestionario()) {
            // Ja respondeu: nao ha mais o que lembrar.
            estado.desativar();
            cancelar(context, intentQuestionario(context));
            return;
        }
        long quando = estado.proximoEm(zona);
        if (quando < 0) {
            cancelar(context, intentQuestionario(context));
            return;
        }
        armar(context, intentQuestionario(context), Math.max(quando, agora + ATRASO_SE_JA_PASSOU_MS));
    }

    // ---- O alarme disparou ----

    @VisibleForTesting
    static void disparar(Context context, Intent intent, @Nullable String uid, long agora, ZoneId zona) {
        if (uid == null || !ACAO_QUESTIONARIO.equals(intent.getAction())) {
            // Sem conta logada nao ha de quem lembrar (o alarme era de uma conta que saiu).
            return;
        }
        LembreteQuestionarioRepository estado = new LembreteQuestionarioRepository(context, uid);
        if (!estado.ativo()) {
            return;
        }
        if (new PerfilRepository(context, uid).jaRespondeuQuestionario()) {
            estado.desativar();
            return;
        }
        Notificador.mostrar(context, Notificador.Canal.LEMBRETES, ID_NOTIFICACAO_QUESTIONARIO,
                context.getString(R.string.lembrete_questionario_notif_titulo),
                context.getString(R.string.lembrete_questionario_notif_texto),
                new Intent(context, PesquisaInicialActivity.class));
        estado.registrarEnvio(agora);
        agendarQuestionario(context, uid, agora, zona);
    }

    // ---- AlarmManager ----

    private static Intent intentQuestionario(Context context) {
        return new Intent(context, LembretesReceiver.class)
                .setAction(ACAO_QUESTIONARIO)
                .setData(Uri.parse("venus-lembrete://questionario"));
    }

    private static void armar(Context context, Intent intent, long quandoMs) {
        PendingIntent alarme = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        context.getSystemService(AlarmManager.class)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quandoMs, alarme);
    }

    private static void cancelar(Context context, Intent intent) {
        PendingIntent existente = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (existente != null) {
            context.getSystemService(AlarmManager.class).cancel(existente);
            existente.cancel();
        }
    }
}
