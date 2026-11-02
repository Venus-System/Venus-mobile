package com.venussystem.venusmobile.repository.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.venussystem.venusmobile.repository.SessaoUsuario;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class TokenFirebase implements Interceptor, Authenticator {

    private static final String CABECALHO = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final SessaoUsuario sessao;

    public TokenFirebase(SessaoUsuario sessao) {
        this.sessao = sessao;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request pedido = chain.request();
        if (pedido.header(CABECALHO) != null) {
            return chain.proceed(pedido);
        }

        String token = tokenOuNull(false);
        if (token == null) {
            return chain.proceed(pedido);
        }
        return chain.proceed(comToken(pedido, token));
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route rota, @NonNull Response resposta) {
        boolean jaRepetiu = resposta.priorResponse() != null;
        boolean foiComToken = resposta.request().header(CABECALHO) != null;
        if (jaRepetiu || !foiComToken) {
            return null;
        }

        String novo = tokenOuNull(true);
        return novo == null ? null : comToken(resposta.request(), novo);
    }

    @Nullable
    private String tokenOuNull(boolean forcarRenovacao) {
        if (sessao.uid() == null) {
            return null;
        }
        try {
            return sessao.token(forcarRenovacao);
        } catch (IOException e) {
            return null;
        }
    }

    private static Request comToken(Request pedido, String token) {
        return pedido.newBuilder()
                .header(CABECALHO, PREFIXO + token)
                .build();
    }
}
