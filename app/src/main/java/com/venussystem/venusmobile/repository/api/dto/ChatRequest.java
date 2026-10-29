package com.venussystem.venusmobile.repository.api.dto;

import com.google.gson.annotations.SerializedName;

public class ChatRequest {
    public String mensagem;

    @SerializedName("conversation_id")
    public String conversationId;
}
