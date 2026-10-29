package com.venussystem.venusmobile.repository;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.model.FiltroProdutos;

public class FiltroRepository {

    private static final MutableLiveData<FiltroProdutos> FILTRO =
            new MutableLiveData<>(FiltroProdutos.vazio());

    public LiveData<FiltroProdutos> getFiltro() {
        return FILTRO;
    }

    @NonNull
    public FiltroProdutos atual() {
        FiltroProdutos valor = FILTRO.getValue();
        return valor == null ? FiltroProdutos.vazio() : valor;
    }

    public void aplicar(@NonNull FiltroProdutos filtro) {
        FILTRO.setValue(filtro);
    }

    public void limpar() {
        FILTRO.setValue(FiltroProdutos.vazio());
    }

    @VisibleForTesting
    public static void resetParaTeste() {
        FILTRO.postValue(FiltroProdutos.vazio());
    }
}
