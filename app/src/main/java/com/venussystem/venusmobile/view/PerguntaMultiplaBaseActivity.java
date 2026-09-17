package com.venussystem.venusmobile.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.ArrayList;
import java.util.List;

public abstract class PerguntaMultiplaBaseActivity extends AppCompatActivity {

    private final List<String> selecionados = new ArrayList<>();
    private PerfilRepository perfil;
    private LinearLayout lista;

    @LayoutRes
    protected abstract int getLayout();

    @Nullable
    protected abstract Class<?> getProximaTela();

    /** Onde a lista desta tela e guardada. Ver PerfilRepository. */
    protected abstract String getChave();

    /** Opcoes que nao convivem com outras: marcar uma delas desmarca o resto. */
    protected abstract List<String> getTagsExclusivas();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(getLayout());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        perfil = new PerfilRepository(this);
        lista = findViewById(R.id.listaOpcoes);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnAvancar).setOnClickListener(v -> avancar());

        selecionados.addAll(perfil.getLista(getChave()));
        prepararOpcoes();
    }

    private void prepararOpcoes() {
        for (int i = 0; i < lista.getChildCount(); i++) {
            View opcao = lista.getChildAt(i);
            String tag = String.valueOf(opcao.getTag());
            opcao.setOnClickListener(v -> alternar(tag));
        }
        marcarSelecionadas();
    }

    private void alternar(String tag) {
        if (selecionados.contains(tag)) {
            selecionados.remove(tag);
        } else if (getTagsExclusivas().contains(tag)) {
            selecionados.clear();
            selecionados.add(tag);
        } else {
            selecionados.removeAll(getTagsExclusivas());
            selecionados.add(tag);
        }
        marcarSelecionadas();
    }

    private void marcarSelecionadas() {
        for (int i = 0; i < lista.getChildCount(); i++) {
            View opcao = lista.getChildAt(i);
            opcao.setSelected(selecionados.contains(String.valueOf(opcao.getTag())));
        }
    }

    private void avancar() {
        // Lista vazia aqui significaria "nao respondeu": quem nao tem nenhuma
        // das condicoes marca a opcao propria para isso, que fica salva.
        if (selecionados.isEmpty()) {
            Toast.makeText(this, R.string.pergunta_escolha_ao_menos_uma, Toast.LENGTH_SHORT).show();
            return;
        }

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
