package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.venussystem.venusmobile.model.CategoriaContagem;
import com.venussystem.venusmobile.model.FaixaNota;
import com.venussystem.venusmobile.model.FiltroProdutos;
import com.venussystem.venusmobile.model.MarcaFiltro;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.FiltroRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tela do filtro. As escolhas ficam num rascunho e so valem para o app
 * quando a pessoa aperta Aplicar; a seta de voltar descarta o rascunho.
 *
 * As opcoes (categorias e marcas) saem do que existe no catalogo, e nao das
 * listas da API: opcao sem produto nenhum nunca aparece.
 */
public class FiltroViewModel extends AndroidViewModel {

    /**
     * Categoria com menos produtos que isso nao vira opcao: levaria a um
     * resultado so, ou a nenhum. A API tem 119 categorias mas so umas 36 sao
     * usadas por algum produto.
     */
    private static final int MINIMO_PRODUTOS_CATEGORIA = 3;

    private final ProdutoRepository repository;
    private final FiltroRepository filtros;

    private final MutableLiveData<FiltroProdutos> rascunho = new MutableLiveData<>();
    private final MutableLiveData<List<CategoriaContagem>> categorias = new MutableLiveData<>();
    private final MutableLiveData<List<MarcaFiltro>> marcas = new MutableLiveData<>();
    private final MutableLiveData<Boolean> temMarcaBrasileira = new MutableLiveData<>(false);

    private final Observer<List<Produto>> aoMudarCatalogo = this::montarOpcoes;

    public FiltroViewModel(@NonNull Application application) {
        this(application, new ProdutoRepository(), new FiltroRepository());
    }

    @VisibleForTesting
    FiltroViewModel(@NonNull Application application, ProdutoRepository repository,
                    FiltroRepository filtros) {
        super(application);
        this.repository = repository;
        this.filtros = filtros;

        rascunho.setValue(filtros.atual());
        repository.getCatalogo().observeForever(aoMudarCatalogo);
        repository.carregar(false);
    }

    @Override
    protected void onCleared() {
        repository.getCatalogo().removeObserver(aoMudarCatalogo);
    }

    public LiveData<FiltroProdutos> getRascunho() {
        return rascunho;
    }

    public LiveData<List<CategoriaContagem>> getCategorias() {
        return categorias;
    }

    public LiveData<List<MarcaFiltro>> getMarcas() {
        return marcas;
    }

    /** Sem nenhuma marca brasileira no catalogo, a opcao nao faz sentido. */
    public LiveData<Boolean> getTemMarcaBrasileira() {
        return temMarcaBrasileira;
    }

    public LiveData<Boolean> getCarregando() {
        return repository.getCarregando();
    }

    public boolean temCatalogo() {
        return repository.getCatalogo().getValue() != null;
    }

    public void alternarFaixa(FaixaNota faixa, boolean marcada) {
        rascunho.setValue(rascunhoAtual().comFaixa(faixa, marcada));
    }

    public void alternarCategoria(long id, boolean marcada) {
        rascunho.setValue(rascunhoAtual().comCategoria(id, marcada));
    }

    public void alternarMarca(long id, boolean marcada) {
        rascunho.setValue(rascunhoAtual().comMarca(id, marcada));
    }

    public void definirSoBrasileiras(boolean valor) {
        rascunho.setValue(rascunhoAtual().comSoBrasileiras(valor));
    }

    /** Zera o que esta marcado na tela; o filtro do app so muda em aplicar(). */
    public void limpar() {
        rascunho.setValue(FiltroProdutos.vazio());
    }

    public void aplicar() {
        filtros.aplicar(rascunhoAtual());
    }

    private FiltroProdutos rascunhoAtual() {
        FiltroProdutos valor = rascunho.getValue();
        return valor == null ? FiltroProdutos.vazio() : valor;
    }

    private void montarOpcoes(List<Produto> catalogo) {
        if (catalogo == null) {
            return;
        }

        Map<Long, String> nomeDaCategoria = new LinkedHashMap<>();
        Map<Long, Integer> contagem = new LinkedHashMap<>();
        Map<Long, String> nomeDaMarca = new LinkedHashMap<>();
        boolean brasileira = false;

        for (Produto p : catalogo) {
            Long categoriaId = p.getCategoryId();
            String categoriaNome = p.getCategoryName();
            if (categoriaId != null && categoriaNome != null && !categoriaNome.trim().isEmpty()) {
                nomeDaCategoria.put(categoriaId, categoriaNome);
                contagem.merge(categoriaId, 1, Integer::sum);
            }

            Long marcaId = p.getBrandId();
            String marcaNome = p.getBrandName();
            if (marcaId != null && marcaNome != null && !marcaNome.trim().isEmpty()) {
                nomeDaMarca.put(marcaId, marcaNome);
            }

            if (Boolean.TRUE.equals(p.getBrazilianBrand())) {
                brasileira = true;
            }
        }

        List<CategoriaContagem> listaCategorias = new ArrayList<>();
        for (Map.Entry<Long, Integer> entrada : contagem.entrySet()) {
            if (entrada.getValue() >= MINIMO_PRODUTOS_CATEGORIA) {
                listaCategorias.add(new CategoriaContagem(
                        entrada.getKey(), nomeDaCategoria.get(entrada.getKey()), entrada.getValue()));
            }
        }
        listaCategorias.sort(Comparator
                .comparingInt(CategoriaContagem::getQuantidade).reversed()
                .thenComparing(CategoriaContagem::getNome));

        List<MarcaFiltro> listaMarcas = new ArrayList<>();
        for (Map.Entry<Long, String> entrada : nomeDaMarca.entrySet()) {
            listaMarcas.add(new MarcaFiltro(entrada.getKey(), entrada.getValue()));
        }
        listaMarcas.sort(Comparator.comparing(MarcaFiltro::getNome, String.CASE_INSENSITIVE_ORDER));

        temMarcaBrasileira.setValue(brasileira);
        categorias.setValue(listaCategorias);
        marcas.setValue(listaMarcas);
    }
}
