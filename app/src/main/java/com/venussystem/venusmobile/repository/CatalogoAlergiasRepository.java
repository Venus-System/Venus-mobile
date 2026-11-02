package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.venussystem.venusmobile.R;
import com.venussystem.venusmobile.repository.api.ClienteApi;
import com.venussystem.venusmobile.repository.api.VenusApi;
import com.venussystem.venusmobile.repository.api.dto.AllergyResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class CatalogoAlergiasRepository {

    public interface AoAtualizar {
        void aoAtualizar(List<String> nomes);
    }

    private static final String ARQUIVO = "venus_catalogo_alergias";
    private static final String CHAVE_NOMES = "nomes";

    private static final String SEPARADOR = String.valueOf((char) 31);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Context context;
    private final SharedPreferences prefs;
    private final VenusApi api;
    private final Executor principal;

    public CatalogoAlergiasRepository(Context context) {
        this(context, ClienteApi.get(), ContextCompat.getMainExecutor(context));
    }

    @VisibleForTesting
    public CatalogoAlergiasRepository(Context context, VenusApi api, Executor principal) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
        this.api = api;
        this.principal = principal;
    }

    public List<String> nomes() {
        String salvo = prefs.getString(CHAVE_NOMES, "");
        if (salvo != null && !salvo.isEmpty()) {
            return new ArrayList<>(Arrays.asList(salvo.split(SEPARADOR)));
        }
        return new ArrayList<>(Arrays.asList(
                context.getResources().getStringArray(R.array.alergias)));
    }

    public void atualizarEmSegundoPlano(AoAtualizar aoAtualizar) {
        EXECUTOR.execute(() -> {
            List<String> nomes = buscarAgora();
            if (nomes != null) {
                principal.execute(() -> aoAtualizar.aoAtualizar(nomes));
            }
        });
    }

    @WorkerThread
    @VisibleForTesting
    @Nullable
    public List<String> buscarAgora() {
        Response<List<AllergyResponse>> resposta;
        try {
            resposta = api.listarAlergias().execute();
        } catch (IOException e) {
            return null;
        }
        if (!resposta.isSuccessful() || resposta.body() == null) {
            return null;
        }

        List<String> nomes = new ArrayList<>();
        for (AllergyResponse alergia : resposta.body()) {
            String nome = alergia.allergyName == null ? "" : alergia.allergyName.trim();
            if (!nome.isEmpty() && !nomes.contains(nome)) {
                nomes.add(nome);
            }
        }
        if (nomes.isEmpty()) {
            return null;
        }

        prefs.edit().putString(CHAVE_NOMES, String.join(SEPARADOR, nomes)).apply();
        return nomes;
    }
}
