package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaPreferenciaActivity extends PerguntaBuscaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_preferencia;
    }

    @Override
    protected int getOpcoes() {
        return R.array.preferencias;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaAlergiaActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.PREFERENCIAS;
    }
}
