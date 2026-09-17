package com.venussystem.venusmobile.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ColecaoRepository {
    private static final List<Colecao> MINHAS = Arrays.asList(
            new Colecao(1L, "Produtos favoritados", R.drawable.capa_favoritos),
            new Colecao(2L, "Produtos escaneados", R.drawable.capa_escaneados),
            new Colecao(3L, "Rotina de skincare", R.drawable.capa_skincare)
    );

    // "Seguindo" e sobre listas PUBLICAS de outros usuarios - e um conceito que
    // ainda nao existe no backend (nao ha endpoint que marque uma lista como
    // publica nem que devolva listas de outras pessoas). Comeca vazia de
    // proposito, sem exemplo inventado. Quando esse endpoint existir, aqui e
    // que ele entra.
    private static final List<Colecao> SEGUINDO = Collections.emptyList();

    public LiveData<List<Colecao>> minhasListas() {
        return new MutableLiveData<>(MINHAS);
    }

    public LiveData<List<Colecao>> seguindo() {
        return new MutableLiveData<>(SEGUINDO);
    }
}
