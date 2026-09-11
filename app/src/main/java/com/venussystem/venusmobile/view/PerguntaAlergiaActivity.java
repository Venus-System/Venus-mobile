package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaAlergiaActivity extends PerguntaBuscaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_alergia;
    }

    @Override
    protected int getOpcoes() {
        return R.array.alergias;
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
}
