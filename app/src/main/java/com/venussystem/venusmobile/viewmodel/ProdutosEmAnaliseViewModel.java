package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.model.ProdutoEmAnalise;
import com.venussystem.venusmobile.repository.ProdutosEmAnaliseRepository;

import java.util.List;

/**
 * Busca os produtos em analise ao abrir a tela. Girar o celular nao busca de
 * novo; "Tentar de novo" chama carregar() outra vez.
 */
public class ProdutosEmAnaliseViewModel extends AndroidViewModel {

    public enum Estado {
        CARREGANDO, PRONTO, ERRO
    }

    private final ProdutosEmAnaliseRepository repositorio;
    private final MutableLiveData<Estado> estado = new MutableLiveData<>();
    private final MutableLiveData<List<ProdutoEmAnalise>> produtos = new MutableLiveData<>();

    public ProdutosEmAnaliseViewModel(@NonNull Application application) {
        this(application, new ProdutosEmAnaliseRepository(application));
    }

    @VisibleForTesting
    ProdutosEmAnaliseViewModel(@NonNull Application application,
                               ProdutosEmAnaliseRepository repositorio) {
        super(application);
        this.repositorio = repositorio;
        carregar();
    }

    public LiveData<Estado> getEstado() {
        return estado;
    }

    public LiveData<List<ProdutoEmAnalise>> getProdutos() {
        return produtos;
    }

    public void carregar() {
        if (estado.getValue() == Estado.CARREGANDO) {
            return;
        }
        estado.setValue(Estado.CARREGANDO);
        repositorio.buscar(lista -> {
            if (lista == null) {
                estado.setValue(Estado.ERRO);
                return;
            }
            produtos.setValue(lista);
            estado.setValue(Estado.PRONTO);
        });
    }
}
