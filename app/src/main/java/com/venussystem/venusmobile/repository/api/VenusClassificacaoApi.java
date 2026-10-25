package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.ClassificationRequest;
import com.venussystem.venusmobile.repository.api.dto.ClassificationResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Rotas da Venus-Classificacao (nota do produto para o perfil da pessoa). As
 * duas exigem o token do Firebase do dono do userId.
 */
public interface VenusClassificacaoApi {

    /** Ultima analise salva da versao atual do produto; 404 quando nao ha nenhuma. */
    @GET("api/classifications/user/{userId}/product/{productId}")
    Call<ClassificationResponse> ultimaClassificacao(@Path("userId") long userId,
                                                     @Path("productId") long productId);

    /** Calcula, grava uma analise nova e devolve. */
    @POST("api/classifications")
    Call<ClassificationResponse> classificar(@Body ClassificationRequest corpo);
}
