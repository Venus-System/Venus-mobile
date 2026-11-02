package com.venussystem.venusmobile.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.MapeadorPerfilApi;
import com.venussystem.venusmobile.repository.api.PerfilDaApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.UserFullProfileResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class RestauracaoDoPerfil {

    public interface AoBuscar {
        void aoTerminar(@Nullable PerfilDaApi daApi);
    }

    private static final int PROIBIDO = 403;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final UsuarioApiRepository usuarios;
    private final VenusApi api;
    private final List<String> opcoesDePreferencia;
    private final Executor principal;

    public RestauracaoDoPerfil(Context context) {
        this(new UsuarioApiRepository(context), ClienteApi.get(),
                Arrays.asList(context.getResources().getStringArray(R.array.preferencias)),
                ContextCompat.getMainExecutor(context));
    }

    @VisibleForTesting
    public RestauracaoDoPerfil(UsuarioApiRepository usuarios, VenusApi api,
                               List<String> opcoesDePreferencia, Executor principal) {
        this.usuarios = usuarios;
        this.api = api;
        this.opcoesDePreferencia = opcoesDePreferencia;
        this.principal = principal;
    }

    public void buscarEmSegundoPlano(AoBuscar aoBuscar) {
        EXECUTOR.execute(() -> {
            PerfilDaApi daApi = buscarAgora();
            principal.execute(() -> aoBuscar.aoTerminar(daApi));
        });
    }

    @WorkerThread
    @Nullable
    @VisibleForTesting
    public PerfilDaApi buscarAgora() {
        Long userId = usuarios.obterIdSincrono();
        if (userId == null) {
            return null;
        }

        try {
            Response<UserFullProfileResponse> resposta = api.perfilCompleto(userId).execute();
            if (resposta.code() == PROIBIDO) {
                usuarios.esquecerId();
                return null;
            }
            if (!resposta.isSuccessful()) {
                return null;
            }
            return MapeadorPerfilApi.daApi(resposta.body(), opcoesDePreferencia);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
