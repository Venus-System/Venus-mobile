package com.venussystem.venusmobile.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.venussystem.venusmobile.model.Produto;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.BrandResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductCategoryResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductLabelResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

public class ProdutoRepository {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // As chamadas do catalogo sao independentes, entao vao juntas neste pool em
    // vez de uma esperando a outra.
    private static final ExecutorService REDE = Executors.newFixedThreadPool(5);

    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    // O catalogo e o mesmo para o app inteiro, entao guardamos em memoria: sair
    // da aba de busca e voltar nao pode disparar tudo de novo.
    private static final MutableLiveData<List<Produto>> CATALOGO = new MutableLiveData<>();
    private static boolean carregado = false;

    // Estado compartilhado: o app aquece o catalogo ainda na tela de boas-vindas,
    // e quando a busca abre ela precisa enxergar essa mesma carga - senao dispara
    // uma segunda igual e a tela mostra "pronto" com a lista ainda vazia.
    private static final MutableLiveData<Boolean> CARREGANDO = new MutableLiveData<>(false);
    private static final MutableLiveData<String> ERRO = new MutableLiveData<>();
    private static final AtomicBoolean EM_ANDAMENTO = new AtomicBoolean(false);

    private final VenusApi api;

    public ProdutoRepository() {
        this(ClienteApi.get());
    }

    @VisibleForTesting
    public ProdutoRepository(VenusApi api) {
        this.api = api;
    }

    @VisibleForTesting
    public static void resetEstadoParaTeste() {
        CATALOGO.postValue(null);
        carregado = false;
        CARREGANDO.postValue(false);
        ERRO.postValue(null);
        EM_ANDAMENTO.set(false);
    }

    public interface AoObterTexto {
        void aoConcluir(@Nullable String texto);
    }

    public LiveData<List<Produto>> getCatalogo() {
        return CATALOGO;
    }

    public LiveData<Boolean> getCarregando() {
        return CARREGANDO;
    }

    public LiveData<String> getErro() {
        return ERRO;
    }

    public void carregar(boolean forcar) {
        if (carregado && !forcar) {
            return;
        }
        if (!EM_ANDAMENTO.compareAndSet(false, true)) {
            return;
        }
        CARREGANDO.postValue(true);
        ERRO.postValue(null);

        EXECUTOR.execute(() -> {
            try {
                List<Produto> lista = montarCatalogo();
                carregado = true;
                CATALOGO.postValue(lista);
            } catch (FalhaApi falha) {
                ERRO.postValue(falha.getMessage());
            } catch (IOException e) {
                ERRO.postValue("Nao foi possivel falar com o servidor. "
                        + "Verifique sua conexao e tente de novo.");
            } finally {
                CARREGANDO.postValue(false);
                EM_ANDAMENTO.set(false);
            }
        });
    }

    /**
     * Procura um produto no catalogo ja carregado, sem ir na rede. A tela de
     * detalhe so e aberta a partir de uma lista que ja mostrou o produto na
     * tela, entao o catalogo sempre ja esta em memoria nesse ponto.
     */
    @Nullable
    public Produto buscarNoCache(long produtoId) {
        List<Produto> lista = CATALOGO.getValue();
        if (lista == null) {
            return null;
        }
        for (Produto produto : lista) {
            if (produto.getId() != null && produto.getId() == produtoId) {
                return produto;
            }
        }
        return null;
    }

    /**
     * Busca o texto de composicao (rotulo) do produto: primeiro acha a versao
     * atual dele, depois o rotulo daquela versao. E uma tela isolada, nao o
     * catalogo compartilhado, entao roda em uma chamada avulsa e devolve pelo
     * callback - null quando a API nao tiver o dado ou estiver fora do ar.
     */
    public void buscarIngredientes(long produtoId, AoObterTexto callback) {
        REDE.execute(() -> {
            String texto = obterTextoDoRotulo(produtoId);
            PRINCIPAL.post(() -> callback.aoConcluir(texto));
        });
    }

    @Nullable
    private String obterTextoDoRotulo(long produtoId) {
        try {
            Response<ProductVersionResponse> respostaVersao =
                    api.versaoAtual(produtoId).execute();
            if (!respostaVersao.isSuccessful() || respostaVersao.body() == null) {
                return null;
            }

            Response<ProductLabelResponse> respostaRotulo =
                    api.buscarRotulo(respostaVersao.body().id).execute();
            if (!respostaRotulo.isSuccessful() || respostaRotulo.body() == null) {
                return null;
            }

            return respostaRotulo.body().normalizedText;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A lista de produtos da API traz so o brandId/categoryId e nao traz nota
     * nenhuma. Para montar o card do jeito que a tela precisa (nome, marca,
     * categoria e nota) juntamos cinco chamadas: produtos, marcas, categorias,
     * versoes e notas.
     *
     * A nota mora na versao atual do produto, nao no produto: por isso o
     * caminho e produto -> versao atual -> nota.
     *
     * Nenhuma depende do resultado da outra, entao as cinco sao disparadas de
     * uma vez: o custo passa a ser o da chamada mais lenta, e nao a soma delas.
     */
    private List<Produto> montarCatalogo() throws IOException, FalhaApi {
        Future<List<ProductResponse>> pedidoProdutos =
                REDE.submit(() -> exigir(api.listarProdutos().execute(), "produtos"));
        Future<List<BrandResponse>> pedidoMarcas =
                REDE.submit(() -> exigir(api.listarMarcas().execute(), "marcas"));
        Future<List<ProductCategoryResponse>> pedidoCategorias =
                REDE.submit(() -> exigir(api.listarCategorias().execute(), "categorias"));
        Future<List<ProductVersionResponse>> pedidoVersoes =
                REDE.submit(() -> exigir(api.listarVersoes().execute(), "versoes"));
        Future<List<ProductScoreResponse>> pedidoNotas =
                REDE.submit(() -> exigir(api.listarNotas().execute(), "notas"));

        List<ProductResponse> produtos = esperar(pedidoProdutos);
        List<BrandResponse> marcas = esperar(pedidoMarcas);
        List<ProductCategoryResponse> categorias = esperar(pedidoCategorias);
        List<ProductVersionResponse> versoes = esperar(pedidoVersoes);
        List<ProductScoreResponse> notas = esperar(pedidoNotas);

        Map<Long, String> nomeDaMarca = new HashMap<>();
        for (BrandResponse marca : marcas) {
            nomeDaMarca.put(marca.id, marca.name);
        }

        Map<Long, String> nomeDaCategoria = new HashMap<>();
        for (ProductCategoryResponse categoria : categorias) {
            nomeDaCategoria.put(categoria.id, categoria.name);
        }

        Map<Long, Long> versaoAtualDoProduto = new HashMap<>();
        for (ProductVersionResponse versao : versoes) {
            if (Boolean.TRUE.equals(versao.isCurrent)) {
                versaoAtualDoProduto.put(versao.productId, versao.id);
            }
        }

        Map<Long, Integer> notaDaVersao = new HashMap<>();
        for (ProductScoreResponse nota : notas) {
            notaDaVersao.put(nota.productVersionId, nota.overallScore);
        }

        List<Produto> catalogo = new ArrayList<>();
        for (ProductResponse produto : produtos) {
            if (Boolean.FALSE.equals(produto.isActive)) {
                continue;
            }

            Long versaoAtual = versaoAtualDoProduto.get(produto.id);
            Integer nota = versaoAtual == null ? null : notaDaVersao.get(versaoAtual);

            String marca = nomeDaMarca.get(produto.brandId);
            if (marca == null) {
                marca = "";
            }

            // A API ainda nao tem campo de imagem, entao o item cai no
            // placeholder do adapter ate o Cloudinary entrar no contrato.
            catalogo.add(new Produto(produto.id, produto.name, marca, nota, null,
                    produto.productCategoryId, nomeDaCategoria.get(produto.productCategoryId)));
        }
        return catalogo;
    }

    /**
     * Devolve o resultado da chamada preservando o motivo da falha: quem chama
     * precisa distinguir "a API respondeu erro" de "nao deu para chegar nela".
     */
    private <T> List<T> esperar(Future<List<T>> pedido) throws IOException, FalhaApi {
        try {
            return pedido.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("A busca foi interrompida.", e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof FalhaApi) {
                throw (FalhaApi) causa;
            }
            if (causa instanceof IOException) {
                throw (IOException) causa;
            }
            throw new IOException(causa);
        }
    }

    private <T> List<T> exigir(Response<List<T>> resposta, String oQue)
            throws FalhaApi {
        if (!resposta.isSuccessful()) {
            throw new FalhaApi("A API respondeu " + resposta.code()
                    + " ao buscar " + oQue + ".");
        }
        List<T> corpo = resposta.body();
        return corpo == null ? Collections.emptyList() : corpo;
    }

    private static class FalhaApi extends Exception {
        FalhaApi(String mensagem) {
            super(mensagem);
        }
    }
}
