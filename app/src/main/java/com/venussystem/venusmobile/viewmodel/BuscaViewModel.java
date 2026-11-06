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
import com.venussystem.venusmobile.model.ProdutoSecao;
import com.venussystem.venusmobile.repository.FiltroRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BuscaViewModel extends AndroidViewModel {

    private final ProdutoRepository repository;
    private final FiltroRepository filtros;

    private final MutableLiveData<List<Produto>> sugestoes = new MutableLiveData<>();
    private final MutableLiveData<List<ProdutoSecao>> sugestoesPorCategoria = new MutableLiveData<>();
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

    public LiveData<List<ProdutoSecao>> getSugestoesPorCategoria() {
        return sugestoesPorCategoria;
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

        List<Produto> porNota = ordenarProdutosSemDuplicatas(filtrados);
        sugestoes.setValue(porNota);
        sugestoesPorCategoria.setValue(agruparPorCategoria(filtrados));

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

    /**
     * Agrupa somente depois do filtro e remove repetições do catálogo pelo ID do produto.
     * A lista resultante mantém uma ordenação determinística dentro de cada seção.
     */
    @VisibleForTesting
    static List<ProdutoSecao> agruparPorCategoria(List<Produto> produtos) {
        Map<String, Grupo> grupos = new LinkedHashMap<>();
        Set<Long> idsVistos = new LinkedHashSet<>();
        Set<Produto> semIdVistos = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (Produto produto : produtos) {
            if (produto == null) {
                continue;
            }
            Long id = produto.getId();
            if (id != null ? !idsVistos.add(id) : !semIdVistos.add(produto)) {
                continue;
            }

            String nome = nomeDaCategoria(produto);
            String chave = chaveDaCategoria(produto, nome);
            Grupo grupo = grupos.get(chave);
            if (grupo == null) {
                grupo = new Grupo(nome, semCategoria(produto));
                grupos.put(chave, grupo);
            }
            grupo.produtos.add(produto);
        }

        List<Grupo> ordenados = new ArrayList<>(grupos.values());
        ordenados.sort(Comparator
                .comparing((Grupo grupo) -> grupo.semCategoria ? 1 : 0)
                .thenComparing((Grupo grupo) -> grupo.produtos.size(), Comparator.reverseOrder())
                .thenComparing(grupo -> grupo.nome, String.CASE_INSENSITIVE_ORDER));

        List<ProdutoSecao> secoes = new ArrayList<>();
        for (Grupo grupo : ordenados) {
            grupo.produtos.sort(comparadorDeProduto());
            secoes.add(new ProdutoSecao(grupo.nome, grupo.produtos));
        }
        return secoes;
    }

    private static List<Produto> ordenarProdutosSemDuplicatas(List<Produto> produtos) {
        List<Produto> unicos = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>();
        for (Produto produto : produtos) {
            if (produto == null || (produto.getId() != null && !ids.add(produto.getId()))) {
                continue;
            }
            unicos.add(produto);
        }
        unicos.sort(Comparator.comparing(Produto::getOverallScore,
                Comparator.nullsLast(Comparator.<Integer>reverseOrder())));
        return unicos;
    }

    private static Comparator<Produto> comparadorDeProduto() {
        return Comparator.comparing(Produto::getOverallScore,
                        Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
                .thenComparing(Produto::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private static String nomeDaCategoria(Produto produto) {
        String nome = produto.getCategoryName();
        return nome == null || nome.trim().isEmpty() ? "Sem categoria" : nome.trim();
    }

    private static String chaveDaCategoria(Produto produto, String nome) {
        if (produto.getCategoryId() != null) {
            return "id:" + produto.getCategoryId();
        }
        return "nome:" + nome.toLowerCase(Locale.ROOT);
    }

    private static final class Grupo {
        final String nome;
        final boolean semCategoria;
        final List<Produto> produtos = new ArrayList<>();

        Grupo(String nome, boolean semCategoria) {
            this.nome = nome;
            this.semCategoria = semCategoria;
        }
    }

    private static boolean semCategoria(Produto produto) {
        return produto.getCategoryId() == null
                && (produto.getCategoryName() == null
                || produto.getCategoryName().trim().isEmpty());
    }
}
