package com.venussystem.venusmobile.repository.api;

import com.venussystem.venusmobile.repository.api.dto.ChatRequest;
import com.venussystem.venusmobile.repository.api.dto.ChatResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
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

    /**
     * Rota publica e leve. Nasceu para acordar o servidor no Render (dormia
     * depois de 15 minutos parado); na AWS ele nao dorme, e a chamada so adianta
     * a conexao enquanto a pessoa ainda esta digitando.
     */
    @GET("v1/health")
    Call<Void> acordar();
}
