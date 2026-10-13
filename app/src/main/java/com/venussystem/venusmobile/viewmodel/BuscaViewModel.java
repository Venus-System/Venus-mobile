package com.venussystem.venusmobile.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.model.CategoriaContagem;
import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.BuscaRecenteRepository;
import com.venussystem.venusmobile.repository.ProdutoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BuscaViewModel extends AndroidViewModel {

    /**
     * Categoria com menos produtos que isso nao vira chip: seriam chips que
     * levam a um resultado so, ou a nenhum. A API tem 119 categorias mas so
     * umas 36 sao usadas por algum produto.
     */
    private static final int MINIMO_PRODUTOS_CATEGORIA = 3;

    private final ProdutoRepository repository;
    private final BuscaRecenteRepository recentes;

    private final MediatorLiveData<List<Produto>> produtos = new MediatorLiveData<>();
    private final MediatorLiveData<List<CategoriaContagem>> categorias = new MediatorLiveData<>();
    private final MutableLiveData<List<String>> termosRecentes = new MutableLiveData<>();

    private String termo = "";
    private Long categoriaSelecionada;

    public BuscaViewModel(@NonNull Application application) {
        this(application, new ProdutoRepository(), new BuscaRecenteRepository(application));
    }

    @VisibleForTesting
    BuscaViewModel(@NonNull Application application, ProdutoRepository repository,
                   BuscaRecenteRepository recentes) {
        super(application);
        this.repository = repository;
        this.recentes = recentes;

        // O catalogo inteiro ja vem para a memoria uma vez so, entao tanto o
        // filtro de texto quanto o de categoria rodam em RAM. Existe o endpoint
        // /api/products/search e ele funciona, mas ir na rede a cada tecla
        // exporia o usuario ao cold start do servidor gratuito.
        produtos.addSource(repository.getCatalogo(), catalogo -> aplicarFiltro());
        categorias.addSource(repository.getCatalogo(), this::montarCategorias);

        termosRecentes.setValue(recentes.getRecentes());
        repository.carregar(false);
    }

    public LiveData<List<Produto>> getProdutos() {
        return produtos;
    }

    public LiveData<List<CategoriaContagem>> getCategorias() {
        return categorias;
    }

    public LiveData<List<String>> getTermosRecentes() {
        return termosRecentes;
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

    /** Sem termo e sem categoria, a tela mostra recentes e categorias. */
    public boolean isLanding() {
        return termo.trim().isEmpty() && categoriaSelecionada == null;
    }

    @Nullable
    public Long getCategoriaSelecionada() {
        return categoriaSelecionada;
    }

    public void recarregar() {
        repository.carregar(true);
    }

    /** Caminho de cada tecla digitada: filtra, mas nao guarda nos recentes. */
    public void buscar(String novoTermo) {
        termo = novoTermo == null ? "" : novoTermo;
        aplicarFiltro();
    }

    /** Caminho do "buscar" do teclado: e aqui que o termo entra no historico. */
    public void confirmarBusca(String novoTermo) {
        buscar(novoTermo);
        registrarTermoAtual();
    }

    /**
     * Guarda o termo que esta valendo agora.
     *
     * Como a lista filtra a cada tecla, quase ninguem chega a apertar "buscar"
     * no teclado - entao so aquele caminho deixaria os recentes sempre vazios.
     * Abrir um produto a partir do resultado e o sinal de que aquela busca
     * serviu para alguma coisa, e vale tanto quanto.
     */
    public void registrarTermoAtual() {
        if (termo.trim().isEmpty()) {
            return;
        }
        recentes.registrar(termo);
        termosRecentes.setValue(recentes.getRecentes());
    }

    public void removerRecente(String termoRemovido) {
        recentes.remover(termoRemovido);
        termosRecentes.setValue(recentes.getRecentes());
    }

    public void selecionarCategoria(@Nullable Long id) {
        categoriaSelecionada = id;
        aplicarFiltro();
    }

    public void limparCategoria() {
        selecionarCategoria(null);
    }

    private void aplicarFiltro() {
        List<Produto> catalogo = repository.getCatalogo().getValue();
        if (catalogo == null) {
            return;
        }

        // No estado inicial a lista fica vazia de proposito: quem aparece e o
        // painel de recentes/categorias, nao o catalogo inteiro.
        if (isLanding()) {
            produtos.setValue(Collections.emptyList());
            return;
        }

        String alvo = termo.trim().toLowerCase(Locale.getDefault());

        List<Produto> encontrados = new ArrayList<>();
        for (Produto p : catalogo) {
            boolean bateTexto = alvo.isEmpty()
                    || contem(p.getName(), alvo)
                    || contem(p.getBrandName(), alvo);
            boolean bateCategoria = categoriaSelecionada == null
                    || categoriaSelecionada.equals(p.getCategoryId());

            if (bateTexto && bateCategoria) {
                encontrados.add(p);
            }
        }
        produtos.setValue(encontrados);
    }

    /**
     * Monta os chips a partir do que existe no catalogo, e nao da lista de
     * categorias da API - assim categoria sem produto nenhum nunca vira chip.
     */
    private void montarCategorias(List<Produto> catalogo) {
        if (catalogo == null) {
            return;
        }

        Map<Long, String> nomePorId = new LinkedHashMap<>();
        Map<Long, Integer> contagem = new LinkedHashMap<>();

        for (Produto p : catalogo) {
            Long id = p.getCategoryId();
            String nome = p.getCategoryName();
            if (id == null || nome == null || nome.trim().isEmpty()) {
                continue;
            }
            nomePorId.put(id, nome);
            contagem.merge(id, 1, Integer::sum);
        }

        List<CategoriaContagem> resultado = new ArrayList<>();
        for (Map.Entry<Long, Integer> entrada : contagem.entrySet()) {
            if (entrada.getValue() >= MINIMO_PRODUTOS_CATEGORIA) {
                resultado.add(new CategoriaContagem(
                        entrada.getKey(), nomePorId.get(entrada.getKey()), entrada.getValue()));
            }
        }

        resultado.sort(Comparator
                .comparingInt(CategoriaContagem::getQuantidade).reversed()
                .thenComparing(CategoriaContagem::getNome));

        categorias.setValue(resultado);
    }

    private boolean contem(String texto, String alvo) {
        return texto != null && texto.toLowerCase(Locale.getDefault()).contains(alvo);
    }
}
