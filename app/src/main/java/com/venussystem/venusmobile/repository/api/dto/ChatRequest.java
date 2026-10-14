package com.venussystem.venusmobile.repository.api.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Corpo do POST /v1/chat da Venus-AI-api. Diferente do Venus-CRUD, essa API e
 * em Python e usa snake_case, dai os @SerializedName.
 */
public class ChatRequest {
    public String mensagem;

    // Fica de fora na primeira mensagem (o Gson nao escreve null): a API usa o
    // UID como conversa e devolve o id, que o app reenvia nas seguintes.
    @SerializedName("conversation_id")
    public String conversationId;

    // Nao existe mais "usuario_id_postgres": a API acha a pessoa no banco pelo
    // UID do token e ignora qualquer id mandado no corpo.
}
