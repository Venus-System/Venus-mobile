package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.BrandResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductCategoryResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductLabelResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface VenusApi {

    @GET("api/products")
    Call<List<ProductResponse>> listarProdutos();

    @GET("api/products/{id}")
    Call<ProductResponse> buscarProduto(@Path("id") long id);

    @GET("api/brands")
    Call<List<BrandResponse>> listarMarcas();

    @GET("api/product-categories")
    Call<List<ProductCategoryResponse>> listarCategorias();

    @GET("api/product-versions")
    Call<List<ProductVersionResponse>> listarVersoes();

    @GET("api/product-versions/product/{productId}/current")
    Call<ProductVersionResponse> versaoAtual(@Path("productId") long productId);

    @GET("api/product-scores")
    Call<List<ProductScoreResponse>> listarNotas();

    @GET("api/product-labels/product-version/{productVersionId}")
    Call<ProductLabelResponse> buscarRotulo(@Path("productVersionId") long productVersionId);
}
