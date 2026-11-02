package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.Collections;
import java.util.List;

public class PerguntaCondicoesPeleActivity extends PerguntaMultiplaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_condicoes_pele;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaFototipoActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.CONDICOES_PELE;
    }

    @Override
    protected List<String> getTagsExclusivas() {
        return Collections.singletonList(PerfilRepository.NENHUMA);
    }
}
