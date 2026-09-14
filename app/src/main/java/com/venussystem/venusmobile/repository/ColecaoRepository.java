package com.venussystem.venusmobile.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.model.Colecao;

import java.util.Arrays;
import java.util.List;

public class ColecaoRepository {
    private static final List<Colecao> MINHAS = Arrays.asList(
            new Colecao(1L, "Produtos favoritados", R.drawable.capa_favoritos),
            new Colecao(2L, "Produtos escaneados", R.drawable.capa_escaneados),
            new Colecao(3L, "Rotina de skincare", R.drawable.capa_skincare)
    );

    private static final List<Colecao> SEGUINDO = Arrays.asList(
            new Colecao(5L, "Produtos de pele", R.drawable.mini_produtos_pele, "Venus"),
            new Colecao(6L, "Fragrância Abacate", R.drawable.mini_abacate, "AvocadoLove"),
            new Colecao(7L, "Cruelty-Free Products", R.drawable.mini_cruelty_free, "Peter"),
            new Colecao(8L, "Produtos contra ACNE", R.drawable.mini_acne, "Jane132"),
            new Colecao(9L, "Meus Favoritos!", R.drawable.mini_favoritos, "JamieJohn"),
            new Colecao(10L, "Laranjas", R.drawable.mini_laranjas, "Oranje"),
            new Colecao(11L, "HairDoe", R.drawable.mini_cabelo, "Jane132"),
            new Colecao(12L, "Coco nutnut", R.drawable.mini_coco, "IsAGiantNut")
    );

    public LiveData<List<Colecao>> minhasListas() {
        return new MutableLiveData<>(MINHAS);
    }

    public LiveData<List<Colecao>> seguindo() {
        return new MutableLiveData<>(SEGUINDO);
    }
}
