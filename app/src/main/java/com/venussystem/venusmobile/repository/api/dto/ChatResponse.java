package com.venussystem.venusmobile.repository.api.dto;

import com.google.gson.annotations.SerializedName;

public class ChatResponse {
    public String resposta;

    @SerializedName("conversation_id")
    public String conversationId;

    // "neutra" ou "magoada": a cara da Veninha junto da resposta.
    public String expressao;
}
