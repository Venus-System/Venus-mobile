package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaCabeloActivity extends PerguntaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_cabelo;
    }

    @Override
    protected int getLayoutAjuda() {
        return R.layout.sheet_tipo_cabelo;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaCouroCabeludoActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.TIPO_CABELO;
    }
}
