package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.BrandResponse;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.MediaAssetResponse;
import com.venussystem.venusmobile.repository.api.dto.PreferenceCatalogResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductCategoryResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductFullResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductLabelResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductScoreResponse;
import com.venussystem.venusmobile.repository.api.dto.ProductVersionResponse;
import com.venussystem.venusmobile.repository.api.dto.ScanEnviadoResponse;
import com.venussystem.venusmobile.repository.api.dto.ScoringModelResponse;
import com.venussystem.venusmobile.repository.api.dto.UserAllergyRequest;
import com.venussystem.venusmobile.repository.api.dto.UserAllergyResponse;
import com.venussystem.venusmobile.repository.api.dto.UserListItemRequest;
import com.venussystem.venusmobile.repository.api.dto.UserListItemResponse;
import com.venussystem.venusmobile.repository.api.dto.UserListRequest;
import com.venussystem.venusmobile.repository.api.dto.UserListResponse;
import com.venussystem.venusmobile.repository.api.dto.UserPreferenceRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileTagRequest;
import com.venussystem.venusmobile.repository.api.dto.UserProfileTagResponse;
import com.venussystem.venusmobile.repository.api.dto.UserRequest;
import com.venussystem.venusmobile.repository.api.dto.UserResponse;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

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

    @GET("api/scoring-models/active")
    Call<ScoringModelResponse> modeloAtivo();

    @GET("api/products/{id}/full")
    Call<ProductFullResponse> buscarProdutoCompleto(@Path("id") long id);

    // ---- Usuario: liga o UID do Firebase ao id numerico da API ----

    @GET("api/users/search")
    Call<FatiaResponse<UserResponse>> buscarUsuarioPorUid(@Query("firebaseUid") String firebaseUid);

    @POST("api/users")
    Call<UserResponse> criarUsuario(@Body UserRequest usuario);

    // ---- Perfil do questionario ----
    // O PUT responde 404 quando a pessoa ainda nao tem perfil; ai vale o POST.
    // O corpo da resposta nao e usado, dai o Void.
    //
    // PUT e nao PATCH de proposito: o app manda sempre o perfil inteiro, e no
    // PUT um campo ausente vira "nao informado". No PATCH ele seria "nao mexe",
    // e quem trocasse "tenho rosacea" por "prefiro nao dizer" continuaria com
    // rosacea na API.

    @PUT("api/user-profiles/{userId}")
    Call<Void> atualizarPerfil(@Path("userId") long userId, @Body UserProfileRequest perfil);

    @POST("api/user-profiles")
    Call<Void> criarPerfil(@Body UserProfileRequest perfil);

    @PUT("api/user-preferences/{userId}")
    Call<Void> atualizarPreferencias(@Path("userId") long userId,
                                     @Body UserPreferenceRequest preferencias);

    @POST("api/user-preferences")
    Call<Void> criarPreferencias(@Body UserPreferenceRequest preferencias);

    // ---- Alergias ----
    // O catalogo e aberto; o que e de cada pessoa exige o token.

    @GET("api/allergies")
    Call<List<AllergyResponse>> listarAlergias();

    @GET("api/user-allergies/user/{userId}")
    Call<FatiaResponse<UserAllergyResponse>> alergiasDoUsuario(@Path("userId") long userId,
                                                               @Query("page") int pagina,
                                                               @Query("size") int tamanho);

    @POST("api/user-allergies")
    Call<Void> adicionarAlergia(@Body UserAllergyRequest alergia);

    @DELETE("api/user-allergies/user/{userId}/allergy/{allergyId}")
    Call<Void> removerAlergia(@Path("userId") long userId, @Path("allergyId") long allergyId);

    // ---- Etiquetas de preferencia (as que nao tem campo em user-preferences) ----

    @GET("api/profile-tags/preferences")
    Call<List<PreferenceCatalogResponse>> listarEtiquetasDePreferencia();

    @GET("api/user-profile-tags/user/{userId}")
    Call<FatiaResponse<UserProfileTagResponse>> etiquetasDoUsuario(@Path("userId") long userId,
                                                                   @Query("page") int pagina,
                                                                   @Query("size") int tamanho);

    @POST("api/user-profile-tags")
    Call<Void> adicionarEtiqueta(@Body UserProfileTagRequest etiqueta);

    @DELETE("api/user-profile-tags/user/{userId}/profile-tag/{profileTagId}")
    Call<Void> removerEtiqueta(@Path("userId") long userId,
                               @Path("profileTagId") long profileTagId);

    // ---- Listas da pessoa e os produtos dentro delas ----

    @POST("api/user-lists")
    Call<UserListResponse> criarLista(@Body UserListRequest lista);

    /** O corpo vem do UserListPatchRequest.paraEnviar(). */
    @PATCH("api/user-lists/{id}")
    Call<Void> atualizarLista(@Path("id") long id, @Body RequestBody lista);

    @DELETE("api/user-lists/{id}")
    Call<Void> apagarLista(@Path("id") long id);

    /** Uma capa nova substitui a anterior; a resposta traz o link da foto. */
    @Multipart
    @POST("api/user-lists/{id}/cover")
    Call<MediaAssetResponse> enviarCapaDaLista(@Path("id") long id,
                                               @Part MultipartBody.Part arquivo);

    @GET("api/user-lists/user/{userId}")
    Call<FatiaResponse<UserListResponse>> listasDoUsuario(@Path("userId") long userId,
                                                          @Query("page") int pagina,
                                                          @Query("size") int tamanho);

    @GET("api/user-list-items/user-list/{userListId}")
    Call<List<UserListItemResponse>> itensDaLista(@Path("userListId") long userListId);

    @POST("api/user-list-items")
    Call<Void> adicionarItem(@Body UserListItemRequest item);

    @DELETE("api/user-list-items/user-list/{userListId}/product/{productId}")
    Call<Void> removerItem(@Path("userListId") long userListId,
                           @Path("productId") long productId);

    // ---- Foto de perfil ----
    // O POST troca a foto anterior; a API sobe a imagem no Cloudinary e devolve o link.

    @Multipart
    @POST("api/users/{userId}/avatar")
    Call<Void> enviarAvatar(@Path("userId") long userId, @Part MultipartBody.Part arquivo);

    @GET("api/users/{userId}/avatar")
    Call<MediaAssetResponse> buscarAvatar(@Path("userId") long userId);

    // ---- Produtos que a pessoa escaneou e mandou para a equipe conferir ----
    // Do mais recente para o mais antigo. So devolve os scans da propria pessoa.

    @GET("api/scan-sessions/user/{userId}")
    Call<FatiaResponse<ScanEnviadoResponse>> scansDoUsuario(@Path("userId") long userId,
                                                            @Query("page") int pagina,
                                                            @Query("size") int tamanho);
}
