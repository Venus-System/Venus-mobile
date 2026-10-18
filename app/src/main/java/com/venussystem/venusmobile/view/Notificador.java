package com.venussystem.venusmobile.view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;

/**
 * Ponto unico das notificacoes do app: os canais, a checagem de permissao e a
 * publicacao. Todas as notificacoes sao locais (o app mesmo as dispara: produto
 * enviado, produto aprovado ou recusado e lembrete do questionario), nao push
 * de servidor.
 *
 * Tocar abre as telas indicadas por cima da {@link PrincipalActivity}, entao o
 * voltar leva ao app mesmo que ele estivesse fechado.
 */
public final class Notificador {

    public enum Canal {
        LEMBRETES("lembretes", R.string.canal_lembretes, R.string.canal_lembretes_descricao),
        ENVIO_DE_PRODUTOS("envio_de_produtos", R.string.canal_envio_produtos,
                R.string.canal_envio_produtos_descricao),
        ANALISE_DE_PRODUTOS("analise_de_produtos", R.string.canal_analise_produtos,
                R.string.canal_analise_produtos_descricao);

        @VisibleForTesting
        final String id;
        private final int nome;
        private final int descricao;

        Canal(String id, @StringRes int nome, @StringRes int descricao) {
            this.id = id;
            this.nome = nome;
            this.descricao = descricao;
        }
    }

    private Notificador() {
    }

    /** Idempotente; chamado ao abrir o app, antes de qualquer alarme disparar. */
    public static void criarCanais(Context context) {
        NotificationManagerCompat gerente = NotificationManagerCompat.from(context);
        for (Canal canal : Canal.values()) {
            NotificationChannel criado = new NotificationChannel(canal.id,
                    context.getString(canal.nome), NotificationManager.IMPORTANCE_DEFAULT);
            criado.setDescription(context.getString(canal.descricao));
            gerente.createNotificationChannel(criado);
        }
    }

    public static boolean temPermissao(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @param id    o mesmo id publica por cima: um lembrete que dispara de novo troca o
     *              anterior em vez de empilhar.
     * @param telas as telas a abrir ao tocar, em ordem; a PrincipalActivity vai embaixo.
     * @return false quando nao publicou porque a permissao foi negada - negar nunca
     * quebra nada, o app continua funcionando sem avisos.
     */
    @SuppressLint("MissingPermission") // conferida logo abaixo
    public static boolean mostrar(Context context, Canal canal, int id, String titulo,
                                  String texto, Intent... telas) {
        if (!temPermissao(context)) {
            return false;
        }

        TaskStackBuilder pilha = TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, PrincipalActivity.class));
        for (Intent tela : telas) {
            pilha.addNextIntent(tela);
        }
        // O mesmo numero na notificacao e no PendingIntent: com um codigo so, o
        // segundo aviso trocaria o destino do primeiro.
        PendingIntent toque = pilha.getPendingIntent(id,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificacao = new NotificationCompat.Builder(context, canal.id)
                .setSmallIcon(R.drawable.ic_notificacao)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(toque);

        NotificationManagerCompat.from(context).notify(id, notificacao.build());
        return true;
    }

    /** No logout: aviso de uma conta que saiu nao pode ficar na barra para a proxima pessoa. */
    public static void cancelarTodas(Context context) {
        NotificationManagerCompat.from(context).cancelAll();
    }
}
