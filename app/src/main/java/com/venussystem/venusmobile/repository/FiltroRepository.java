package com.venussystem.venusmobile.repository;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.model.FiltroProdutos;

/**
 * O filtro escolhido, compartilhado entre a Home, a busca e a escolha de
 * produto para uma lista: o que se marca numa tela continua valendo nas outras.
 *
 * Fica so em memoria, como o catalogo do ProdutoRepository: vale enquanto o
 * app esta aberto e comeca vazio na proxima vez.
 */
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
