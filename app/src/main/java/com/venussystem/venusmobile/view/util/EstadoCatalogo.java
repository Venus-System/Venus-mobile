package com.venussystem.venusmobile.view.util;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.venussystem.venusmobile.R;

public class EstadoCatalogo {
    private static final long ESPERA_ATE_AVISAR = 4000L;

    private final View conteudo;
    private final View blocoCarregando;
    private final View textAcordando;
    private final View blocoErro;
    private final TextView textErro;
    private final TextView textVazio;

    private final Handler relogio = new Handler(Looper.getMainLooper());
    private final Runnable avisarDemora = this::avisarQueDemorou;

    private boolean ocupado;
    private boolean demorou;
    @Nullable
    private String erro;

    public EstadoCatalogo(View painel, View conteudo, Runnable aoTentarDeNovo) {
        this.conteudo = conteudo;
        blocoCarregando = painel.findViewById(R.id.blocoCarregando);
        textAcordando = painel.findViewById(R.id.textAcordando);
        blocoErro = painel.findViewById(R.id.blocoErro);
        textErro = painel.findViewById(R.id.textErro);
        textVazio = painel.findViewById(R.id.textVazio);
        painel.findViewById(R.id.btnTentarDeNovo).setOnClickListener(v -> aoTentarDeNovo.run());
    }

    public void definirCarregando(boolean carregando) {
        ocupado = carregando;
        relogio.removeCallbacks(avisarDemora);
        demorou = false;
        textAcordando.setVisibility(View.GONE);
        if (carregando) {
            relogio.postDelayed(avisarDemora, ESPERA_ATE_AVISAR);
        }
    }

    public void definirErro(@Nullable String mensagem) {
        erro = mensagem;
        textErro.setText(mensagem);
    }

    public void atualizar(boolean ativo, boolean temCatalogo, int quantidade,
                          @StringRes int textoVazio) {
        boolean carregando = ativo && ocupado;

        boolean comErro = ativo && !ocupado && erro != null && !erro.isEmpty() && !temCatalogo;
        boolean vazio = ativo && !ocupado && !comErro && temCatalogo && quantidade == 0;

        blocoCarregando.setVisibility(carregando ? View.VISIBLE : View.GONE);
        textAcordando.setVisibility(carregando && demorou ? View.VISIBLE : View.GONE);
        blocoErro.setVisibility(comErro ? View.VISIBLE : View.GONE);
        textVazio.setText(textoVazio);
        textVazio.setVisibility(vazio ? View.VISIBLE : View.GONE);
        conteudo.setVisibility(ativo && !ocupado && !comErro && !vazio ? View.VISIBLE : View.GONE);
    }

    private void avisarQueDemorou() {
        demorou = true;
        textAcordando.setVisibility(blocoCarregando.getVisibility());
    }

    public void parar() {
        relogio.removeCallbacks(avisarDemora);
    }
}
