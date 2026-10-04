package com.venussystem.venusmobile.repository.api.dto;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * Corpo do PATCH /api/user-lists/{id}, so com os campos que mudaram: a API
 * mantem o campo que nao veio e apaga o que vier null.
 *
 * O JSON e montado aqui porque o conversor do Retrofit nao escreve campo
 * null, e sem o null nao da para apagar a descricao.
 */
public class UserListPatchRequest {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON_COM_NULL = new GsonBuilder().serializeNulls().create();

    private final Map<String, String> campos = new LinkedHashMap<>();

    public void name(String nome) {
        campos.put("name", nome);
    }

    /** null apaga a descricao na API. */
    public void description(@Nullable String descricao) {
        campos.put("description", descricao);
    }

    /** Ver UserListRequest para os valores. null apaga a capa padrao. */
    public void coverKey(@Nullable String capaPadrao) {
        campos.put("coverKey", capaPadrao);
    }

    public RequestBody paraEnviar() {
        return RequestBody.create(GSON_COM_NULL.toJson(campos), JSON);
    }
}
