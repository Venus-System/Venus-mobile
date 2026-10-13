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

/**
 * Poe o token de ID do Firebase em toda chamada ao Venus-CRUD.
 *
 * A API exige "Authorization: Bearer <token>" nos dados de usuario (perfil,
 * preferencias, alergias, etiquetas, listas, favoritos, analises) e responde
 * 401 sem ele. O catalogo (produtos, marcas, alergias...) e aberto, mas o
 * token vai junto do mesmo jeito: a API ignora, e assim nenhuma rota nova
 * fica sem token por esquecimento. O site do Venus faz igual.
 *
 * Roda na thread da chamada (nunca a principal): o Retrofit so chama a rede
 * fora dela, e e ali que da para esperar o Firebase.
 */
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

    /**
     * Chamado pelo OkHttp quando a API responde 401. O Firebase so troca o
     * token perto de vencer, entao um token recusado pode ser um que venceu
     * no caminho: pede um novo e repete uma vez so.
     */
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

    /**
     * Sem token (ninguem logado, ou o Firebase sem rede para renovar) o pedido
     * segue sem ele: o catalogo continua funcionando, e a rota de usuario
     * responde 401, que quem chamou ja trata como falha.
     */
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
