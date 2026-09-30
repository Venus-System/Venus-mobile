package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.ArrayList;
import java.util.List;

public abstract class PerguntaBuscaBaseActivity extends AppCompatActivity {

    private final List<String> selecionados = new ArrayList<>();
    private LinearLayout lista;
    private PerfilRepository perfil;
    private AppCompatAutoCompleteTextView campo;

    @LayoutRes
    protected abstract int getLayout();

    /** As opcoes do campo de busca quando a tela abre. */
    protected abstract List<String> getOpcoes();

    @Nullable
    protected abstract Class<?> getProximaTela();

    /** Onde a lista desta tela e guardada. Ver PerfilRepository. */
    protected abstract String getChave();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsSistema.aplicar(this, getLayout());

        lista = findViewById(R.id.listaSelecionados);
        perfil = new PerfilRepository(this);

        // Se ja havia algo salvo para esta pergunta, os chips vem pre-montados.
        for (String salvo : perfil.getLista(getChave())) {
            adicionar(salvo);
        }

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnAvancar).setOnClickListener(v -> avancar());

        prepararBusca();
    }

    private void prepararBusca() {
        campo = findViewById(R.id.campoBusca);
        trocarOpcoes(getOpcoes());

        campo.setOnItemClickListener((parent, view, posicao, id) -> {
            adicionar((String) parent.getItemAtPosition(posicao));
            campo.setText("");
        });
    }

    /**
     * Para opcoes que chegam depois da tela aberta (as alergias vem da API).
     * Os chips ja escolhidos ficam como estao.
     */
    protected void trocarOpcoes(List<String> opcoes) {
        campo.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, opcoes));
    }

    private void adicionar(String item) {
        if (selecionados.contains(item)) {
            return;
        }
        selecionados.add(item);

        View chip = getLayoutInflater().inflate(R.layout.item_chip_selecionado, lista, false);
        ((TextView) chip.findViewById(R.id.textChip)).setText(item);
        chip.findViewById(R.id.btnRemoverChip).setOnClickListener(v -> {
            selecionados.remove(item);
            lista.removeView(chip);
        });
        lista.addView(chip);
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
