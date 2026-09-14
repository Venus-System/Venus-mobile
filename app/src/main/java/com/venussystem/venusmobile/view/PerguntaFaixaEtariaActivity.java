package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

public class PerguntaFaixaEtariaActivity extends PerguntaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_faixa_etaria;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaPreferenciaActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.FAIXA_ETARIA;
    }
}
