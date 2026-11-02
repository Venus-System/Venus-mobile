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

public final class Lembretes {

    static final String ACAO_QUESTIONARIO = "com.venussystem.venusmobile.lembrete.QUESTIONARIO";

    @VisibleForTesting
    static final int ID_NOTIFICACAO_QUESTIONARIO = 7001;

    private static final long ATRASO_SE_JA_PASSOU_MS = 30_000L;

    private Lembretes() {
    }

    public static void ativarQuestionario(Context context) {
        String uid = DadosDaConta.uidAtual(context);
        if (uid == null) {
            return;
        }
        long agora = System.currentTimeMillis();
        new LembreteQuestionarioRepository(context, uid).ativar(agora);
        agendarQuestionario(context, uid, agora, ZoneId.systemDefault());
    }

    public static void reagendarTudo(Context context) {
        reagendarTudo(context, DadosDaConta.uidAtual(context), System.currentTimeMillis(),
                ZoneId.systemDefault());
    }

    public static void cancelarDaContaAtual(Context context) {
        cancelar(context, intentQuestionario(context));
    }

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

    @VisibleForTesting
    static void disparar(Context context, Intent intent, @Nullable String uid, long agora, ZoneId zona) {
        if (uid == null || !ACAO_QUESTIONARIO.equals(intent.getAction())) {
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
