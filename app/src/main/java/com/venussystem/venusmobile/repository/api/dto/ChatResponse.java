package com.venussystem.venusmobile.repository.api.dto;

import com.google.gson.annotations.SerializedName;

public class ChatResponse {
    public String resposta;

    @SerializedName("conversation_id")
    public String conversationId;
}
