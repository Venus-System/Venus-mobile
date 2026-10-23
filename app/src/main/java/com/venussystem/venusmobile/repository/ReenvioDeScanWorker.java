package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.view.Notificador;
import com.venussystem.venusmobile.view.ProdutosEmAnaliseActivity;

import java.io.IOException;
import java.util.List;

/**
 * Roda quando ha internet e termina os envios de produto que ficaram pela
 * metade ({@link ReenvioDeScan}). Se enviou algo, avisa; se ficou algo para
 * tentar depois, o WorkManager repete com espera crescente, ate
 * {@link #MAXIMO_DE_TENTATIVAS} - depois disso a proxima abertura do app agenda
 * de novo.
 */
public class ReenvioDeScanWorker extends Worker {

    static final int MAXIMO_DE_TENTATIVAS = 6;
    static final int ID_NOTIFICACAO = 9001;

    public ReenvioDeScanWorker(@NonNull Context context, @NonNull WorkerParameters parametros) {
        super(context, parametros);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context contexto = getApplicationContext();
        String uid = DadosDaConta.uidAtual(contexto);
        if (uid == null) {
            // Ninguem logado: os rascunhos de cada conta esperam essa conta voltar.
            return Result.success();
        }

        ReenvioDeScan.Resultado resultado;
        try {
            resultado = new ReenvioDeScan(new EtapasDoScan(contexto), this::isStopped)
                    .tentar(uid, System.currentTimeMillis());
        } catch (IOException e) {
            return repetirOuDesistir();
        }

        if (!resultado.enviados.isEmpty()) {
            avisarEnvio(contexto, resultado.enviados);
            // Um produto novo na fila da equipe: passa a conferir se ja decidiram.
            DecisoesAgenda.verificarAgora(contexto);
        }
        return resultado.tentarDepois ? repetirOuDesistir() : Result.success();
    }

    private Result repetirOuDesistir() {
        return getRunAttemptCount() + 1 >= MAXIMO_DE_TENTATIVAS ? Result.success() : Result.retry();
    }

    static void avisarEnvio(Context contexto, List<String> nomes) {
        String texto;
        if (nomes.size() == 1) {
            String nome = nomes.get(0);
            texto = contexto.getString(R.string.envio_reenviado_texto_um,
                    nome == null ? contexto.getString(R.string.envio_reenviado_nome_padrao) : nome);
        } else {
            texto = contexto.getString(R.string.envio_reenviado_texto_varios, nomes.size());
        }
        Notificador.mostrar(contexto, Notificador.Canal.ENVIO_DE_PRODUTOS, ID_NOTIFICACAO,
                contexto.getString(R.string.envio_reenviado_titulo), texto,
                new Intent(contexto, ProdutosEmAnaliseActivity.class));
    }
}
