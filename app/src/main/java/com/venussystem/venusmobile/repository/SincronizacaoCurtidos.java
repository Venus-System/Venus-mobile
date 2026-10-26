package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.FatiaResponse;
import com.venussystem.venusmobile.repository.api.dto.FavoriteRequest;
import com.venussystem.venusmobile.repository.api.dto.FavoriteResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import retrofit2.Response;

/**
 * Mantem os produtos curtidos do aparelho e os de /api/favorites iguais.
 *
 * Mandar: cada produto curtido vira um POST e cada descurtido um DELETE. Roda
 * quieto quando a tela do produto (ou a dos curtidos) fecha; o que falhar
 * fica na fila e vai na proxima.
 *
 * Trazer: ao abrir o app e a tela dos curtidos, depois de mandar o pendente,
 * le os curtidos da pessoa no servidor. O servidor vence, menos no que ainda
 * esta na fila (ver CurtidosRepository.trocarPelosDaApi).
 */
public class SincronizacaoCurtidos {

    private static final int TAMANHO_PAGINA = 100;
    private static final int LIMITE_PAGINAS = 20;

    private static final int INVALIDO = 400;
    private static final int PROIBIDO = 403;
    private static final int NAO_ENCONTRADO = 404;
    private static final int CONFLITO = 409;

    private final CurtidosRepository curtidos;
    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final Executor principal;

    public SincronizacaoCurtidos(Context context) {
        this(new CurtidosRepository(context), new UsuarioApiRepository(context),
                ClienteApi.get(), ContextCompat.getMainExecutor(context));
    }

    @VisibleForTesting
    public SincronizacaoCurtidos(CurtidosRepository curtidos, UsuarioApiRepository usuarios,
                                 VenusApi api, Executor principal) {
        this.curtidos = curtidos;
        this.usuarios = usuarios;
        this.api = api;
        this.principal = principal;
    }

    /** Mesma fila do perfil e das listas: nada de dois envios em paralelo. */
    public void sincronizarEmSegundoPlano() {
        SincronizacaoRepository.EXECUTOR.execute(this::sincronizarAgora);
    }

    /**
     * Manda o pendente e traz o que mudou no servidor.
     *
     * @param aoMudar chamado na thread principal se os curtidos mudaram no
     *                aparelho, para a tela recarregar.
     */
    public void atualizarEmSegundoPlano(@Nullable Runnable aoMudar) {
        SincronizacaoRepository.EXECUTOR.execute(() -> {
            if (atualizarAgora() && aoMudar != null) {
                principal.execute(aoMudar);
            }
        });
    }

    /**
     * @return true se tudo o que estava pendente foi para a API.
     */
    @WorkerThread
    @VisibleForTesting
    public boolean sincronizarAgora() {
        // Sem nada para mandar, nem pergunta o id da pessoa.
        if (!curtidos.temAlgoParaEnviar()) {
            return false;
        }
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return false;
        }
        try {
            return enviarPendentes(userId);
        } catch (IOException e) {
            return false;
        } catch (Proibido e) {
            usuarios.esquecerId();
            return false;
        }
    }

    /**
     * @return true se os curtidos mudaram no aparelho.
     */
    @WorkerThread
    @VisibleForTesting
    public boolean atualizarAgora() {
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return false;
        }
        try {
            // Primeiro o que mudou aqui: assim o que vem do servidor ja inclui.
            enviarPendentes(userId);
            List<Long> daApi = todosOsCurtidos(userId);
            return daApi != null && curtidos.trocarPelosDaApi(daApi);
        } catch (IOException e) {
            return false;
        } catch (Proibido e) {
            // O id guardado nao e mais desta pessoa (banco recriado): a proxima
            // tentativa busca de novo.
            usuarios.esquecerId();
            return false;
        }
    }

    /**
     * @return true se a fila esvaziou. Um erro do servidor (5xx) para no meio:
     * o resto fica para a proxima.
     */
    private boolean enviarPendentes(long userId) throws IOException, Proibido {
        for (Long produtoId : curtidos.paraCurtirNaApi()) {
            int codigo = api.curtir(new FavoriteRequest(userId, produtoId)).execute().code();
            exigirPermissao(codigo);
            // 409: ja estava curtido. 404/400: o produto nao existe mais - nao
            // adianta repetir, e a proxima leitura tira ele da tela.
            if (sucesso(codigo) || codigo == CONFLITO
                    || codigo == NAO_ENCONTRADO || codigo == INVALIDO) {
                curtidos.marcarCurtidaEnviada(produtoId);
            } else {
                return false;
            }
        }
        for (Long produtoId : curtidos.paraDescurtirNaApi()) {
            int codigo = api.descurtir(userId, produtoId).execute().code();
            exigirPermissao(codigo);
            // 404: ja nao estava curtido.
            if (sucesso(codigo) || codigo == NAO_ENCONTRADO) {
                curtidos.marcarDescurtidaEnviada(produtoId);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Todos os curtidos da pessoa na API, do mais recente para o mais antigo.
     *
     * @return null se alguma pagina falhou ou se passou do limite de paginas.
     */
    @Nullable
    private List<Long> todosOsCurtidos(long userId) throws IOException, Proibido {
        List<FavoriteResponse> todos = new ArrayList<>();
        for (int pagina = 0; pagina < LIMITE_PAGINAS; pagina++) {
            Response<FatiaResponse<FavoriteResponse>> resposta =
                    api.curtidosDoUsuario(userId, pagina, TAMANHO_PAGINA).execute();
            exigirPermissao(resposta.code());
            FatiaResponse<FavoriteResponse> fatia = resposta.body();
            if (!resposta.isSuccessful() || fatia == null || fatia.content == null) {
                return null;
            }
            todos.addAll(fatia.content);
            if (!Boolean.FALSE.equals(fatia.last) || fatia.content.isEmpty()) {
                return ordenados(todos);
            }
        }
        return null;
    }

    /** A API nao promete ordem; o id do registro cresce com o tempo. */
    private static List<Long> ordenados(List<FavoriteResponse> daApi) {
        List<FavoriteResponse> validos = new ArrayList<>();
        for (FavoriteResponse curtido : daApi) {
            if (curtido.productId != null) {
                validos.add(curtido);
            }
        }
        validos.sort((a, b) -> Long.compare(
                b.id == null ? 0L : b.id, a.id == null ? 0L : a.id));
        List<Long> produtos = new ArrayList<>();
        for (FavoriteResponse curtido : validos) {
            if (!produtos.contains(curtido.productId)) {
                produtos.add(curtido.productId);
            }
        }
        return produtos;
    }

    private static void exigirPermissao(int codigo) throws Proibido {
        if (codigo == PROIBIDO) {
            throw new Proibido();
        }
    }

    private static boolean sucesso(int codigo) {
        return codigo >= 200 && codigo < 300;
    }

    private static class Proibido extends Exception {
    }
}
