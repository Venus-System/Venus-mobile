package com.venussystem.venusmobile.view;

import androidx.annotation.Nullable;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.PerfilRepository;

import java.util.Arrays;
import java.util.List;

public class PerguntaGestacaoActivity extends PerguntaMultiplaBaseActivity {
    @Override
    protected int getLayout() {
        return R.layout.activity_pergunta_gestacao;
    }

    @Override
    @Nullable
    protected Class<?> getProximaTela() {
        return PerguntaPreferenciaActivity.class;
    }

    @Override
    protected String getChave() {
        return PerfilRepository.GESTACAO;
    }

    @Override
    protected List<String> getTagsExclusivas() {
        return Arrays.asList(PerfilRepository.NENHUMA, PerfilRepository.PREFIRO_NAO_DIZER);
    }
}
