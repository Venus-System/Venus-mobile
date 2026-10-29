package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.ChatRequest;
import com.venussystem.venusmobile.repository.api.dto.ChatResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface VenusIaApi {
    @POST("v1/chat")
    Call<ChatResponse> conversar(@Header("Authorization") String autorizacao,
                                 @Body ChatRequest corpo);

    @GET("v1/health")
    Call<Void> acordar();
}
