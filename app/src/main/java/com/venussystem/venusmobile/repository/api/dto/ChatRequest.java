package com.venussystem.venusmobile.repository.api.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Corpo do POST /v1/chat da Venus-AI-api. Diferente do Venus-CRUD, essa API e
 * em Python e usa snake_case, dai os @SerializedName.
 */
public class ChatRequest {
    public String mensagem;

    @SerializedName("conversation_id")
    public String conversationId;

    // Id numerico do usuario no Venus-CRUD. Sem ele a Venus ainda conversa,
    // mas nao consegue consultar perfil, alergias nem favoritos da pessoa.
    @SerializedName("usuario_id_postgres")
    public Long usuarioIdPostgres;
}
