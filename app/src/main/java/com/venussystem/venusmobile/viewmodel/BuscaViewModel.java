package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.venussystem.venusmobile.model.FiltroProdutos;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.FiltroRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BuscaViewModel extends AndroidViewModel {

    private final ProdutoRepository repository;
    private final FiltroRepository filtros;

    private final MutableLiveData<List<Produto>> sugestoes = new MutableLiveData<>();
    private final MutableLiveData<List<Produto>> resultados = new MutableLiveData<>();

    private final Observer<List<Produto>> aoMudarCatalogo = catalogo -> recalcular();
    private final Observer<FiltroProdutos> aoMudarFiltro = filtro -> recalcular();

    private String termo = "";

    public BuscaViewModel(@NonNull Application application) {
        this(application, new ProdutoRepository(), new FiltroRepository());
    }

    @VisibleForTesting
    BuscaViewModel(@NonNull Application application, ProdutoRepository repository,
                   FiltroRepository filtros) {
        super(application);
        this.repository = repository;
        this.filtros = filtros;

        repository.getCatalogo().observeForever(aoMudarCatalogo);
        filtros.getFiltro().observeForever(aoMudarFiltro);
        repository.carregar(false);
    }

    @Override
    protected void onCleared() {
        repository.getCatalogo().removeObserver(aoMudarCatalogo);
        filtros.getFiltro().removeObserver(aoMudarFiltro);
    }

    public LiveData<List<Produto>> getSugestoes() {
        return sugestoes;
    }

    public LiveData<List<Produto>> getResultados() {
        return resultados;
    }

    public LiveData<FiltroProdutos> getFiltro() {
        return filtros.getFiltro();
    }

    public LiveData<Boolean> getCarregando() {
        return repository.getCarregando();
    }

    public LiveData<String> getErro() {
        return repository.getErro();
    }

    public boolean temCatalogo() {
        return repository.getCatalogo().getValue() != null;
    }

    public boolean isFiltroAtivo() {
        return filtros.atual().isAtivo();
    }

    public boolean isMostrandoHistorico() {
        return termo.trim().isEmpty();
    }

    public void recarregar() {
        repository.carregar(true);
    }

    public void buscar(String novoTermo) {
        termo = novoTermo == null ? "" : novoTermo;
        recalcular();
    }

    private void recalcular() {
        List<Produto> catalogo = repository.getCatalogo().getValue();
        if (catalogo == null) {
            return;
        }
        FiltroProdutos filtro = filtros.atual();

        List<Produto> filtrados = new ArrayList<>();
        for (Produto p : catalogo) {
            if (filtro.aceita(p)) {
                filtrados.add(p);
            }
        }

        List<Produto> porNota = new ArrayList<>(filtrados);
        porNota.sort(Comparator.comparing(Produto::getOverallScore,
                Comparator.nullsLast(Comparator.<Integer>reverseOrder())));
        sugestoes.setValue(porNota);

        if (isMostrandoHistorico()) {
            resultados.setValue(Collections.<Produto>emptyList());
            return;
        }

        String alvo = termo.trim().toLowerCase(Locale.getDefault());
        List<Produto> encontrados = new ArrayList<>();
        for (Produto p : filtrados) {
            if (contem(p.getName(), alvo) || contem(p.getBrandName(), alvo)) {
                encontrados.add(p);
            }
        }
        resultados.setValue(encontrados);
    }

    private boolean contem(String texto, String alvo) {
        return texto != null && texto.toLowerCase(Locale.getDefault()).contains(alvo);
    }
}
