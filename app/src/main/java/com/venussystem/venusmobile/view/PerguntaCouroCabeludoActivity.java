package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaCouroCabeludoActivity extends PerguntaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_couro;
    }

    @Override
    protected int getLayoutAjuda() {
        return R.layout.sheet_couro;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaTipoPeleActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.COURO_CABELUDO;
    }
}
