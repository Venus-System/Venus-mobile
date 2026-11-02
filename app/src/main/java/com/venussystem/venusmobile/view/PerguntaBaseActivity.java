package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.view.util.SheetAjuda;

public abstract class PerguntaBaseActivity extends AppCompatActivity {

    private String respostaSelecionada;
    private String rotuloSelecionado;
    private PerfilRepository perfil;

    @LayoutRes
    protected abstract int getLayout();

    @Nullable
    protected abstract Class<?> getProximaTela();

    @LayoutRes
    protected int getLayoutAjuda() {
        return 0;
    }

    protected abstract String getChave();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, getLayout());

        perfil = new PerfilRepository(this);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnAvancar).setOnClickListener(v -> avancar());

        prepararOpcoes();
        prepararAjuda();
    }

    private void prepararAjuda() {
        View link = findViewById(R.id.textAjudaCabelo);
        if (link == null || getLayoutAjuda() == 0) {
            return;
        }
        link.setOnClickListener(v -> SheetAjuda.mostrar(this, getLayoutAjuda(), (conteudo, sheet) -> {
            LinearLayout lista = findViewById(R.id.listaOpcoes);
            for (int i = 0; i < lista.getChildCount(); i++) {
                View opcao = lista.getChildAt(i);
                View cartao = conteudo.findViewWithTag(opcao.getTag());
                if (cartao != null) {
                    cartao.setOnClickListener(x -> {
                        selecionar(lista, opcao);
                        sheet.dismiss();
                    });
                }
            }
        }));
    }

    private void prepararOpcoes() {
        LinearLayout lista = findViewById(R.id.listaOpcoes);
        String jaEscolhido = perfil.getTag(getChave());

        for (int i = 0; i < lista.getChildCount(); i++) {
            View opcao = lista.getChildAt(i);
            opcao.setOnClickListener(v -> selecionar(lista, v));

            if (jaEscolhido != null && jaEscolhido.equals(String.valueOf(opcao.getTag()))) {
                selecionar(lista, opcao);
            }
        }
    }

    private void selecionar(LinearLayout lista, View escolhida) {
        for (int i = 0; i < lista.getChildCount(); i++) {
            lista.getChildAt(i).setSelected(false);
        }
        escolhida.setSelected(true);
        respostaSelecionada = String.valueOf(escolhida.getTag());

        rotuloSelecionado = escolhida instanceof TextView
                ? ((TextView) escolhida).getText().toString()
                : respostaSelecionada;
    }

    private void avancar() {
        if (respostaSelecionada == null) {
            Toast.makeText(this, R.string.pergunta_escolha_uma, Toast.LENGTH_SHORT).show();
            return;
        }

        perfil.salvarResposta(getChave(), respostaSelecionada, rotuloSelecionado);

        if (FluxoPerguntas.avulso(getIntent())) {
            FluxoPerguntas.seguir(this, getChave());
            return;
        }

        Class<?> proxima = getProximaTela();
        if (proxima != null) {
            startActivity(new Intent(this, proxima));
            return;
        }

        perfil.marcarQuestionarioRespondido();
        NavegacaoPosLogin.irParaMenu(this);
    }

    protected String getRespostaSelecionada() {
        return respostaSelecionada;
    }
}
