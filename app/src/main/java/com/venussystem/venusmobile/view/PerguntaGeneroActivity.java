package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaGeneroActivity extends PerguntaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_genero;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaCabeloActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.GENERO;
    }
}
