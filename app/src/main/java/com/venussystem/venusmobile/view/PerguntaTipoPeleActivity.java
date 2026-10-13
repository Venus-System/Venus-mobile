package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaTipoPeleActivity extends PerguntaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_tipo_pele;
    }

    @Override
    protected int getLayoutAjuda() {
        return R.layout.sheet_tipo_pele;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaSensibilidadeActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.TIPO_PELE;
    }
}
