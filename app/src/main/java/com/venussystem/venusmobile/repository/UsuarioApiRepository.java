package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.UserRequest;
import com.venussystem.venusmobile.repository.api.dto.UserResponse;

import java.io.IOException;

import retrofit2.Response;

public class UsuarioApiRepository {

    private static final String ARQUIVO = "venus_usuario_api";
    private static final String PREFIXO_ID = "id_";

    private static final String STATUS_ATIVO = "ACTIVE";
    private static final String NOME_PADRAO = "Usuário Venus";

    private static final Object TRAVA = new Object();

    private final SharedPreferences prefs;
    private final VenusApi api;
    private final SessaoUsuario sessao;

    public UsuarioApiRepository(Context context) {
        this(context, ClienteApi.get(), new SessaoFirebase());
    }

    @VisibleForTesting
    public UsuarioApiRepository(Context context, VenusApi api, SessaoUsuario sessao) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
        this.api = api;
        this.sessao = sessao;
    }

    @WorkerThread
    @Nullable
    public Long obterIdSincrono() {
        String uid = sessao.uid();
        if (uid == null) {
            return null;
        }

        synchronized (TRAVA) {
            Long salvo = idSalvo(uid);
            if (salvo != null) {
                return salvo;
            }

            try {
                Long id = buscarPorUid(uid);
                if (id == null) {
                    id = criar(uid);
                }
                if (id != null) {
                    prefs.edit().putLong(PREFIXO_ID + uid, id).apply();
                }
                return id;
            } catch (IOException | FalhaApi e) {
                return null;
            }
        }
    }

    @Nullable
    public Long idEmCache() {
        String uid = sessao.uid();
        return uid == null ? null : idSalvo(uid);
    }

    @Nullable
    public String uidAtual() {
        return sessao.uid();
    }

    public void esquecerId() {
        String uid = sessao.uid();
        if (uid != null) {
            prefs.edit().remove(PREFIXO_ID + uid).apply();
        }
    }

    @Nullable
    private Long idSalvo(String uid) {
        String chave = PREFIXO_ID + uid;
        return prefs.contains(chave) ? prefs.getLong(chave, 0L) : null;
    }

    @Nullable
    private Long buscarPorUid(String uid) throws IOException, FalhaApi {
        Response<FatiaResponse<UserResponse>> resposta = api.buscarUsuarioPorUid(uid).execute();
        if (!resposta.isSuccessful()) {
            throw new FalhaApi();
        }

        FatiaResponse<UserResponse> fatia = resposta.body();
        if (fatia == null || fatia.content == null) {
            return null;
        }
        for (UserResponse usuario : fatia.content) {
            if (uid.equals(usuario.firebaseUid) && usuario.id != null) {
                return usuario.id;
            }
        }
        return null;
    }

    @Nullable
    private Long criar(String uid) throws IOException, FalhaApi {
        UserRequest corpo = new UserRequest();
        corpo.firebaseUid = uid;
        corpo.name = nomeParaCadastro();
        corpo.email = sessao.email();
        corpo.status = STATUS_ATIVO;

        Response<UserResponse> resposta = api.criarUsuario(corpo).execute();
        if (resposta.isSuccessful() && resposta.body() != null) {
            return resposta.body().id;
        }
        if (resposta.code() == 409) {
            return buscarPorUid(uid);
        }
        throw new FalhaApi();
    }

    private String nomeParaCadastro() {
        String nome = sessao.nome();
        if (nome != null && !nome.trim().isEmpty()) {
            return nome.trim();
        }
        String email = sessao.email();
        int arroba = email == null ? -1 : email.indexOf('@');
        if (arroba > 0) {
            return email.substring(0, arroba);
        }
        return NOME_PADRAO;
    }

    private static class FalhaApi extends Exception {
    }
}
