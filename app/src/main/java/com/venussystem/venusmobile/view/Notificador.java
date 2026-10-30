package com.venussystem.venusmobile.view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.widget.RemoteViews;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;

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

    public enum Visual {
        APROVADO(R.drawable.bg_notificacao_aprovado, R.drawable.veninha_cara_feliz,
                R.color.notificacao_aprovado_fim),
        RECUSADO(R.drawable.bg_notificacao_recusado, R.drawable.veninha_cara_chorando,
                R.color.notificacao_recusado_fim);

        private final int fundo;
        private final int mascote;
        private final int cor;

        Visual(@DrawableRes int fundo, @DrawableRes int mascote, @ColorRes int cor) {
            this.fundo = fundo;
            this.mascote = mascote;
            this.cor = cor;
        }
    }

    private Notificador() {
    }

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

    public static boolean mostrar(Context context, Canal canal, int id, String titulo,
                                  String texto, Intent... telas) {
        return mostrar(context, canal, id, titulo, texto, null, telas);
    }

    @SuppressLint("MissingPermission")
    public static boolean mostrar(Context context, Canal canal, int id, String titulo,
                                  String texto, @Nullable Visual visual, Intent... telas) {
        if (!temPermissao(context)) {
            return false;
        }

        TaskStackBuilder pilha = TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, PrincipalActivity.class));
        for (Intent tela : telas) {
            pilha.addNextIntent(tela);
        }
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

        if (visual != null) {
            notificacao.setColor(ContextCompat.getColor(context, visual.cor))
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(cartao(context, R.layout.notificacao_decisao, visual, titulo, texto))
                    .setCustomBigContentView(cartao(context, R.layout.notificacao_decisao_expandida,
                            visual, titulo, texto));
        }

        NotificationManagerCompat.from(context).notify(id, notificacao.build());
        return true;
    }

    private static RemoteViews cartao(Context context, @LayoutRes int layout, Visual visual,
                                      String titulo, String texto) {
        RemoteViews cartao = new RemoteViews(context.getPackageName(), layout);
        cartao.setInt(R.id.notificacao_fundo, "setBackgroundResource", visual.fundo);
        cartao.setImageViewResource(R.id.notificacao_mascote, visual.mascote);
        cartao.setTextViewText(R.id.notificacao_titulo, titulo);
        cartao.setTextViewText(R.id.notificacao_texto, texto);
        return cartao;
    }

    public static void cancelarTodas(Context context) {
        NotificationManagerCompat.from(context).cancelAll();
    }
}
