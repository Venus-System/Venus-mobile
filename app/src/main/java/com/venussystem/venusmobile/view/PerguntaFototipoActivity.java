package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaFototipoActivity extends PerguntaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_fototipo;
    }

    @Override
    protected int getLayoutAjuda() {
        return R.layout.sheet_fototipo;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaFaixaEtariaActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.FOTOTIPO;
    }
}
