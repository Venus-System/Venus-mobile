package com.venussystem.venusmobile.view.componente;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.domain.perfil.ProgressoPerfil;
import com.venussystem.venusmobile.view.PesquisaInicialActivity;

public class CartaoPerfilView extends LinearLayout {

    private final ProgressBar barra;
    private final TextView subtitulo;
    private final AppCompatButton botao;

    public CartaoPerfilView(Context context) {
        this(context, null);
    }

    public CartaoPerfilView(Context context, @Nullable AttributeSet atributos) {
        super(context, atributos);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_cartao_perfil_venus);
        int margem = getResources().getDimensionPixelSize(R.dimen.padding_cartao_perfil);
        setPadding(margem, margem, margem, margem);
        LayoutInflater.from(context).inflate(R.layout.view_cartao_perfil, this, true);

        barra = findViewById(R.id.barraCartaoPerfil);
        subtitulo = findViewById(R.id.subtituloCartaoPerfil);
        botao = findViewById(R.id.botaoCartaoPerfil);
        botao.setOnClickListener(v -> abrirQuestionario());
    }

    public void atualizar(ProgressoPerfil progresso) {
        if (progresso.estado == ProgressoPerfil.Estado.COMPLETO) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);

        if (progresso.estado == ProgressoPerfil.Estado.SEM_QUESTIONARIO) {
            barra.setVisibility(GONE);
            subtitulo.setText(R.string.cartao_perfil_sub_sem_questionario);
            botao.setText(R.string.cartao_perfil_responder);
        } else {
            barra.setVisibility(VISIBLE);
            barra.setProgress(progresso.porcentagem);
            barra.setContentDescription(getContext().getString(
                    R.string.cartao_perfil_progresso_desc, progresso.porcentagem));
            subtitulo.setText(getContext().getString(
                    R.string.cartao_perfil_sub_incompleto, 100 - progresso.porcentagem));
            botao.setText(R.string.cartao_perfil_completar);
        }
    }

    private void abrirQuestionario() {
        Context contexto = getContext();
        Intent intent = new Intent(contexto, PesquisaInicialActivity.class);
        if (!(contexto instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        contexto.startActivity(intent);
    }

    public boolean estaVisivel() {
        return getVisibility() == View.VISIBLE;
    }
}
