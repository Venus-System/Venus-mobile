package com.venussystem.venusmobile.repository.api;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Public, read-only PostgreSQL catalog endpoints. No Firebase token is needed. */
public interface ScanIngredientApi {
    @GET("api/ingredients/search")
    Call<Page<Ingredient>> ingredients(@Query("page") int page, @Query("size") int size,
                                      @Query("sort") String sort);
    @GET("api/ingredients/inci-name/{name}")
    Call<Ingredient> ingredientByInci(@Path("name") String name);
    @GET("api/ingredients/{id}")
    Call<Ingredient> ingredientById(@Path("id") long id);
    @GET("api/ingredient-aliases/search")
    Call<Page<Alias>> aliases(@Query("page") int page, @Query("size") int size,
                             @Query("sort") String sort);
    @GET("api/ingredient-aliases/alias-name/{name}")
    Call<Alias> aliasByName(@Path("name") String name);

    class Page<T> { public List<T> content; public Boolean last; public Integer number; }
    class Ingredient { public Long id; public String inciName; }
    class Alias { public Long id, ingredientId; public String aliasName; }
}
