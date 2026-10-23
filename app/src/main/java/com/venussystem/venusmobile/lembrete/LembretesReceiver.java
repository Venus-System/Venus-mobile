package com.venussystem.venusmobile.lembrete;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.venussystem.venusmobile.repository.DadosDaConta;

import java.time.ZoneId;

/**
 * Recebe o alarme do lembrete e os avisos do sistema depois dos quais o alarme
 * precisa ser armado de novo: celular reiniciado, app atualizado e fuso ou hora
 * mudados (o horario do lembrete e o do relogio da pessoa).
 */
public class LembretesReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        String acao = intent.getAction();
        if (acao == null) {
            return;
        }
        switch (acao) {
            case Lembretes.ACAO_QUESTIONARIO:
                Lembretes.disparar(app, intent, DadosDaConta.uidAtual(app),
                        System.currentTimeMillis(), ZoneId.systemDefault());
                break;
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_TIME_CHANGED:
                Lembretes.reagendarTudo(app);
                break;
            default:
                break;
        }
    }
}
