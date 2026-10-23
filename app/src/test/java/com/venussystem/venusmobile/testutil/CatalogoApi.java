package com.venussystem.venusmobile.testutil;

import com.google.gson.Gson;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.BrandResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductCategoryResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;
import com.venussystem.venusmobile.repository.api.dto.ScoringModelResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Monta, para os testes, as seis respostas que o ProdutoRepository junta para
 * formar o catalogo (produtos, marcas, categorias, versoes, notas e modelo de
 * scoring ativo), sem repetir esse cadastro em cada classe de teste.
 *
 * Cada produto ganha uma versao atual; se tiver nota, ganha tambem a nota do
 * modelo ativo.
 */
public final class CatalogoApi {

    private static final long MODELO_ATIVO = 1L;
    private static final Gson GSON = new Gson();

    private final List<ProductResponse> produtos = new ArrayList<>();
    private final List<BrandResponse> marcas = new ArrayList<>();
    private final List<ProductCategoryResponse> categorias = new ArrayList<>();
    private final List<ProductVersionResponse> versoes = new ArrayList<>();
    private final List<ProductScoreResponse> notas = new ArrayList<>();

    public static CatalogoApi novo() {
        return new CatalogoApi();
    }

    public CatalogoApi marca(long id, String nome, Boolean brasileira) {
        BrandResponse r = new BrandResponse();
        r.id = id;
        r.name = nome;
        r.isBrazilian = brasileira;
        marcas.add(r);
        return this;
    }

    public CatalogoApi categoria(long id, String nome) {
        ProductCategoryResponse r = new ProductCategoryResponse();
        r.id = id;
        r.name = nome;
        categorias.add(r);
        return this;
    }

    /** @param nota null para um produto sem nota */
    public CatalogoApi produto(long id, long marcaId, Long categoriaId, String nome, Integer nota) {
        ProductResponse p = new ProductResponse();
        p.id = id;
        p.brandId = marcaId;
        p.productCategoryId = categoriaId;
        p.name = nome;
        p.isActive = true;
        produtos.add(p);

        long versaoId = 1000L + id;
        ProductVersionResponse v = new ProductVersionResponse();
        v.id = versaoId;
        v.productId = id;
        v.isCurrent = true;
        versoes.add(v);

        if (nota != null) {
            ProductScoreResponse n = new ProductScoreResponse();
            n.productVersionId = versaoId;
            n.scoringModelId = MODELO_ATIVO;
            n.overallScore = nota;
            notas.add(n);
        }
        return this;
    }

    public FakeApiDispatcher dispatcher() {
        ScoringModelResponse modelo = new ScoringModelResponse();
        modelo.id = MODELO_ATIVO;
        modelo.name = "Recomendacao Geral";
        modelo.isActive = true;

        FakeApiDispatcher d = new FakeApiDispatcher();
        d.em("/api/products", json(produtos));
        d.em("/api/brands", json(marcas));
        d.em("/api/product-categories", json(categorias));
        d.em("/api/product-versions", json(versoes));
        d.em("/api/product-scores", json(notas));
        d.em("/api/scoring-models/active", json(modelo));
        return d;
    }

    /** So a resposta da lista de produtos, para o teste atrasar ou trocar. */
    public MockResponse respostaProdutos() {
        return json(produtos);
    }

    public static MockResponse json(Object corpo) {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(GSON.toJson(corpo));
    }

    public static VenusApi criarApi(MockWebServer server) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build();
        return new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VenusApi.class);
    }
}
