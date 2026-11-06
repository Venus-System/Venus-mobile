package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;
import com.venussystem.venusmobile.view.componente.SeletorDeOpcoes;

import java.util.ArrayList;
import java.util.List;

public abstract class PerguntaBuscaBaseActivity extends AppCompatActivity {

    private final List<String> selecionados = new ArrayList<>();
    private LinearLayout lista;
    private PerfilRepository perfil;
    private SeletorDeOpcoes seletor;
    private boolean avancando;

    @LayoutRes
    protected abstract int getLayout();

    protected abstract List<String> getOpcoes();

    @StringRes
    protected abstract int getHintOutro();

    @Nullable
    protected abstract Class<?> getProximaTela();

    protected abstract String getChave();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, getLayout());

        lista = findViewById(R.id.listaSelecionados);
        perfil = new PerfilRepository(this);

        selecionados.addAll(perfil.getLista(getChave()));
        desenharChips();

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnAvancar).setOnClickListener(v -> {
            if (avancando) {
                return;
            }
            avancando = true;
            v.setEnabled(false);
            avancar();
        });

        seletor = new SeletorDeOpcoes(findViewById(R.id.campoBusca),
                findViewById(R.id.blocoOutro), getHintOutro(), getOpcoes(), this::adicionar);
    }

    protected void trocarOpcoes(List<String> opcoes) {
        seletor.trocarOpcoes(opcoes);
    }

    private void adicionar(String item) {
        List<String> novos = SeletorDeOpcoes.aplicar(selecionados, item);
        selecionados.clear();
        selecionados.addAll(novos);
        perfil.salvarLista(getChave(), selecionados);
        desenharChips();
    }

    private void desenharChips() {
        lista.removeAllViews();
        for (String item : selecionados) {
            View chip = getLayoutInflater().inflate(R.layout.item_chip_selecionado, lista, false);
            ((TextView) chip.findViewById(R.id.textChip))
                    .setText(SeletorDeOpcoes.rotulo(this, item));
            chip.findViewById(R.id.btnRemoverChip).setOnClickListener(v -> {
                selecionados.remove(item);
                perfil.salvarLista(getChave(), selecionados);
                desenharChips();
            });
            lista.addView(chip);
        }
    }

    private void avancar() {
        perfil.salvarLista(getChave(), selecionados);

        Class<?> proxima = getProximaTela();
        if (proxima != null) {
            startActivity(new Intent(this, proxima));
            return;
        }

        perfil.marcarQuestionarioRespondido();
        NavegacaoPosLogin.irParaMenu(this);
    }

    protected List<String> getSelecionados() {
        return selecionados;
    }
}
