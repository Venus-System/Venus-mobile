package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaSensibilidadeActivity extends PerguntaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_sensibilidade;
    }

    @Override
    protected int getLayoutAjuda() {
        return R.layout.sheet_sensibilidade;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaCondicoesPeleActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.SENSIBILIDADE;
    }
}
