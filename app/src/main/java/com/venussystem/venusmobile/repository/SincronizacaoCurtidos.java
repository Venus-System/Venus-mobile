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

    public void sincronizarEmSegundoPlano() {
        SincronizacaoRepository.EXECUTOR.execute(this::sincronizarAgora);
    }

    public void atualizarEmSegundoPlano(@Nullable Runnable aoMudar) {
        SincronizacaoRepository.EXECUTOR.execute(() -> {
            if (atualizarAgora() && aoMudar != null) {
                principal.execute(aoMudar);
            }
        });
    }

    @WorkerThread
    @VisibleForTesting
    public boolean sincronizarAgora() {
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

    @WorkerThread
    @VisibleForTesting
    public boolean atualizarAgora() {
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return false;
        }
        try {
            enviarPendentes(userId);
            List<Long> daApi = todosOsCurtidos(userId);
            return daApi != null && curtidos.trocarPelosDaApi(daApi);
        } catch (IOException e) {
            return false;
        } catch (Proibido e) {
            usuarios.esquecerId();
            return false;
        }
    }

    private boolean enviarPendentes(long userId) throws IOException, Proibido {
        for (Long produtoId : curtidos.paraCurtirNaApi()) {
            int codigo = api.curtir(new FavoriteRequest(userId, produtoId)).execute().code();
            exigirPermissao(codigo);
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
            if (sucesso(codigo) || codigo == NAO_ENCONTRADO) {
                curtidos.marcarDescurtidaEnviada(produtoId);
            } else {
                return false;
            }
        }
        return true;
    }

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
