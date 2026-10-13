package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.ChatRequest;
import com.venussystem.venusmobile.repository.api.dto.ChatResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Venus-AI-api, a API da assistente. E outro servidor, separado do Venus-CRUD.
 */
public interface VenusIaApi {

    /**
     * Sem barra no fim de proposito: "v1/chat/" faz o FastAPI responder um
     * redirecionamento 307, e isso num POST so atrasa (ou perde) a mensagem.
     *
     * @param autorizacao "Bearer " + token de ID do Firebase.
     */
    @POST("v1/chat")
    Call<ChatResponse> conversar(@Header("Authorization") String autorizacao,
                                 @Body ChatRequest corpo);
}
