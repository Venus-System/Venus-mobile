package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.venussystem.venusmobile.domain.aviso.AvisoDeAnalise;
import com.venussystem.venusmobile.view.DetalheProdutoActivity;
import com.venussystem.venusmobile.view.Notificador;
import com.venussystem.venusmobile.view.ProdutosEmAnaliseActivity;

public class DecisoesWorker extends Worker {

    public DecisoesWorker(@NonNull Context context, @NonNull WorkerParameters parametros) {
        super(context, parametros);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context contexto = getApplicationContext();
        String uid = DadosDaConta.uidAtual(contexto);
        if (uid == null) {
            DecisoesAgenda.desligarPeriodica(contexto);
            return Result.success();
        }

        ConsultaDeDecisoes.Resultado resultado = new ConsultaDeDecisoes(contexto, uid).verificar();
        if (resultado.falhou) {
            return Result.retry();
        }

        for (AvisoDeAnalise aviso : resultado.avisos) {
            avisar(contexto, aviso);
        }

        if (resultado.aindaAguardando) {
            DecisoesAgenda.ligarPeriodica(contexto);
        } else {
            DecisoesAgenda.desligarPeriodica(contexto);
        }
        return Result.success();
    }

    static void avisar(Context contexto, AvisoDeAnalise aviso) {
        Intent tela = aviso.destino == AvisoDeAnalise.Destino.PRODUTO
                ? new Intent(contexto, DetalheProdutoActivity.class)
                        .putExtra(DetalheProdutoActivity.EXTRA_ID, aviso.produtoId)
                : new Intent(contexto, ProdutosEmAnaliseActivity.class);
        Notificador.mostrar(contexto, Notificador.Canal.ANALISE_DE_PRODUTOS, aviso.scanId.hashCode(),
                aviso.titulo, aviso.texto, tela);
    }
}
