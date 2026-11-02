package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.ClassificationRequest;
import com.venussystem.venusmobile.repository.api.dto.ClassificationResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface VenusClassificacaoApi {
    @GET("api/classifications/user/{userId}/product/{productId}")
    Call<ClassificationResponse> ultimaClassificacao(@Path("userId") long userId,
                                                     @Path("productId") long productId);

    @POST("api/classifications")
    Call<ClassificationResponse> classificar(@Body ClassificationRequest corpo);
}
