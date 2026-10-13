package com.venussystem.venusmobile.view;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.CatalogoAlergiasRepository;
import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.List;

public class PerguntaAlergiaActivity extends PerguntaBuscaBaseActivity {

    private CatalogoAlergiasRepository catalogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // A tela abre com o ultimo catalogo guardado (ou a lista reserva) e
        // troca pelo da API quando ele chegar.
        catalogo().atualizarEmSegundoPlano(nomes -> {
            if (!isDestroyed()) {
                trocarOpcoes(nomes);
            }
        });
    }

    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_alergia;
    }

    @Override
    protected List<String> getOpcoes() {
        return catalogo().nomes();
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerfilProntoActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.ALERGIAS;
    }

    // getOpcoes() e chamado de dentro do super.onCreate, antes de qualquer
    // linha do onCreate desta classe, dai o repositorio criado sob demanda.
    private CatalogoAlergiasRepository catalogo() {
        if (catalogo == null) {
            catalogo = new CatalogoAlergiasRepository(this);
        }
        return catalogo;
    }
}
